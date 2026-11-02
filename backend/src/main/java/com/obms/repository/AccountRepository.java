package com.obms.repository;

import com.obms.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Loads an account under a pessimistic write lock.
     *
     * <p>This is the entry point used by the transfer service. Taking a row
     * lock before reading the balance is what makes the check-then-debit
     * sequence safe: without it, two concurrent transfers could both read the
     * same sufficient balance and both debit it.</p>
     *
     * <p>Callers must always lock accounts in a deterministic order (OBMS locks
     * by ascending account id) or two transfers in opposite directions between
     * the same pair of accounts can deadlock.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    @Query("select count(a) from Account a where a.status = 'ACTIVE'")
    long countActive();
}
