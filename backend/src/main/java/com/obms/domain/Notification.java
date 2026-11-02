package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An alert raised for a customer (FR-8).
 *
 * <p>Every notification is persisted for the in-app notification centre
 * regardless of whether an email or SMS channel was also used. The delivery
 * side-channel is simulated unless SMTP credentials are configured, and the
 * {@code dispatched} flag records which of the two happened.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_customer", columnList = "customer_id, read_at")
})
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private Enums.NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.NotificationChannel channel = Enums.NotificationChannel.IN_APP;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** Deep-link target inside the SPA, e.g. {@code /transactions/TXN123}. */
    @Column(name = "link_target", length = 160)
    private String linkTarget;

    @Column(name = "read_at")
    private Instant readAt;

    /** True when the external channel accepted the message. */
    @Column(nullable = false)
    private boolean dispatched;

    public boolean isRead() {
        return readAt != null;
    }
}
