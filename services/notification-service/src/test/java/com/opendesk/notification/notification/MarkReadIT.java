package com.opendesk.notification.notification;

// Requirement: D-14 (optional) — PATCH /api/v1/notifications/{id}/read IDOR-protected mark-as-read
// RED → made GREEN by plan 06-04 Task 2

import com.opendesk.notification.AbstractNotificationIntegrationTest;
import com.opendesk.notification.JwtTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: PATCH /api/v1/notifications/{id}/read marks the caller's own notification
 * as read; 404 (IDOR) for another user's notification; 404 for a random UUID.
 *
 * <p>Behaviors tested:
 * <ol>
 *   <li>PATCH /{ownId}/read → 204 and read flag becomes true.</li>
 *   <li>PATCH /{otherUsersId}/read → 404; read flag unchanged (IDOR guard).</li>
 *   <li>PATCH /{randomUuid}/read → 404.</li>
 * </ol>
 */
class MarkReadIT extends AbstractNotificationIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    private final JwtTestHelper jwtHelper = new JwtTestHelper();

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    // ── Test 1: PATCH own notification → 204, read=true ──

    @Test
    void patchOwnNotification_returns204AndSetsReadTrue() {
        UUID owner = UUID.randomUUID();
        Notification n = notificationRepository.save(
                new Notification(owner, NotificationType.NIDA_VERIFIED, "Verified", "Your identity verified.", null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtHelper.generateToken(owner));

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/notifications/" + n.getId() + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Notification updated = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(updated.isRead()).as("read flag must be true after PATCH").isTrue();
    }

    // ── Test 2: PATCH another user's notification → 404, read unchanged (IDOR) ──

    @Test
    void patchOtherUsersNotification_returns404AndReadFlagUnchanged() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        Notification n = notificationRepository.save(
                new Notification(owner, NotificationType.PAYMENT_CONFIRMED, "Payment", "Payment received.", null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtHelper.generateToken(attacker));

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/notifications/" + n.getId() + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // read flag must remain false — IDOR guard prevents mutation
        Notification unchanged = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(unchanged.isRead()).as("IDOR: read flag must remain false when access is denied").isFalse();
    }

    // ── Test 3: PATCH random UUID → 404 ──

    @Test
    void patchNonExistentNotification_returns404() {
        UUID anyUser = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtHelper.generateToken(anyUser));

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/notifications/" + UUID.randomUUID() + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
