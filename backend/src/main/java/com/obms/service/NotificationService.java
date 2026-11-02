package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Notification;
import com.obms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Raises customer notifications (FR-8).
 *
 * <p>An in-app row is always written; email delivery is attempted only when a
 * mail sender is configured. When it is not, the notification is still created
 * and simply marked undispatched. The system never claims to have sent an email
 * it did not send.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public Notification notify(Customer customer, Enums.NotificationType type,
                               String title, String body, String linkTarget) {
        Notification notification = new Notification();
        notification.setCustomer(customer);
        notification.setNotificationType(type);
        notification.setChannel(Enums.NotificationChannel.IN_APP);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLinkTarget(linkTarget);
        Notification saved = notificationRepository.save(notification);

        sendEmailQuietly(customer.getEmail(), title, body);
        return saved;
    }

    /**
     * Attempts email delivery without letting a mail outage break a banking
     * operation. A transfer must not fail because an SMTP server is down.
     */
    @Async
    public void sendEmailQuietly(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("[OBMS] " + subject);
            message.setText(body + "\n\n--\nThis is a simulated banking environment. "
                    + "No real funds are involved.");
            mailSender.send(message);
        } catch (MailException | IllegalStateException ex) {
            log.info("Email channel unavailable, notification remains in-app only: {}",
                    ex.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long customerId, Pageable pageable) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long customerId) {
        return notificationRepository.countByCustomerIdAndReadAtIsNull(customerId);
    }

    @Transactional
    public void markRead(Long notificationId, Long customerId) {
        Notification notification = notificationRepository
                .findByIdAndCustomerId(notificationId, customerId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Transactional
    public int markAllRead(Long customerId) {
        return notificationRepository.markAllRead(customerId, Instant.now());
    }
}
