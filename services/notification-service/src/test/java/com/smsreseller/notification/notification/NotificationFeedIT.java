package com.smsreseller.notification.notification;

// Requirement: NOTF-01..06 — notification feed API returns user's notifications (JWT-scoped)
// RED → made GREEN by plan 05-06 Task 1

import com.smsreseller.notification.AbstractNotificationIntegrationTest;
import com.smsreseller.notification.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: GET /api/v1/notifications returns a user's notification
 * feed scoped to the JWT subject — paginated, sorted by created_at DESC.
 *
 * <p>T-05-16: Feed scoped to auth.getToken().getSubject() — user A cannot see user B's rows.
 */
class NotificationFeedIT extends AbstractNotificationIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    private final JwtTestHelper jwtHelper = new JwtTestHelper();

    @Test
    void feedReturnsPaginatedResultsScopedToJwt() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        // Persist two notifications for user A and one for user B directly via repo
        notificationRepository.save(new Notification(userA, NotificationType.NIDA_VERIFIED, "Verified", "Your identity was verified.", null));
        notificationRepository.save(new Notification(userA, NotificationType.PAYMENT_CONFIRMED, "Payment", "Payment received.", null));
        notificationRepository.save(new Notification(userB, NotificationType.LOW_CREDIT, "Low credit", "Credits low.", null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtHelper.generateToken(userA));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/notifications?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(2);
        // User B's notification must not appear
    }

    @Test
    void feedDoesNotReturnOtherUsersNotifications() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        notificationRepository.save(new Notification(userB, NotificationType.LOW_CREDIT, "Low credit", "Only for B.", null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtHelper.generateToken(userA));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/notifications?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @Test
    void feedRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/notifications", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
