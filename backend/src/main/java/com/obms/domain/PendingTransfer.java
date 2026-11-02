package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transfer that has passed validation but is waiting on a second factor or
 * on analyst review.
 *
 * <p>This exists so that the step-up flow cannot be subverted. The alternative
 * - having the client resend the transfer details along with the OTP - would
 * let a caller verify a small transfer and then submit a large one with the
 * same code. Here the amount, source and destination are fixed at initiation
 * and the confirmation step supplies nothing but the code.</p>
 *
 * <p>No money has moved while a row sits in this table. Balances change only
 * in the ledger write that happens on confirmation.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pending_transfers", indexes = {
        @Index(name = "idx_pending_ref", columnList = "pending_ref", unique = true),
        @Index(name = "idx_pending_customer", columnList = "customer_id, status")
})
public class PendingTransfer extends BaseEntity {

    /** Opaque handle the client presents at confirmation. */
    @Column(name = "pending_ref", nullable = false, unique = true, length = 32)
    private String pendingRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    /** Set when the destination is an account inside OBMS. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private Beneficiary beneficiary;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.TransactionStatus status = Enums.TransactionStatus.AWAITING_VERIFICATION;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private Enums.RiskLevel riskLevel;

    /** Decision that put this transfer into a waiting state. */
    @Column(name = "risk_decision", length = 20)
    private String riskDecision;

    @Lob
    @Column(name = "risk_explanation_json", columnDefinition = "TEXT")
    private String riskExplanationJson;

    /** Idempotency key the initiation was made under, replayed on confirmation. */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Reference of the ledger entry created once this transfer completes. */
    @Column(name = "resulting_reference", length = 32)
    private String resultingReference;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isAwaiting() {
        return status == Enums.TransactionStatus.AWAITING_VERIFICATION && !isExpired();
    }
}
