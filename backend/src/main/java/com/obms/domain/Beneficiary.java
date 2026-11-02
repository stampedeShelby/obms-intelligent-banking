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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A payee the customer has registered for transfers (FR-5).
 *
 * <p>A beneficiary must reach {@code ACTIVE} through OTP verification before it
 * can receive money. The {@code verifiedAt} timestamp also feeds the fraud
 * model: a transfer to a beneficiary added minutes ago is a materially
 * different risk from one added months ago.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_beneficiary_owner_account",
                columnNames = {"owner_customer_id", "account_number"}),
        indexes = @Index(name = "idx_beneficiary_owner", columnList = "owner_customer_id"))
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_customer_id", nullable = false)
    private Customer ownerCustomer;

    @Column(name = "beneficiary_name", nullable = false, length = 120)
    private String beneficiaryName;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @Column(length = 60)
    private String nickname;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.BeneficiaryStatus status = Enums.BeneficiaryStatus.PENDING_VERIFICATION;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** True when the account number resolves to an account inside OBMS. */
    @Column(name = "internal_account", nullable = false)
    private boolean internalAccount;

    public String displayName() {
        return nickname != null && !nickname.isBlank() ? nickname : beneficiaryName;
    }
}
