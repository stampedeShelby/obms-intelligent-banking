package com.obms.dto;

import com.obms.domain.Enums;
import com.obms.service.fraud.RiskAssessment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request and response shapes for the transfer API.
 *
 * <p>Grouped as nested records because they are meaningless apart from one
 * another, and because reading the whole contract of an endpoint in one file is
 * easier to review than tracing six single-record files.</p>
 *
 * <p>Note what the request does <em>not</em> contain: no customer id, and no
 * source account number. The caller identifies the source by its id, and
 * ownership is then proved against the authenticated token. A request body can
 * never assert who it belongs to.</p>
 */
public final class TransferDtos {

    private TransferDtos() {
        // DTO holder
    }

    /**
     * A transfer instruction.
     *
     * @param sourceAccountId      account to debit, must belong to the caller
     * @param beneficiaryId        verified beneficiary to pay, or null
     * @param destinationAccountId the caller's own account to credit, or null
     * @param amount               positive amount with at most two decimals
     * @param description           narrative for the statement
     */
    public record TransferRequest(
            @NotNull(message = "Select the account to transfer from")
            Long sourceAccountId,

            Long beneficiaryId,

            Long destinationAccountId,

            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 15, fraction = 2, message = "Amount may have at most two decimal places")
            BigDecimal amount,

            @Size(max = 255, message = "Description may be at most 255 characters")
            String description
    ) {
    }

    /**
     * Confirmation of a transfer that required a second factor.
     *
     * @param pendingRef handle returned by initiation
     * @param otpCode    the code the customer received
     */
    public record ConfirmRequest(
            @NotNull(message = "Pending reference is required")
            String pendingRef,

            @NotNull(message = "Enter the verification code")
            @Size(min = 4, max = 8, message = "Enter the code that was sent to you")
            String otpCode
    ) {
    }

    /** Outcome states the client must handle. */
    public enum Outcome {
        /** Money has moved; {@code reference} identifies the ledger entry. */
        COMPLETED,
        /** An OTP is required; confirm with {@code pendingRef}. */
        VERIFICATION_REQUIRED,
        /** Parked for analyst review; no money has moved. */
        HELD_FOR_REVIEW
    }

    /**
     * Result of initiating or confirming a transfer.
     *
     * <p>{@code riskFactors} is populated whenever the customer is being asked
     * for something extra, so the UI can explain <em>why</em> rather than
     * showing an unexplained challenge.</p>
     *
     * @param outcome        what happened
     * @param reference      ledger reference, present when completed
     * @param pendingRef     handle for confirmation, present when waiting
     * @param amount         the amount involved
     * @param balanceAfter   source balance after settlement, when completed
     * @param riskScore      0-100 score the model assigned
     * @param riskLevel      band the score falls in
     * @param message        human-readable summary
     * @param riskFactors    explainable contributions behind the score
     */
    public record TransferResult(
            Outcome outcome,
            String reference,
            String pendingRef,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Integer riskScore,
            Enums.RiskLevel riskLevel,
            String message,
            List<RiskAssessment.RiskFactor> riskFactors
    ) {

        public static TransferResult completed(String reference, BigDecimal amount,
                                               BigDecimal balanceAfter, Integer riskScore,
                                               Enums.RiskLevel riskLevel) {
            return new TransferResult(Outcome.COMPLETED, reference, null, amount, balanceAfter,
                    riskScore, riskLevel, "Transfer completed successfully.", List.of());
        }

        public static TransferResult verificationRequired(String pendingRef, BigDecimal amount,
                                                          int riskScore, Enums.RiskLevel riskLevel,
                                                          String reason,
                                                          List<RiskAssessment.RiskFactor> factors) {
            return new TransferResult(Outcome.VERIFICATION_REQUIRED, null, pendingRef, amount, null,
                    riskScore, riskLevel, reason, factors);
        }

        public static TransferResult held(String pendingRef, BigDecimal amount, int riskScore,
                                          Enums.RiskLevel riskLevel, String reason,
                                          List<RiskAssessment.RiskFactor> factors) {
            return new TransferResult(Outcome.HELD_FOR_REVIEW, null, pendingRef, amount, null,
                    riskScore, riskLevel, reason, factors);
        }
    }
}
