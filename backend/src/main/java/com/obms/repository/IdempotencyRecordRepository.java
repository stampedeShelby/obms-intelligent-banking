package com.obms.repository;

import com.obms.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUsername(String idempotencyKey, String username);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
