package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only record of a security-relevant or money-relevant action.
 *
 * <p>Nothing in the application updates or deletes rows in this table; the
 * admin module exposes it read-only. Actor is stored as a username string
 * rather than a foreign key so that the trail survives the deletion of a user
 * record.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actor, created_at"),
        @Index(name = "idx_audit_action", columnList = "action, created_at")
})
public class AuditLog extends BaseEntity {

    /** Username of the acting principal, or {@code SYSTEM}. */
    @Column(nullable = false, length = 160)
    private String actor;

    /** Dotted action key, e.g. {@code transfer.executed}, {@code auth.login.failed}. */
    @Column(nullable = false, length = 80)
    private String action;

    /** Type of the thing acted upon, e.g. {@code Account}. */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    /** Business identifier of the thing acted upon. */
    @Column(name = "entity_ref", length = 64)
    private String entityRef;

    /** Short outcome summary. Must never contain credentials or OTP values. */
    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success = true;
}
