package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.PendingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PendingTransferRepository extends JpaRepository<PendingTransfer, Long> {

    Optional<PendingTransfer> findByPendingRef(String pendingRef);

    /** Scoped by customer so one caller cannot confirm another's transfer. */
    Optional<PendingTransfer> findByPendingRefAndCustomerId(String pendingRef, Long customerId);

    List<PendingTransfer> findByCustomerIdAndStatus(Long customerId, Enums.TransactionStatus status);

    @Modifying
    @Query("""
            update PendingTransfer p set p.status = 'FAILED'
            where p.status = 'AWAITING_VERIFICATION' and p.expiresAt < :now
            """)
    int expireStale(@Param("now") Instant now);
}
