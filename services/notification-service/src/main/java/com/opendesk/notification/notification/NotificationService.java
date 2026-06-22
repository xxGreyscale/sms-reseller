package com.opendesk.notification.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Marks a notification as read if it belongs to the given user (D-14, T-06-04-03).
     *
     * <p>IDOR guard: uses a compound (id, userId) lookup so a caller can only mark their
     * own notifications. Returns {@code true} on success; {@code false} if the notification
     * does not exist or is not owned by the given user (controller maps false → 404).
     *
     * @param id     notification UUID from the path
     * @param userId the authenticated user's UUID (from JWT subject — never from request body)
     * @return {@code true} if the notification was found and marked read; {@code false} otherwise
     */
    @Transactional
    public boolean markAsRead(UUID id, UUID userId) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .map(n -> {
                    n.markRead();
                    notificationRepository.save(n);
                    log.info("Marked notification id={} read for userId={}", id, userId);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Notification create(UUID userId, NotificationType type, String title, String body, String payload) {
        Notification notification = new Notification(userId, type, title, body, payload);
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification type={} for userId={}", type, userId);
        return saved;
    }
}
