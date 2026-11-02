package com.obms.controller;

import com.obms.domain.Notification;
import com.obms.security.CurrentUser;
import com.obms.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** In-app notification centre (FR-8). */
@Tag(name = "Notifications", description = "In-app notification centre")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @Operation(summary = "Paged notifications, newest first")
    @GetMapping
    public Page<NotificationView> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return notificationService
                .list(currentUser.requireCustomerId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(NotificationView::from);
    }

    @Operation(summary = "Unread count for the badge")
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationService.unreadCount(currentUser.requireCustomerId()));
    }

    @Operation(summary = "Mark one notification read")
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId, currentUser.requireCustomerId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark every notification read")
    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated",
                notificationService.markAllRead(currentUser.requireCustomerId()));
    }

    /**
     * Notification projection.
     *
     * @param dispatched whether an external channel accepted the message
     */
    public record NotificationView(
            Long id,
            String type,
            String title,
            String body,
            String linkTarget,
            boolean read,
            boolean dispatched,
            Instant createdAt
    ) {
        static NotificationView from(Notification notification) {
            return new NotificationView(
                    notification.getId(),
                    notification.getNotificationType().name(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getLinkTarget(),
                    notification.isRead(),
                    notification.isDispatched(),
                    notification.getCreatedAt());
        }
    }

    /** Kept for API discoverability in Swagger. */
    @Operation(summary = "Notification types this API can raise")
    @GetMapping("/types")
    public List<String> types() {
        return java.util.Arrays.stream(com.obms.domain.Enums.NotificationType.values())
                .map(Enum::name)
                .toList();
    }
}
