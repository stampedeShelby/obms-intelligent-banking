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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A bank account with a monetary balance.
 *
 * <p>Two safeguards on this entity carry the integrity guarantee the report
 * calls for:</p>
 * <ul>
 *   <li>{@code balance} is a {@link BigDecimal} mapped to DECIMAL(19,2).
 *       Floating point is never used for money anywhere in OBMS.</li>
 *   <li>{@code version} enables JPA optimistic locking, so two concurrent
 *       transfers touching the same account cannot both commit a stale balance.
 *       The transfer service additionally takes a pessimistic row lock for the
 *       debit path; the version column is the second line of defence.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_account_customer", columnList = "customer_id")
})
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private Enums.AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.AccountStatus status = Enums.AccountStatus.ACTIVE;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Balance may not be drawn below this floor. Zero for savings accounts. */
    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    /** Per-day outbound transfer cap, enforced in the transfer service. */
    @Column(name = "daily_transfer_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit = new BigDecimal("100000.00");

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "nickname", length = 60)
    private String nickname;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Funds that may actually be moved out of this account.
     *
     * @return balance less the minimum balance floor, never negative
     */
    public BigDecimal availableBalance() {
        BigDecimal available = balance.subtract(minimumBalance);
        return available.signum() < 0 ? BigDecimal.ZERO : available;
    }

    /**
     * @return true when the account is in a state that permits money movement
     */
    public boolean isOperational() {
        return status == Enums.AccountStatus.ACTIVE;
    }
}
