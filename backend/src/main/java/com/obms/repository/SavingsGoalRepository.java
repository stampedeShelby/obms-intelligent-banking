package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByCustomerIdOrderByTargetDateAsc(Long customerId);

    List<SavingsGoal> findByCustomerIdAndStatus(Long customerId, Enums.GoalStatus status);

    Optional<SavingsGoal> findByIdAndCustomerId(Long id, Long customerId);
}
