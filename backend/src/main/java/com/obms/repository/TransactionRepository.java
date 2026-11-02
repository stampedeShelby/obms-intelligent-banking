package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReference(String reference);

    List<Transaction> findByTransferGroupRef(String transferGroupRef);

    Page<Transaction> findByAccountIdOrderByBookedAtDesc(Long accountId, Pageable pageable);

    List<Transaction> findTop10ByAccountIdInOrderByBookedAtDesc(List<Long> accountIds);

    /**
     * Statement window for one account, ordered oldest first so a running
     * balance reads naturally down the page.
     */
    @Query("""
            select t from Transaction t
            where t.account.id = :accountId
              and t.bookedAt >= :from
              and t.bookedAt < :to
              and t.status in ('COMPLETED', 'REVERSED')
            order by t.bookedAt asc, t.id asc
            """)
    List<Transaction> findStatementRows(@Param("accountId") Long accountId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    /**
     * Total debited out of an account since a point in time. Used to enforce
     * the daily transfer limit. Only settled debits count.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.id = :accountId
              and t.direction = 'DEBIT'
              and t.status = 'COMPLETED'
              and t.bookedAt >= :since
            """)
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") Instant since);

    /**
     * Completed ledger rows for a customer across all their accounts, used by
     * the analytics, health-score and copilot modules.
     */
    @Query("""
            select t from Transaction t
            where t.account.customer.id = :customerId
              and t.bookedAt >= :from
              and t.status = 'COMPLETED'
            order by t.bookedAt desc
            """)
    List<Transaction> findCustomerHistory(@Param("customerId") Long customerId,
                                          @Param("from") Instant from);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.customer.id = :customerId
              and t.direction = :direction
              and t.status = 'COMPLETED'
              and t.bookedAt >= :from
              and t.bookedAt < :to
            """)
    BigDecimal sumByDirectionBetween(@Param("customerId") Long customerId,
                                     @Param("direction") Enums.TransactionDirection direction,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    /**
     * Debit rows the fraud model uses as behavioural history for a customer.
     */
    @Query("""
            select t from Transaction t
            where t.account.customer.id = :customerId
              and t.direction = 'DEBIT'
              and t.status = 'COMPLETED'
            order by t.bookedAt desc
            """)
    List<Transaction> findDebitHistory(@Param("customerId") Long customerId, Pageable pageable);

    @Query("select count(t) from Transaction t where t.bookedAt >= :since")
    long countSince(@Param("since") Instant since);

    /**
     * Counts a customer's recent debits, which is the "multiple rapid
     * transactions" signal fed to the risk model.
     */
    @Query("""
            select count(t) from Transaction t
            where t.account.customer.id = :customerId
              and t.direction = 'DEBIT'
              and t.bookedAt >= :since
            """)
    long countRecentDebits(@Param("customerId") Long customerId, @Param("since") Instant since);
}
