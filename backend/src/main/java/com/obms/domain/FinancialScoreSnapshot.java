package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.YearMonth;

/**
 * A month's financial health score for a customer (innovation module 3).
 *
 * <p>Snapshots are stored rather than recomputed on demand so the dashboard can
 * plot a score history, and so a score shown to the customer last month does
 * not silently change when the formula is tuned.</p>
 *
 * <p>This is a simulated personal financial wellness score. It is not a credit
 * score, is not shared with any bureau, and the UI states so.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "financial_score_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_score_customer_period",
                columnNames = {"customer_id", "period"}),
        indexes = @Index(name = "idx_score_customer", columnList = "customer_id"))
public class FinancialScoreSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Period in {@code yyyy-MM} form, e.g. {@code 2026-10}. */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private int score;

    /** EXCELLENT, GOOD, FAIR or NEEDS_ATTENTION. */
    @Column(nullable = false, length = 20)
    private String band;

    /** JSON array of {indicator, value, weight, points, explanation}. */
    @Lob
    @Column(name = "breakdown_json", columnDefinition = "TEXT")
    private String breakdownJson;

    /** Version of the scoring formula that produced this snapshot. */
    @Column(name = "formula_version", length = 20)
    private String formulaVersion;

    public YearMonth periodAsYearMonth() {
        return YearMonth.parse(period);
    }
}
