package com.smsreseller.notification.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Feed API for in-app notifications (NOTF-01..06).
 *
 * <p>T-05-16: Feed scoped strictly to the JWT subject — user A cannot see user B's rows.
 * {@code JwtAuthenticationToken} is injected by Spring Security resource-server and
 * populated from the validated JWT (no SecurityContextHolder thread-local needed).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /**
     * Returns the caller's notification feed, newest first.
     *
     * @param page zero-based page index (default 0)
     * @param size page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken auth) {

        UUID userId = UUID.fromString(auth.getToken().getSubject());
        int clampedSize = Math.min(size, 100);
        Page<NotificationDto> feed = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, clampedSize))
                .map(NotificationDto::from);
        return ResponseEntity.ok(feed);
    }

    /**
     * Mark a single notification as read (D-14 optional, T-06-04-03).
     *
     * <p>IDOR guard: the compound (id, userId) lookup in the service layer ensures a caller
     * can only mark their own notifications. Returns 404 (not 403) when the notification does
     * not exist or belongs to a different user — avoids disclosing existence to attackers.
     *
     * @param id   notification UUID from the path
     * @param auth JWT authentication token (userId always derived from subject — never path param)
     * @return 204 No Content on success; 404 Not Found if not found or not owned
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        boolean updated = notificationService.markAsRead(id, userId);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
