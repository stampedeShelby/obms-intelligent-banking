package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Guards against duplicate debits from a retried or replayed request.
 *
 * <p>The client sends an {@code Idempotency-Key} header with any money-moving
 * request. The key is inserted here inside the same database transaction as the
 * ledger write, under a unique constraint. A second request carrying the same
 * key therefore cannot commit a second debit - the insert fails and the service
 * returns the original result instead.</p>
 *
 * <p>This is the mechanism behind the "duplicate transaction prevention"
 * requirement; a time-window heuristic on amount and payee would also reject
 * legitimate repeat payments, so the explicit key is used.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "idempotency_records", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
public class IdempotencyRecord extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    /** Username the key was issued under, so keys cannot be shared across users. */
    @Column(nullable = false, length = 160)
    private String username;

    /** Logical operation, e.g. {@code transfer} or {@code billpay}. */
    @Column(nullable = false, length = 40)
    private String operation;

    /** Business reference of the result, returned on replay. */
    @Column(name = "result_ref", length = 32)
    private String resultRef;

    public IdempotencyRecord(String idempotencyKey, String username, String operation, String resultRef) {
        this.idempotencyKey = idempotencyKey;
        this.username = username;
        this.operation = operation;
        this.resultRef = resultRef;
    }
}
