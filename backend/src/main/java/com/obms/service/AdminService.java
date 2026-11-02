package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import com.obms.dto.AdminDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.AuditLogRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.FraudAlertRepository;
import com.obms.repository.KycRepository;
import com.obms.repository.TransactionRepository;
import com.obms.service.fraud.FraudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Administrative, staff and fraud-analyst operations (module 6).
 *
 * <p>Authorisation is enforced twice, deliberately. The URL patterns in
 * {@code SecurityConfig} gate the endpoints, and {@code @PreAuthorize} on the
 * methods here gates the behaviour. Either alone would be sufficient in the
 * happy path; together they mean a future refactor that moves a method behind
 * a different URL cannot silently widen access.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final KycRepository kycRepository;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogRepository auditLogRepository;
    private final FraudService fraudService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // statistics
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_STAFF', 'FRAUD_ANALYST')")
    @Transactional(readOnly = true)
    public AdminDtos.SystemStats stats() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return new AdminDtos.SystemStats(
                customerRepository.count(),
                accountRepository.countActive(),
                kycRepository.countByStatus(Enums.KycStatus.PENDING),
                transactionRepository.countSince(weekAgo),
                fraudAlertRepository.countSince(weekAgo),
                fraudAlertRepository.countSuspiciousSince(weekAgo),
                fraudAlertRepository.countByStatus(Enums.AlertStatus.OPEN)
                        + fraudAlertRepository.countByStatus(Enums.AlertStatus.UNDER_REVIEW));
    }

    // ------------------------------------------------------------------
    // customer management
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_STAFF')")
    @Transactional(readOnly = true)
    public Page<AdminDtos.CustomerRow> customers(String query, Pageable pageable) {
        Page<Customer> page = query == null || query.isBlank()
                ? customerRepository.findAll(pageable)
                : customerRepository.search(query.trim(), pageable);

        return page.map(customer -> {
            List<Account> accounts = accountRepository
                    .findByCustomerIdOrderByCreatedAtAsc(customer.getId());
            BigDecimal total = accounts.stream().map(Account::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String kycStatus = kycRepository.findByCustomerId(customer.getId())
                    .map(kyc -> kyc.getStatus().name())
                    .orElse(Enums.KycStatus.NOT_SUBMITTED.name());
            return AdminDtos.CustomerRow.of(customer, kycStatus, accounts.size(), total);
        });
    }

    // ------------------------------------------------------------------
    // KYC queue
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_STAFF')")
    @Transactional(readOnly = true)
    public Page<AdminDtos.KycQueueItem> kycQueue(Pageable pageable) {
        return kycRepository
                .findByStatusOrderBySubmittedAtAsc(Enums.KycStatus.PENDING, pageable)
                .map(AdminDtos.KycQueueItem::from);
    }

    /**
     * Approves or rejects a KYC submission.
     *
     * <p>Approval is what unlocks transfers for the customer, so the decision,
     * the reviewer and the timestamp are all recorded on the KYC row and in the
     * audit trail.</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'BANK_STAFF')")
    @Transactional
    public AdminDtos.KycQueueItem decideKyc(Long kycId, String reviewer,
                                             AdminDtos.KycDecisionRequest request) {
        Kyc kyc = kycRepository.findById(kycId)
                .orElseThrow(() -> ApiException.notFound("KYC record"));

        if (kyc.getStatus() != Enums.KycStatus.PENDING) {
            throw ApiException.invalidState("This submission is " + kyc.getStatus() + ".");
        }
        if (Boolean.FALSE.equals(request.approve())
                && (request.reason() == null || request.reason().isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED",
                    "A rejection must state a reason, which the customer will see.");
        }

        boolean approved = Boolean.TRUE.equals(request.approve());
        kyc.setStatus(approved ? Enums.KycStatus.VERIFIED : Enums.KycStatus.REJECTED);
        kyc.setReviewedAt(Instant.now());
        kyc.setReviewedBy(reviewer);
        kyc.setRejectionReason(approved ? null : request.reason());

        auditService.record(approved ? "kyc.approved" : "kyc.rejected", "Kyc",
                String.valueOf(kycId),
                (approved ? "Approved" : "Rejected: " + request.reason())
                        + " for " + kyc.getCustomer().getCustomerNumber());

        notificationService.notify(kyc.getCustomer(), Enums.NotificationType.SECURITY,
                approved ? "KYC verified" : "KYC could not be verified",
                approved
                        ? "Your identity has been verified. Transfers are now available."
                        : "Your submission was not accepted: " + request.reason()
                                + " You can submit again from your profile.",
                "/profile");

        return AdminDtos.KycQueueItem.from(kyc);
    }

    // ------------------------------------------------------------------
    // account status
    // ------------------------------------------------------------------

    /**
     * Freezes, reactivates or closes an account.
     *
     * <p>Restricted to ADMIN. Freezing an account stops money movement, so it
     * is a control that branch staff should request rather than apply.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void setAccountStatus(Long accountId, AdminDtos.AccountStatusRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account"));

        if (request.status() == Enums.AccountStatus.CLOSED
                && account.getBalance().signum() != 0) {
            throw ApiException.invalidState(
                    "An account holding a balance of " + account.getBalance()
                            + " cannot be closed. Transfer the funds out first.");
        }

        Enums.AccountStatus previous = account.getStatus();
        account.setStatus(request.status());

        auditService.record("account.status_changed", "Account", account.getAccountNumber(),
                previous + " to " + request.status()
                        + (request.reason() == null ? "" : " - " + request.reason()));

        notificationService.notify(account.getCustomer(), Enums.NotificationType.SECURITY,
                "Account status changed",
                "Your account ending " + tail(account.getAccountNumber()) + " is now "
                        + request.status() + "."
                        + (request.reason() == null ? "" : " Reason: " + request.reason()),
                "/accounts");
    }

    // ------------------------------------------------------------------
    // fraud investigation
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    @Transactional(readOnly = true)
    public Page<AdminDtos.AlertDetail> investigationQueue(Enums.AlertStatus status,
                                                           Pageable pageable) {
        Enums.AlertStatus target = status == null ? Enums.AlertStatus.OPEN : status;
        return fraudService.queue(target, pageable).map(AdminDtos.AlertDetail::from);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    @Transactional(readOnly = true)
    public AdminDtos.AlertDetail alert(Long alertId) {
        return fraudAlertRepository.findById(alertId)
                .map(AdminDtos.AlertDetail::from)
                .orElseThrow(() -> ApiException.notFound("Alert"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    @Transactional
    public AdminDtos.AlertDetail reviewAlert(Long alertId, String analyst,
                                              AdminDtos.AlertReviewRequest request) {
        var reviewed = fraudService.review(alertId, request.status(), analyst, request.notes());
        auditService.record("fraud.alert.reviewed", "FraudAlert", String.valueOf(alertId),
                "Set to " + request.status());

        if (request.status() == Enums.AlertStatus.CONFIRMED_FRAUD) {
            notificationService.notify(reviewed.getCustomer(),
                    Enums.NotificationType.SUSPICIOUS_ACTIVITY,
                    "Confirmed unauthorised activity",
                    "Our team confirmed suspicious activity of " + reviewed.getAmount()
                            + " on your account and has recorded it for investigation.",
                    "/fraud-alerts");
        }
        return AdminDtos.AlertDetail.from(reviewed);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    @Transactional(readOnly = true)
    public FraudService.MonitoringSummary monitoring(int days) {
        return fraudService.monitoringSummary(days <= 0 ? 7 : days);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    @Transactional(readOnly = true)
    public AdminDtos.RiskDistribution riskDistribution() {
        return new AdminDtos.RiskDistribution(
                List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"),
                List.of(fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.LOW),
                        fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.MEDIUM),
                        fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.HIGH),
                        fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.CRITICAL)));
    }

    // ------------------------------------------------------------------
    // audit trail
    // ------------------------------------------------------------------

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AdminDtos.AuditRow> auditTrail(String action, String actor, Pageable pageable) {
        return auditLogRepository
                .search(blankToNull(action), blankToNull(actor), pageable)
                .map(AdminDtos.AuditRow::from);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String tail(String accountNumber) {
        return accountNumber.length() <= 4
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - 4);
    }
}
