package com.obms.service;

import com.obms.common.ApiException;
import com.obms.common.BankTime;
import com.obms.common.References;
import com.obms.domain.Account;
import com.obms.domain.BillPayment;
import com.obms.domain.Biller;
import com.obms.domain.Enums;
import com.obms.domain.IdempotencyRecord;
import com.obms.domain.Transaction;
import com.obms.dto.BillPaymentDtos;
import com.obms.repository.BillPaymentRepository;
import com.obms.repository.BillerRepository;
import com.obms.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bill payments against the simulated biller catalogue (FR-6).
 *
 * <p>A payment is one debit, not a transfer: the money leaves the bank's
 * perimeter toward a biller that does not hold an OBMS account. It still goes
 * through {@link LedgerService}, so the same balance floor and locking apply.</p>
 */
@Service
@RequiredArgsConstructor
public class BillPaymentService {

    private final BillerRepository billerRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BillPaymentDtos.BillerView> billers() {
        return billerRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(BillPaymentDtos.BillerView::from)
                .toList();
    }

    /**
     * Pays a bill.
     *
     * @param idempotencyKey client key preventing a retry from paying twice
     */
    @Transactional
    public BillPaymentDtos.PaymentReceipt pay(Long customerId, String username,
                                               BillPaymentDtos.PayBillRequest request,
                                               String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyRepository
                    .findByIdempotencyKeyAndUsername(idempotencyKey, username);
            if (existing.isPresent()) {
                BillPayment replay = billPaymentRepository
                        .findByReference(existing.get().getResultRef())
                        .orElseThrow(ApiException::duplicateRequest);
                return BillPaymentDtos.PaymentReceipt.from(replay,
                        replay.getAccount().getBalance());
            }
        }

        Biller biller = billerRepository.findById(request.billerId())
                .orElseThrow(() -> ApiException.notFound("Biller"));
        if (!biller.isActive()) {
            throw ApiException.invalidState("This biller is not currently accepting payments.");
        }

        Account account = accountService.requireOwned(request.accountId(), customerId);

        validateAmountAgainstBiller(request.amount(), biller);

        if (request.amount().compareTo(account.availableBalance()) > 0) {
            throw ApiException.insufficientFunds();
        }

        BillPayment payment = new BillPayment();
        payment.setReference(References.billPaymentReference());
        payment.setCustomer(account.getCustomer());
        payment.setAccount(account);
        payment.setBiller(biller);
        payment.setConsumerIdentifier(request.consumerIdentifier().trim());
        payment.setAmount(request.amount());
        payment.setDueDate(request.dueDate());

        Transaction debit = ledgerService.postDebit(
                account.getId(), request.amount(), Enums.TransactionType.BILL_PAYMENT,
                biller.getName() + " - " + payment.getConsumerIdentifier(),
                biller.getName(), biller.getSpendCategory());

        payment.setTransaction(debit);
        payment.setStatus(Enums.TransactionStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        BillPayment saved = billPaymentRepository.save(payment);

        recordIdempotency(idempotencyKey, username, saved.getReference());

        auditService.record("billpay.executed", "BillPayment", saved.getReference(),
                "Paid " + request.amount() + " to " + biller.getName());
        notificationService.notify(account.getCustomer(), Enums.NotificationType.TRANSACTION_SUCCESS,
                "Bill paid",
                request.amount() + " paid to " + biller.getName()
                        + ". Reference " + saved.getReference() + ".",
                "/bills");

        return BillPaymentDtos.PaymentReceipt.from(saved, debit.getBalanceAfter());
    }

    @Transactional(readOnly = true)
    public Page<BillPaymentDtos.PaymentHistoryItem> history(Long customerId, Pageable pageable) {
        return billPaymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(BillPaymentDtos.PaymentHistoryItem::from);
    }

    @Transactional(readOnly = true)
    public List<BillPaymentDtos.PaymentHistoryItem> upcoming(Long customerId, int withinDays) {
        return billPaymentRepository
                .findUpcoming(customerId, BankTime.today().plusDays(withinDays)).stream()
                .map(BillPaymentDtos.PaymentHistoryItem::from)
                .toList();
    }

    private void validateAmountAgainstBiller(BigDecimal amount, Biller biller) {
        if (biller.getMinAmount() != null && amount.compareTo(biller.getMinAmount()) < 0) {
            throw ApiException.badRequest("AMOUNT_BELOW_MINIMUM",
                    biller.getName() + " requires at least " + biller.getMinAmount() + ".");
        }
        if (biller.getMaxAmount() != null && amount.compareTo(biller.getMaxAmount()) > 0) {
            throw ApiException.badRequest("AMOUNT_ABOVE_MAXIMUM",
                    biller.getName() + " accepts at most " + biller.getMaxAmount() + ".");
        }
    }

    private void recordIdempotency(String idempotencyKey, String username, String resultRef) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            idempotencyRepository.save(
                    new IdempotencyRecord(idempotencyKey, username, "billpay", resultRef));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.duplicateRequest();
        }
    }
}
