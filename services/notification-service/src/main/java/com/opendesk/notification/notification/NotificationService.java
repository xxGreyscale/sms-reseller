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

    @Transactional
    public Notification create(UUID userId, NotificationType type, String title, String body, String payload) {
        Notification notification = new Notification(userId, type, title, body, payload);
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification type={} for userId={}", type, userId);
        return saved;
    }
}
