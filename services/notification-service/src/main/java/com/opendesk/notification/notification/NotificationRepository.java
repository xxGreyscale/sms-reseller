package com.opendesk.notification.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Feed API — return all notifications for a user, newest first (Pageable carries Sort). */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Used in IT assertions for convenience (no Pageable needed). */
    List<Notification> findByUserId(UUID userId);

    long countByUserIdAndReadFalse(UUID userId);
}
