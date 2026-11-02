package com.obms.repository;

import com.obms.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<Notification> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerIdAndReadAtIsNull(Long customerId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.customer.id = :customerId and n.readAt is null")
    int markAllRead(@Param("customerId") Long customerId, @Param("now") Instant now);
}
