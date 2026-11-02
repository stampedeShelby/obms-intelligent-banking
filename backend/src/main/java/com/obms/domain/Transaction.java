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
import java.time.Instant;

/**
 * One side of a money movement, written to the ledger.
 *
 * <p>OBMS uses double-entry style recording: an internal transfer produces two
 * rows - a DEBIT on the source account and a CREDIT on the destination - both
 * sharing the same {@code transferGroupRef}. This makes reconciliation and
 * statement generation straightforward and means a half-written transfer is
 * detectable rather than invisible.</p>
 *
 * <p>{@code balanceAfter} is captured at write time so a statement can show a
 * running balance without recomputing history, and so a later balance
 * discrepancy can be traced to the exact row where it appeared.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_account_date", columnList = "account_id, booked_at"),
        @Index(name = "idx_txn_reference", columnList = "reference", unique = true),
        @Index(name = "idx_txn_group", columnList = "transfer_group_ref"),
        @Index(name = "idx_txn_category", columnList = "category")
})
public class Transaction extends BaseEntity {

    /** Customer-visible reference, e.g. {@code TXN20261102A1B2C3}. */
    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    /** Shared by the debit and credit legs of the same transfer. */
    @Column(name = "transfer_group_ref", length = 32)
    private String transferGroupRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** Counterparty account where it is an internal OBMS account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_account_id")
    private Account counterpartyAccount;

    /** Free-text counterparty label, used for external or biller payments. */
    @Column(name = "counterparty_name", length = 120)
    private String counterpartyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private Enums.TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.TransactionStatus status = Enums.TransactionStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private Enums.SpendCategory category;

    /** True when a human corrected the auto-assigned category. */
    @Column(name = "category_user_set", nullable = false)
    private boolean categoryUserSet;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt = Instant.now();

    /** Risk score 0-100 returned by the fraud service, null if not scored. */
    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private Enums.RiskLevel riskLevel;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}
