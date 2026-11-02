package com.obms.service;

import com.obms.common.ApiException;
import com.obms.common.References;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.repository.AccountRepository;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The only component in OBMS that changes an account balance.
 *
 * <p>Concentrating balance mutation here is what makes the integrity guarantee
 * checkable: there is exactly one code path to review, and every other service
 * - transfers, bill payments, seeding - goes through it.</p>
 *
 * <h2>How atomicity is achieved</h2>
 * <ol>
 *   <li>{@link #postDoubleEntry} runs in a single {@code REQUIRED} transaction.
 *       Both the debit and the credit, and both ledger rows, commit together or
 *       not at all. A half-applied transfer is therefore not representable.</li>
 *   <li>Accounts are re-read under {@code PESSIMISTIC_WRITE} row locks inside
 *       that transaction. The balance used for the sufficiency check is the
 *       locked one, so two concurrent debits cannot both pass the check.</li>
 *   <li>Locks are always acquired in ascending account-id order. Without a
 *       deterministic order, simultaneous A-to-B and B-to-A transfers would
 *       each hold one lock and wait for the other - a deadlock.</li>
 *   <li>A final guard rejects any computed balance below the account's minimum.
 *       Even if a caller skipped validation, the balance cannot go negative.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Moves money between two internal accounts, writing both ledger legs.
     *
     * @param sourceId      account to debit
     * @param destinationId account to credit
     * @param amount        strictly positive amount
     * @param type          business classification for both legs
     * @param description   narrative shown on statements
     * @param category      spend category applied to the debit leg
     * @return the two persisted ledger rows, debit first
     * @throws ApiException when funds are insufficient or an account is not operational
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DoubleEntry postDoubleEntry(Long sourceId, Long destinationId, BigDecimal amount,
                                       Enums.TransactionType type, String description,
                                       Enums.SpendCategory category) {
        requirePositive(amount);
        if (sourceId.equals(destinationId)) {
            throw ApiException.badRequest("SAME_ACCOUNT", "Source and destination must differ.");
        }

        // Deterministic lock ordering - see class documentation. Both accounts
        // are locked in ascending id order; the entities are then taken from
        // the persistence context, so no second query is issued.
        List<Long> lockOrder = new ArrayList<>(List.of(sourceId, destinationId));
        lockOrder.sort(Comparator.naturalOrder());

        Account first = accountRepository.findByIdForUpdate(lockOrder.get(0))
                .orElseThrow(() -> ApiException.notFound("Account"));
        Account second = accountRepository.findByIdForUpdate(lockOrder.get(1))
                .orElseThrow(() -> ApiException.notFound("Account"));

        Account source = first.getId().equals(sourceId) ? first : second;
        Account destination = first.getId().equals(destinationId) ? first : second;

        requireOperational(source, "Source account");
        requireOperational(destination, "Destination account");

        BigDecimal newSourceBalance = source.getBalance().subtract(amount);
        if (newSourceBalance.compareTo(source.getMinimumBalance()) < 0) {
            throw ApiException.insufficientFunds();
        }

        String groupRef = References.transferGroupReference();
        Instant bookedAt = Instant.now();

        source.setBalance(newSourceBalance);
        Transaction debit = writeLeg(source, destination, amount, Enums.TransactionDirection.DEBIT,
                type, description, category, groupRef, bookedAt, newSourceBalance);

        BigDecimal newDestinationBalance = destination.getBalance().add(amount);
        destination.setBalance(newDestinationBalance);
        Transaction credit = writeLeg(destination, source, amount, Enums.TransactionDirection.CREDIT,
                type, description, Enums.SpendCategory.TRANSFER, groupRef, bookedAt,
                newDestinationBalance);

        log.debug("Posted double entry {} for {} from {} to {}",
                groupRef, amount, sourceId, destinationId);

        return new DoubleEntry(debit, credit, groupRef);
    }

    /**
     * Writes a single-sided entry, for money leaving the bank's perimeter -
     * a bill payment to a simulated biller, for instance.
     *
     * @return the persisted debit row
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Transaction postDebit(Long accountId, BigDecimal amount, Enums.TransactionType type,
                                 String description, String counterpartyName,
                                 Enums.SpendCategory category) {
        requirePositive(amount);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> ApiException.notFound("Account"));
        requireOperational(account, "Account");

        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(account.getMinimumBalance()) < 0) {
            throw ApiException.insufficientFunds();
        }
        account.setBalance(newBalance);

        Transaction debit = new Transaction();
        debit.setReference(References.transactionReference());
        debit.setAccount(account);
        debit.setCounterpartyName(counterpartyName);
        debit.setDirection(Enums.TransactionDirection.DEBIT);
        debit.setTransactionType(type);
        debit.setStatus(Enums.TransactionStatus.COMPLETED);
        debit.setAmount(amount);
        debit.setBalanceAfter(newBalance);
        debit.setDescription(description);
        debit.setCategory(category);
        debit.setBookedAt(Instant.now());
        return transactionRepository.save(debit);
    }

    /**
     * Writes a single-sided credit, for money entering from outside - a cash
     * deposit recorded by branch staff, or demo seeding.
     *
     * @return the persisted credit row
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Transaction postCredit(Long accountId, BigDecimal amount, Enums.TransactionType type,
                                 String description, String counterpartyName,
                                 Enums.SpendCategory category) {
        requirePositive(amount);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> ApiException.notFound("Account"));
        requireOperational(account, "Account");

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);

        Transaction credit = new Transaction();
        credit.setReference(References.transactionReference());
        credit.setAccount(account);
        credit.setCounterpartyName(counterpartyName);
        credit.setDirection(Enums.TransactionDirection.CREDIT);
        credit.setTransactionType(type);
        credit.setStatus(Enums.TransactionStatus.COMPLETED);
        credit.setAmount(amount);
        credit.setBalanceAfter(newBalance);
        credit.setDescription(description);
        credit.setCategory(category);
        credit.setBookedAt(Instant.now());
        return transactionRepository.save(credit);
    }

    private Transaction writeLeg(Account account, Account counterparty, BigDecimal amount,
                                 Enums.TransactionDirection direction, Enums.TransactionType type,
                                 String description, Enums.SpendCategory category, String groupRef,
                                 Instant bookedAt, BigDecimal balanceAfter) {
        Transaction leg = new Transaction();
        leg.setReference(References.transactionReference());
        leg.setTransferGroupRef(groupRef);
        leg.setAccount(account);
        leg.setCounterpartyAccount(counterparty);
        leg.setCounterpartyName(counterparty.getCustomer().fullName());
        leg.setDirection(direction);
        leg.setTransactionType(type);
        leg.setStatus(Enums.TransactionStatus.COMPLETED);
        leg.setAmount(amount);
        leg.setBalanceAfter(balanceAfter);
        leg.setDescription(description);
        leg.setCategory(category);
        leg.setBookedAt(bookedAt);
        return transactionRepository.save(leg);
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT",
                    "Amount cannot have more than two decimal places.");
        }
    }

    private void requireOperational(Account account, String label) {
        if (!account.isOperational()) {
            throw ApiException.invalidState(label + " is " + account.getStatus() + ".");
        }
    }

    /**
     * The pair of rows produced by one internal transfer.
     *
     * @param debit    row on the source account
     * @param credit   row on the destination account
     * @param groupRef reference shared by both legs
     */
    public record DoubleEntry(Transaction debit, Transaction credit, String groupRef) {
    }
}
