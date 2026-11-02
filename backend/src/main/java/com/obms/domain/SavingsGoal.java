package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A customer-defined savings target (innovation module 6).
 *
 * <p>Progress is tracked as an explicit {@code savedAmount} rather than being
 * derived from account balance, because a goal is an intention the customer
 * declares and tops up, not a view over their balance.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "savings_goals", indexes = {
        @Index(name = "idx_goal_customer", columnList = "customer_id, status")
})
public class SavingsGoal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "saved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.GoalStatus status = Enums.GoalStatus.ACTIVE;

    @Column(length = 40)
    private String icon;

    /**
     * Completion as a percentage, capped at 100.
     *
     * @return progress percentage with two decimal places
     */
    public BigDecimal progressPercent() {
        if (targetAmount == null || targetAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = savedAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 2, RoundingMode.HALF_UP);
        return pct.min(BigDecimal.valueOf(100));
    }

    public BigDecimal remainingAmount() {
        BigDecimal remaining = targetAmount.subtract(savedAmount);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
