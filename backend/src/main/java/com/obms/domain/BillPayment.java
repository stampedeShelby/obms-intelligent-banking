package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A bill payment instruction and its outcome (FR-6).
 *
 * <p>The ledger entry produced by a successful payment is linked through
 * {@code transaction}, so the money movement and the business event are never
 * recorded independently of one another.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bill_payments", indexes = {
        @Index(name = "idx_billpay_customer", columnList = "customer_id"),
        @Index(name = "idx_billpay_reference", columnList = "reference", unique = true)
})
public class BillPayment extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    /** Consumer number / mobile number / policy number as the biller requires. */
    @Column(name = "consumer_identifier", nullable = false, length = 60)
    private String consumerIdentifier;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.TransactionStatus status = Enums.TransactionStatus.PENDING;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}
