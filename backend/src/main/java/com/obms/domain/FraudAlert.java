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
 * The audit record of one fraud evaluation (innovation module 2).
 *
 * <p>This row is written for every scored transaction, not only for suspicious
 * ones, because the rubric for an explainable model is that you can show why a
 * clean transaction was judged clean as well. {@code explanationJson} holds the
 * per-feature contributions returned by the model, which is what the
 * investigation panel renders - the system never shows a bare score.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fraud_alerts", indexes = {
        @Index(name = "idx_fraud_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_fraud_status", columnList = "status"),
        @Index(name = "idx_fraud_level", columnList = "risk_level")
})
public class FraudAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** Reference of the evaluated operation, retained even if no ledger row exists. */
    @Column(name = "evaluated_ref", length = 32)
    private String evaluatedRef;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private Enums.RiskLevel riskLevel;

    /** Short human-readable headline, e.g. "Amount 8x your usual transfer". */
    @Column(name = "primary_reason", nullable = false, length = 255)
    private String primaryReason;

    /** JSON array of {feature, value, contribution, explanation} objects. */
    @Lob
    @Column(name = "explanation_json", columnDefinition = "TEXT")
    private String explanationJson;

    /** Which model produced the score, e.g. {@code isolation_forest_v1}. */
    @Column(name = "model_version", length = 40)
    private String modelVersion;

    /** Action the system took: ALLOW, STEP_UP, HOLD, BLOCK. */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.AlertStatus status = Enums.AlertStatus.OPEN;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    /**
     * True when an analyst or the customer marked this alert as a false
     * positive. Collected as model feedback.
     */
    @Column(name = "marked_false_positive", nullable = false)
    private boolean markedFalsePositive;
}
