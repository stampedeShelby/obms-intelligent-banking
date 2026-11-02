package com.obms.repository;

import com.obms.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read and append only. No update or delete method is exposed here, and the
 * admin API offers none, which is what makes the trail trustworthy.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:actor is null or a.actor = :actor)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("actor") String actor,
                          Pageable pageable);
}
