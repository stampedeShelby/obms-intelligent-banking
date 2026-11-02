package com.obms.repository;

import com.obms.domain.FinancialScoreSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialScoreSnapshotRepository extends JpaRepository<FinancialScoreSnapshot, Long> {

    Optional<FinancialScoreSnapshot> findByCustomerIdAndPeriod(Long customerId, String period);

    List<FinancialScoreSnapshot> findTop12ByCustomerIdOrderByPeriodDesc(Long customerId);
}
