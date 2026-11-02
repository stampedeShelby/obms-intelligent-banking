package com.obms.repository;

import com.obms.domain.BillPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    Optional<BillPayment> findByReference(String reference);

    Page<BillPayment> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** Unpaid bills with a due date in the window, for the reminder panel. */
    @Query("""
            select b from BillPayment b
            where b.customer.id = :customerId
              and b.status = 'PENDING'
              and b.dueDate is not null
              and b.dueDate <= :before
            order by b.dueDate asc
            """)
    List<BillPayment> findUpcoming(@Param("customerId") Long customerId,
                                   @Param("before") LocalDate before);
}
