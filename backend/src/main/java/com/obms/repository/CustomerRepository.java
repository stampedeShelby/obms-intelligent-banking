package com.obms.repository;

import com.obms.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByCustomerNumber(String customerNumber);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    @Query("""
            select c from Customer c
            where lower(c.firstName) like lower(concat('%', :q, '%'))
               or lower(c.lastName) like lower(concat('%', :q, '%'))
               or lower(c.email) like lower(concat('%', :q, '%'))
               or c.customerNumber like concat('%', :q, '%')
            """)
    Page<Customer> search(@Param("q") String query, Pageable pageable);
}
