package com.obms.service;

import com.obms.common.ApiException;
import com.obms.config.AppProperties;
import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.IdempotencyRecord;
import com.obms.domain.PendingTransfer;
import com.obms.domain.Transaction;
import com.obms.dto.TransferDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.BeneficiaryRepository;
import com.obms.repository.FraudAlertRepository;
import com.obms.repository.IdempotencyRecordRepository;
import com.obms.repository.PendingTransferRepository;
import com.obms.repository.TransactionRepository;
import com.obms.service.fraud.FraudService;
import com.obms.service.fraud.RiskAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates internal fund transfers (FR-4).
 *
 * <p>This is the integrated path the project is built around. One customer
 * action runs through validation, risk scoring, risk-based authentication,
 * atomic settlement, and notification - the modules are wired to each other
 * rather than sitting side by side:</p>
 *
 * <pre>
 *   initiate
 *     -> ownership and state checks
 *     -> balance and daily-limit checks
 *     -> fraud scoring (behavioural features from this customer's history)
 *     -> decision:
 *          BLOCK    : refuse, record an explainable alert, notify
 *          HOLD     : park for analyst review, no money moves
 *          STEP_UP  : issue an OTP bound to this exact transfer
 *          ALLOW    : settle immediately
 *   confirm
 *     -> verify the OTP bound to the pending reference
 *     -> settle atomically through the ledger
 *     -> notify, and let analytics pick up the new ledger rows
 * </pre>
 *
 * <p>Money movement itself is delegated to {@link LedgerService}, which owns
 * the locking and atomicity guarantees.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int PENDING_TTL_MINUTES = 10;

    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionRepository transactionRepository;
    private final PendingTransferRepository pendingTransferRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final LedgerService ledgerService;
    private final FraudService fraudService;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties properties;

    /**
     * Starts a transfer.
     *
     * @param customerId     authenticated caller, taken from the token not the body
     * @param username       caller's login name, for OTP binding and audit
     * @param request        validated transfer details
     * @param idempotencyKey client-supplied key, may be null
     * @return either a completed transfer or a challenge the client must answer
     */
    @Transactional
    public TransferDtos.TransferResult initiate(Long customerId, String username,
                                                TransferDtos.TransferRequest request,
                                                String idempotencyKey) {
        Optional<TransferDtos.TransferResult> replay = replayIfSeen(idempotencyKey, username);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account source = loadOwnedAccount(request.sourceAccountId(), customerId);
        Resolution destination = resolveDestination(request, customerId, source);

        validateAmount(request.amount(), source);
        validateDailyLimit(source, request.amount());

        RiskAssessment assessment = fraudService.assess(
                source, request.amount(), destination.beneficiary(), Enums.SpendCategory.TRANSFER);

        boolean forceVerification = request.amount()
                .compareTo(properties.getTransfer().getAlwaysVerifyAbove()) >= 0;

        RiskAssessment.Decision decision = escalate(assessment.decision(), forceVerification);

        return switch (decision) {
            case BLOCK -> handleBlocked(source, request, assessment);
            case HOLD -> handleHeld(source, destination, request, assessment, idempotencyKey);
            case STEP_UP -> handleStepUp(source, destination, request, assessment,
                    username, idempotencyKey);
            case ALLOW -> settle(source, destination, request.amount(), request.description(),
                    assessment, username, idempotencyKey);
        };
    }

    /**
     * Completes a transfer that was waiting on a second factor.
     *
     * <p>The caller supplies only the pending reference and the code. The
     * amount and destination come from the stored {@link PendingTransfer}, so
     * they cannot be changed between the challenge and the confirmation.</p>
     */
    @Transactional
    public TransferDtos.TransferResult confirm(Long customerId, String username,
                                               String pendingRef, String otpCode) {
        PendingTransfer pending = pendingTransferRepository
                .findByPendingRefAndCustomerId(pendingRef, customerId)
                .orElseThrow(() -> ApiException.notFound("Pending transfer"));

        if (pending.getStatus() == Enums.TransactionStatus.COMPLETED) {
            // Idempotent confirmation: return the original outcome rather than
            // settling a second time.
            return TransferDtos.TransferResult.completed(
                    pending.getResultingReference(), pending.getAmount(),
                    pending.getSourceAccount().getBalance(), pending.getRiskScore(),
                    pending.getRiskLevel());
        }
        if (!pending.isAwaiting()) {
            throw ApiException.invalidState("This transfer is no longer awaiting verification.");
        }

        otpService.verify(username, Enums.OtpPurpose.TRANSFER, pendingRef, otpCode);

        Account source = loadOwnedAccount(pending.getSourceAccount().getId(), customerId);
        validateDailyLimit(source, pending.getAmount());

        Resolution destination = new Resolution(
                pending.getDestinationAccount(), pending.getBeneficiary());

        TransferDtos.TransferResult result = settle(source, destination, pending.getAmount(),
                pending.getDescription(), rehydrate(pending), username, pending.getIdempotencyKey());

        pending.setStatus(Enums.TransactionStatus.COMPLETED);
        pending.setResultingReference(result.reference());
        return result;
    }

    // ------------------------------------------------------------------
    // analyst resolution of a held transfer
    // ------------------------------------------------------------------

    /**
     * Settles a transfer that fraud controls held for review.
     *
     * <p>Before this existed a held transfer could never complete: clearing the
     * alert as a false positive only updated the alert row, and
     * {@link #confirm} accepts nothing but {@code AWAITING_VERIFICATION}. The
     * customer's money was never taken, but their transfer never happened
     * either and nothing recorded an outcome.</p>
     *
     * <p>Releasing re-runs the balance and daily-limit checks rather than
     * trusting the figures captured when the transfer was held. Time has
     * passed; the account may since have been drawn down or frozen, and
     * settling on stale numbers is how an overdraft gets created.</p>
     *
     * @param pendingRef the held transfer's reference
     * @param analyst    username recorded against the decision
     * @return the settled transfer
     * @throws ApiException if it is not held, or no longer passes validation
     */
    @Transactional
    public TransferDtos.TransferResult releaseHeld(String pendingRef, String analyst, String notes) {
        PendingTransfer pending = pendingTransferRepository.findByPendingRef(pendingRef)
                .orElseThrow(() -> ApiException.notFound("Held transfer"));

        if (pending.getStatus() == Enums.TransactionStatus.COMPLETED) {
            // Idempotent: a second release returns the original outcome rather
            // than debiting the customer twice.
            return TransferDtos.TransferResult.completed(
                    pending.getResultingReference(), pending.getAmount(),
                    pending.getSourceAccount().getBalance(), pending.getRiskScore(),
                    pending.getRiskLevel());
        }
        if (pending.getStatus() != Enums.TransactionStatus.PENDING) {
            throw ApiException.invalidState(
                    "This transfer is " + pending.getStatus() + " and cannot be released.");
        }

        Account source = accountRepository.findById(pending.getSourceAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Source account"));
        if (!source.isOperational()) {
            throw ApiException.invalidState("The source account is now " + source.getStatus() + ".");
        }
        if (pending.getAmount().compareTo(source.availableBalance()) > 0) {
            throw ApiException.insufficientFunds();
        }
        validateDailyLimit(source, pending.getAmount());

        Resolution destination = new Resolution(
                pending.getDestinationAccount(), pending.getBeneficiary());

        RiskAssessment cleared = new RiskAssessment(
                pending.getRiskScore() == null ? 0 : pending.getRiskScore(),
                pending.getRiskLevel() == null ? Enums.RiskLevel.LOW : pending.getRiskLevel(),
                RiskAssessment.Decision.ALLOW,
                "Released after review by " + analyst + ".",
                List.of(),
                "analyst_released",
                false);

        TransferDtos.TransferResult result = settle(source, destination, pending.getAmount(),
                pending.getDescription(), cleared, analyst, null);

        pending.setStatus(Enums.TransactionStatus.COMPLETED);
        pending.setResultingReference(result.reference());

        resolveAlert(pendingRef, Enums.AlertStatus.FALSE_POSITIVE, analyst, notes);

        auditService.record("transfer.hold.released", "PendingTransfer", pendingRef,
                "Released by " + analyst + " and settled as " + result.reference());
        notificationService.notify(source.getCustomer(), Enums.NotificationType.TRANSACTION_SUCCESS,
                "Held transfer released",
                "Your transfer of " + pending.getAmount() + " was reviewed, cleared and completed. "
                        + "Reference " + result.reference() + ".",
                "/transactions/" + result.reference());

        return result;
    }

    /**
     * Refuses a held transfer after review. No money moves; the transfer is
     * recorded as failed so the outcome is not left open, and the customer is
     * told rather than left waiting.
     */
    @Transactional
    public void rejectHeld(String pendingRef, String analyst, String notes) {
        PendingTransfer pending = pendingTransferRepository.findByPendingRef(pendingRef)
                .orElseThrow(() -> ApiException.notFound("Held transfer"));

        if (pending.getStatus() == Enums.TransactionStatus.COMPLETED) {
            throw ApiException.invalidState("This transfer has already settled and cannot be rejected.");
        }
        pending.setStatus(Enums.TransactionStatus.FAILED);

        resolveAlert(pendingRef, Enums.AlertStatus.CONFIRMED_FRAUD, analyst, notes);

        auditService.record("transfer.hold.rejected", "PendingTransfer", pendingRef,
                "Rejected by " + analyst);
        notificationService.notify(pending.getCustomer(),
                Enums.NotificationType.SUSPICIOUS_ACTIVITY,
                "Transfer declined after review",
                "Your transfer of " + pending.getAmount()
                        + " was declined following a fraud review. No money left your account. "
                        + "Contact support if you believe this is wrong.",
                "/fraud-alerts");
    }

    private void resolveAlert(String evaluatedRef, Enums.AlertStatus status,
                              String analyst, String notes) {
        fraudAlertRepository.findByEvaluatedRef(evaluatedRef).ifPresent(alert -> {
            alert.setStatus(status);
            alert.setReviewedBy(analyst);
            alert.setReviewedAt(Instant.now());
            alert.setReviewNotes(notes);
            alert.setMarkedFalsePositive(status == Enums.AlertStatus.FALSE_POSITIVE);
        });
    }

    /**
     * Fails step-up transfers whose OTP window has closed.
     *
     * <p>{@code expireStale} existed but nothing ever called it, so abandoned
     * transfers sat at {@code AWAITING_VERIFICATION} indefinitely. The query
     * deliberately does not touch {@code PENDING} rows: those are held for
     * analyst review and must not time out on a ten-minute OTP window.</p>
     */
    @Scheduled(fixedDelay = 300_000L)
    @Transactional
    public void expireAbandonedStepUps() {
        int expired = pendingTransferRepository.expireStale(Instant.now());
        if (expired > 0) {
            log.info("Expired {} unverified transfer(s) past their OTP window", expired);
        }
    }

    // ------------------------------------------------------------------
    // decision handlers
    // ------------------------------------------------------------------

    private TransferDtos.TransferResult handleBlocked(Account source,
                                                      TransferDtos.TransferRequest request,
                                                      RiskAssessment assessment) {
        Customer customer = source.getCustomer();
        fraudService.recordBlockedAttempt(customer, null, request.amount(), assessment);
        auditService.recordFailure("transfer.blocked", "Account", source.getAccountNumber(),
                "Blocked at risk score " + assessment.score() + ": " + assessment.primaryReason());
        notificationService.notify(customer, Enums.NotificationType.SUSPICIOUS_ACTIVITY,
                "Transfer blocked for your protection",
                "A transfer of " + request.amount() + " was blocked. " + assessment.primaryReason()
                        + " If this was you, please contact support.",
                "/fraud-alerts");

        throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "TRANSFER_BLOCKED",
                "This transfer was blocked by fraud controls. " + assessment.primaryReason());
    }

    private TransferDtos.TransferResult handleHeld(Account source, Resolution destination,
                                                   TransferDtos.TransferRequest request,
                                                   RiskAssessment assessment, String idempotencyKey) {
        PendingTransfer pending = createPending(source, destination, request, assessment, idempotencyKey);
        pending.setStatus(Enums.TransactionStatus.PENDING);

        Customer customer = source.getCustomer();
        fraudService.recordBlockedAttempt(customer, pending.getPendingRef(),
                request.amount(), assessment);
        auditService.record("transfer.held", "PendingTransfer", pending.getPendingRef(),
                "Held for review at risk score " + assessment.score());
        notificationService.notify(customer, Enums.NotificationType.SUSPICIOUS_ACTIVITY,
                "Transfer held for review",
                "Your transfer of " + request.amount() + " is being reviewed. "
                        + assessment.primaryReason(),
                "/fraud-alerts");

        return TransferDtos.TransferResult.held(pending.getPendingRef(), request.amount(),
                assessment.score(), assessment.level(), assessment.primaryReason(),
                assessment.factors());
    }

    private TransferDtos.TransferResult handleStepUp(Account source, Resolution destination,
                                                     TransferDtos.TransferRequest request,
                                                     RiskAssessment assessment, String username,
                                                     String idempotencyKey) {
        PendingTransfer pending = createPending(source, destination, request, assessment, idempotencyKey);

        // The OTP is bound to this pending reference, so it authorises this
        // transfer and no other.
        String code = otpService.issue(username, Enums.OtpPurpose.TRANSFER, pending.getPendingRef());
        notificationService.sendEmailQuietly(source.getCustomer().getEmail(),
                "Verification code for your transfer",
                "Your one-time code is " + code + ". It expires in 5 minutes. "
                        + "If you did not request this transfer, do not share this code.");

        auditService.record("transfer.step_up", "PendingTransfer", pending.getPendingRef(),
                "Verification required at risk score " + assessment.score());

        return TransferDtos.TransferResult.verificationRequired(pending.getPendingRef(),
                request.amount(), assessment.score(), assessment.level(),
                assessment.primaryReason(), assessment.factors());
    }

    private TransferDtos.TransferResult settle(Account source, Resolution destination,
                                               BigDecimal amount, String description,
                                               RiskAssessment assessment, String username,
                                               String idempotencyKey) {
        if (destination.account() == null) {
            // External destinations are out of Phase-1 scope: the report limits
            // transfers to accounts within the bank. Reaching here means a
            // beneficiary resolved to no internal account.
            throw ApiException.badRequest("EXTERNAL_TRANSFER_UNSUPPORTED",
                    "Transfers to accounts outside OBMS are not supported in this release.");
        }

        LedgerService.DoubleEntry entry = ledgerService.postDoubleEntry(
                source.getId(), destination.account().getId(), amount,
                Enums.TransactionType.INTERNAL_TRANSFER, description, Enums.SpendCategory.TRANSFER);

        Transaction debit = entry.debit();
        debit.setRiskScore(assessment.score());
        debit.setRiskLevel(assessment.level());

        Customer customer = source.getCustomer();
        fraudService.recordAssessment(customer, debit, debit.getReference(), amount, assessment);
        recordIdempotency(idempotencyKey, username, "transfer", debit.getReference());

        auditService.record("transfer.executed", "Transaction", debit.getReference(),
                "Transferred " + amount + " from " + source.getAccountNumber()
                        + " to " + destination.account().getAccountNumber());

        notificationService.notify(customer, Enums.NotificationType.TRANSACTION_SUCCESS,
                "Transfer successful",
                amount + " " + source.getCurrency() + " sent to "
                        + destination.describe() + ". Reference " + debit.getReference() + ".",
                "/transactions/" + debit.getReference());

        return TransferDtos.TransferResult.completed(debit.getReference(), amount,
                debit.getBalanceAfter(), assessment.score(), assessment.level());
    }

    // ------------------------------------------------------------------
    // validation helpers
    // ------------------------------------------------------------------

    private Account loadOwnedAccount(Long accountId, Long customerId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account"));
        // Ownership is checked against the token's customer id, never against
        // anything the client supplied.
        if (!account.getCustomer().getId().equals(customerId)) {
            throw ApiException.notOwned("account");
        }
        if (!account.isOperational()) {
            throw ApiException.invalidState("This account is " + account.getStatus() + ".");
        }
        return account;
    }

    /**
     * Works out where the money is going, and refuses anything the customer has
     * not been authorised to pay.
     */
    private Resolution resolveDestination(TransferDtos.TransferRequest request, Long customerId,
                                          Account source) {
        if (request.beneficiaryId() != null) {
            Beneficiary beneficiary = beneficiaryRepository
                    .findByIdAndOwnerCustomerId(request.beneficiaryId(), customerId)
                    .orElseThrow(() -> ApiException.notFound("Beneficiary"));
            if (beneficiary.getStatus() != Enums.BeneficiaryStatus.ACTIVE) {
                throw ApiException.invalidState(
                        "This beneficiary is not verified yet. Verify it before transferring.");
            }
            Account target = accountRepository.findByAccountNumber(beneficiary.getAccountNumber())
                    .orElseThrow(() -> ApiException.badRequest("EXTERNAL_TRANSFER_UNSUPPORTED",
                            "Transfers to accounts outside OBMS are not supported in this release."));
            requireDistinct(source, target);
            return new Resolution(target, beneficiary);
        }

        if (request.destinationAccountId() != null) {
            // Own-account transfer: the destination must also belong to the caller.
            Account target = accountRepository.findById(request.destinationAccountId())
                    .orElseThrow(() -> ApiException.notFound("Destination account"));
            if (!target.getCustomer().getId().equals(customerId)) {
                throw ApiException.badRequest("BENEFICIARY_REQUIRED",
                        "Transfers to another customer must go through a verified beneficiary.");
            }
            requireDistinct(source, target);
            return new Resolution(target, null);
        }

        throw ApiException.badRequest("DESTINATION_REQUIRED",
                "Provide either a beneficiary or one of your own accounts as the destination.");
    }

    private void requireDistinct(Account source, Account target) {
        if (source.getId().equals(target.getId())) {
            throw ApiException.badRequest("SAME_ACCOUNT",
                    "Source and destination accounts must be different.");
        }
    }

    private void validateAmount(BigDecimal amount, Account source) {
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be greater than zero.");
        }
        if (amount.compareTo(source.availableBalance()) > 0) {
            throw ApiException.insufficientFunds();
        }
    }

    /**
     * Enforces the per-day outbound cap (FR-4).
     *
     * <p>The window is the current calendar day in the bank's timezone, which
     * is what a customer means by "today", rather than a rolling 24 hours.</p>
     */
    private void validateDailyLimit(Account source, BigDecimal amount) {
        Instant startOfDay = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        BigDecimal alreadyToday = transactionRepository.sumDebitsSince(source.getId(), startOfDay);
        BigDecimal projected = alreadyToday.add(amount);

        if (projected.compareTo(source.getDailyTransferLimit()) > 0) {
            BigDecimal remaining = source.getDailyTransferLimit().subtract(alreadyToday);
            throw ApiException.limitExceeded(
                    "This transfer would exceed your daily limit of "
                            + source.getDailyTransferLimit() + ". You have "
                            + remaining.max(BigDecimal.ZERO) + " left today.");
        }
    }

    // ------------------------------------------------------------------
    // idempotency
    // ------------------------------------------------------------------

    private Optional<TransferDtos.TransferResult> replayIfSeen(String idempotencyKey, String username) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return idempotencyRepository.findByIdempotencyKeyAndUsername(idempotencyKey, username)
                .map(record -> {
                    log.info("Replaying idempotent transfer for key ending {}",
                            tail(idempotencyKey));
                    Transaction original = transactionRepository.findByReference(record.getResultRef())
                            .orElseThrow(ApiException::duplicateRequest);
                    return TransferDtos.TransferResult.completed(original.getReference(),
                            original.getAmount(), original.getBalanceAfter(),
                            original.getRiskScore(), original.getRiskLevel());
                });
    }

    /**
     * Inserts the idempotency key inside the same transaction as the ledger
     * write. The unique constraint is what actually prevents a double debit
     * under a concurrent retry; the earlier lookup is only a fast path.
     */
    private void recordIdempotency(String idempotencyKey, String username,
                                   String operation, String resultRef) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            idempotencyRepository.save(
                    new IdempotencyRecord(idempotencyKey, username, operation, resultRef));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.duplicateRequest();
        }
    }

    private static String tail(String value) {
        return value.length() <= 6 ? "******" : value.substring(value.length() - 6);
    }

    // ------------------------------------------------------------------
    // pending transfers
    // ------------------------------------------------------------------

    private PendingTransfer createPending(Account source, Resolution destination,
                                          TransferDtos.TransferRequest request,
                                          RiskAssessment assessment, String idempotencyKey) {
        PendingTransfer pending = new PendingTransfer();
        pending.setPendingRef("PND" + com.obms.common.References.transactionReference().substring(3));
        pending.setCustomer(source.getCustomer());
        pending.setSourceAccount(source);
        pending.setDestinationAccount(destination.account());
        pending.setBeneficiary(destination.beneficiary());
        pending.setAmount(request.amount());
        pending.setDescription(request.description());
        pending.setRiskScore(assessment.score());
        pending.setRiskLevel(assessment.level());
        pending.setRiskDecision(assessment.decision().name());
        pending.setIdempotencyKey(idempotencyKey);
        pending.setExpiresAt(Instant.now().plus(PENDING_TTL_MINUTES, ChronoUnit.MINUTES));
        return pendingTransferRepository.save(pending);
    }

    /** Rebuilds an assessment from a stored pending transfer. */
    private RiskAssessment rehydrate(PendingTransfer pending) {
        return new RiskAssessment(
                pending.getRiskScore() == null ? 0 : pending.getRiskScore(),
                pending.getRiskLevel() == null ? Enums.RiskLevel.LOW : pending.getRiskLevel(),
                RiskAssessment.Decision.ALLOW,
                "Verified by one-time password after risk review.",
                java.util.List.of(),
                "step_up_confirmed",
                false);
    }

    /**
     * Raises a decision to the stricter of the model's verdict and the bank's
     * amount-based policy. A large transfer always gets a challenge even when
     * the model considers it routine.
     */
    private RiskAssessment.Decision escalate(RiskAssessment.Decision modelDecision,
                                             boolean forceVerification) {
        if (!forceVerification) {
            return modelDecision;
        }
        return modelDecision == RiskAssessment.Decision.ALLOW
                ? RiskAssessment.Decision.STEP_UP
                : modelDecision;
    }

    /**
     * Where a transfer is headed.
     *
     * @param account     internal destination account, null if unresolved
     * @param beneficiary the beneficiary used, null for own-account transfers
     */
    private record Resolution(Account account, Beneficiary beneficiary) {

        String describe() {
            if (beneficiary != null) {
                return beneficiary.displayName();
            }
            return account == null ? "destination account"
                    : "your " + account.getAccountType() + " account";
        }
    }
}
