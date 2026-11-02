package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time password challenge (FR-2).
 *
 * <p>Security properties of this table, which are the point of it existing:</p>
 * <ul>
 *   <li>Only a BCrypt hash of the code is stored. The plaintext OTP exists
 *       for the lifetime of one method call and is never persisted, logged, or
 *       returned by any customer-facing endpoint.</li>
 *   <li>Each token is scoped to a {@link Enums.OtpPurpose} and to the operation
 *       it authorises ({@code targetRef}), so an OTP issued for login cannot be
 *       replayed to approve a transfer.</li>
 *   <li>{@code consumedAt} makes verification single-use; attempts are counted
 *       so a brute-force guess exhausts the token rather than the attacker's
 *       patience.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "otp_tokens", indexes = {
        @Index(name = "idx_otp_lookup", columnList = "username, purpose, consumed_at")
})
public class OtpToken extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String username;

    /** BCrypt hash of the 6-digit code. Never the code itself. */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.OtpPurpose purpose;

    /**
     * Identifier of the thing being authorised - a pending transfer reference,
     * a beneficiary id, and so on. Binds the OTP to a single operation.
     */
    @Column(name = "target_ref", length = 64)
    private String targetRef;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExhausted() {
        return attempts >= maxAttempts;
    }

    /**
     * @return true when this token can still be presented for verification
     */
    public boolean isUsable() {
        return !isExpired() && !isConsumed() && !isExhausted();
    }
}
