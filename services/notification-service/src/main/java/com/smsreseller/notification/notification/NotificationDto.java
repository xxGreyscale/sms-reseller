package com.smsreseller.notification.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO for the feed API (GET /api/v1/notifications).
 */
public record NotificationDto(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        String payload,
        boolean read,
        Instant createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getPayload(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
