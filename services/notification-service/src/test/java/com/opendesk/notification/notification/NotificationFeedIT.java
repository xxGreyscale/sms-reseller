package com.opendesk.notification.notification;

// Requirement: NOTF-01..06 — notification feed API returns user's notifications (JWT-scoped)
// RED → made GREEN by plan 05-06 Task 1

import com.opendesk.notification.AbstractNotificationIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    JwtEncoder jwtEncoder;

    @Autowired
    NotificationRepository notificationRepository;

    private String issueToken(UUID userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("open-desk")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .claim("roles", List.of("ROLE_USER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    void feedReturnsPaginatedResultsScopedToJwt() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        // Persist two notifications for user A and one for user B directly via repo
        Notification n1 = new Notification(userA, NotificationType.NIDA_VERIFIED, "Verified", "Your identity was verified.", null);
        Notification n2 = new Notification(userA, NotificationType.PAYMENT_CONFIRMED, "Payment", "Payment received.", null);
        Notification n3 = new Notification(userB, NotificationType.LOW_CREDIT, "Low credit", "Credits low.", null);
        notificationRepository.save(n1);
        notificationRepository.save(n2);
        notificationRepository.save(n3);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(issueToken(userA));

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
        headers.setBearerAuth(issueToken(userA));

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
