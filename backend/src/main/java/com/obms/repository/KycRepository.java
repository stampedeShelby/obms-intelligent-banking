package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycRepository extends JpaRepository<Kyc, Long> {

    Optional<Kyc> findByCustomerId(Long customerId);

    /** Backs the admin KYC verification queue. */
    Page<Kyc> findByStatusOrderBySubmittedAtAsc(Enums.KycStatus status, Pageable pageable);

    long countByStatus(Enums.KycStatus status);
}
