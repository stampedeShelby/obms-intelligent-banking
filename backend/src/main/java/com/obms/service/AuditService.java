package com.obms.service;

import com.obms.domain.AuditLog;
import com.obms.repository.AuditLogRepository;
import com.obms.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>Every method runs in {@code REQUIRES_NEW}. That is deliberate: if a
 * transfer fails and its transaction rolls back, the record that somebody
 * <em>attempted</em> the transfer must survive. An audit trail that disappears
 * along with the failed operation is exactly the trail you need after an
 * incident and no longer have.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityRef, String detail) {
        write(currentUser.actorOrSystem(), action, entityType, entityRef, detail, true, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String entityType, String entityRef, String detail) {
        write(currentUser.actorOrSystem(), action, entityType, entityRef, detail, false, null);
    }

    /** Used by authentication, where the principal is not yet in the context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(String actor, String action, boolean success, String detail, String ipAddress) {
        write(actor, action, "UserAccount", actor, detail, success, ipAddress);
    }

    private void write(String actor, String action, String entityType, String entityRef,
                       String detail, boolean success, String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActor(actor);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityRef(entityRef);
            entry.setDetail(truncate(detail));
            entry.setSuccess(success);
            entry.setIpAddress(ipAddress);
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // An audit write must never be the reason a banking operation fails.
            // The failure is logged loudly instead so it is still visible.
            log.error("Failed to write audit entry for action {}", action, ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }
}
