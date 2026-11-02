package com.obms.dto;

import com.obms.domain.AuditLog;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.FraudAlert;
import com.obms.domain.Kyc;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin, staff and fraud-analyst projections (module 6). */
public final class AdminDtos {

    private AdminDtos() {
        // DTO holder
    }

    /**
     * System-wide figures for the admin landing page.
     *
     * @param totalCustomers     registered customers
     * @param activeAccounts     accounts in ACTIVE state
     * @param pendingKyc         submissions awaiting review
     * @param transactions7d     ledger rows in the last week
     * @param alerts7d           fraud evaluations in the last week
     * @param suspicious7d       of those, how many scored above LOW
     * @param openInvestigations alerts still open or under review
     */
    public record SystemStats(
            long totalCustomers,
            long activeAccounts,
            long pendingKyc,
            long transactions7d,
            long alerts7d,
            long suspicious7d,
            long openInvestigations
    ) {
    }

    /**
     * Customer record as staff see it.
     *
     * <p>No full ID-proof number and no password material is included. Staff
     * need enough to identify and support a customer, not everything the
     * database holds about them.</p>
     */
    public record CustomerRow(
            Long id,
            String customerNumber,
            String fullName,
            String email,
            String mobile,
            String city,
            String kycStatus,
            int accountCount,
            BigDecimal totalBalance,
            Instant registeredAt
    ) {
        public static CustomerRow of(Customer customer, String kycStatus,
                                     int accountCount, BigDecimal totalBalance) {
            return new CustomerRow(customer.getId(), customer.getCustomerNumber(),
                    customer.fullName(), customer.getEmail(), customer.getMobile(),
                    customer.getCity(), kycStatus, accountCount, totalBalance,
                    customer.getCreatedAt());
        }
    }

    /** An item in the KYC verification queue. */
    public record KycQueueItem(
            Long kycId,
            String customerNumber,
            String fullName,
            String email,
            String idProofType,
            String idProofMasked,
            String addressProofType,
            Enums.KycStatus status,
            Instant submittedAt
    ) {
        public static KycQueueItem from(Kyc kyc) {
            return new KycQueueItem(kyc.getId(),
                    kyc.getCustomer().getCustomerNumber(), kyc.getCustomer().fullName(),
                    kyc.getCustomer().getEmail(), kyc.getIdProofType(), kyc.getIdProofMasked(),
                    kyc.getAddressProofType(), kyc.getStatus(), kyc.getSubmittedAt());
        }
    }

    public record KycDecisionRequest(
            @NotNull(message = "Approve or reject must be specified")
            Boolean approve,

            @Size(max = 255)
            String reason
    ) {
    }

    public record AccountStatusRequest(
            @NotNull(message = "Choose a status")
            Enums.AccountStatus status,

            @Size(max = 255)
            String reason
    ) {
    }

    /**
     * A fraud alert in the investigation panel, including its explanation.
     *
     * @param factorsJson the per-feature contributions the model returned
     */
    public record AlertDetail(
            Long id,
            String customerNumber,
            String customerName,
            String evaluatedRef,
            BigDecimal amount,
            int riskScore,
            Enums.RiskLevel riskLevel,
            String primaryReason,
            String factorsJson,
            String modelVersion,
            String decision,
            Enums.AlertStatus status,
            String reviewedBy,
            Instant reviewedAt,
            String reviewNotes,
            boolean markedFalsePositive,
            Instant raisedAt
    ) {
        public static AlertDetail from(FraudAlert alert) {
            return new AlertDetail(alert.getId(),
                    alert.getCustomer().getCustomerNumber(), alert.getCustomer().fullName(),
                    alert.getEvaluatedRef(), alert.getAmount(), alert.getRiskScore(),
                    alert.getRiskLevel(), alert.getPrimaryReason(), alert.getExplanationJson(),
                    alert.getModelVersion(), alert.getDecision(), alert.getStatus(),
                    alert.getReviewedBy(), alert.getReviewedAt(), alert.getReviewNotes(),
                    alert.isMarkedFalsePositive(), alert.getCreatedAt());
        }
    }

    public record AlertReviewRequest(
            @NotNull(message = "Choose an outcome")
            Enums.AlertStatus status,

            @Size(max = 500)
            String notes
    ) {
    }

    /** An audit trail row. Read-only everywhere. */
    public record AuditRow(
            Long id,
            String actor,
            String action,
            String entityType,
            String entityRef,
            String detail,
            String ipAddress,
            boolean success,
            Instant at
    ) {
        public static AuditRow from(AuditLog log) {
            return new AuditRow(log.getId(), log.getActor(), log.getAction(),
                    log.getEntityType(), log.getEntityRef(), log.getDetail(),
                    log.getIpAddress(), log.isSuccess(), log.getCreatedAt());
        }
    }

    /** Risk distribution for the monitoring dashboard chart. */
    public record RiskDistribution(List<String> labels, List<Long> counts) {
    }
}
