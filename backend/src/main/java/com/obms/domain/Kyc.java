package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Simulated KYC submission and review record (FR-1).
 *
 * <p>Document identifiers are stored masked - only the last four characters of
 * an ID proof are retained, which is enough to show the customer which document
 * they submitted without holding a full government identifier in the database.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "kyc_records")
public class Kyc extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.KycStatus status = Enums.KycStatus.NOT_SUBMITTED;

    @Column(name = "id_proof_type", length = 40)
    private String idProofType;

    /** Masked reference, e.g. {@code XXXXXX1234}. */
    @Column(name = "id_proof_masked", length = 40)
    private String idProofMasked;

    @Column(name = "address_proof_type", length = 40)
    private String addressProofType;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;
}
