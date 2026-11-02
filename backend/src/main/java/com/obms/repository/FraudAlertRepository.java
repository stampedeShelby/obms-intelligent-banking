package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.FraudAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Page<FraudAlert> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<FraudAlert> findByIdAndCustomerId(Long id, Long customerId);

    Page<FraudAlert> findByStatusOrderByRiskScoreDesc(Enums.AlertStatus status, Pageable pageable);

    /** Alerts worth showing the customer: anything above the low band. */
    @Query("""
            select f from FraudAlert f
            where f.customer.id = :customerId
              and f.riskLevel <> 'LOW'
              and f.status in ('OPEN', 'UNDER_REVIEW')
            order by f.createdAt desc
            """)
    List<FraudAlert> findActiveForCustomer(@Param("customerId") Long customerId);

    long countByRiskLevel(Enums.RiskLevel riskLevel);

    long countByStatus(Enums.AlertStatus status);

    @Query("select count(f) from FraudAlert f where f.createdAt >= :since")
    long countSince(@Param("since") Instant since);

    @Query("select count(f) from FraudAlert f where f.riskLevel <> 'LOW' and f.createdAt >= :since")
    long countSuspiciousSince(@Param("since") Instant since);

    /** Risk timeline for the monitoring dashboard. */
    @Query("""
            select f from FraudAlert f
            where f.createdAt >= :since
            order by f.createdAt asc
            """)
    List<FraudAlert> findTimelineSince(@Param("since") Instant since);
}
