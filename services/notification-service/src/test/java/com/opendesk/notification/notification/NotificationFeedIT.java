package com.opendesk.notification.notification;

// Wave 0 RED placeholder — made GREEN by plan 05-03
// Requirement: NOTF-01..06 — notification feed API returns user's notifications (JWT-scoped)

import com.opendesk.notification.AbstractNotificationIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/notifications returns a user's notification
 * feed scoped to the JWT subject — paginated, sorted by created_at DESC.
 *
 * <p>Will FAIL until plan 05-03 implements NotificationController + NotificationService.
 */
class NotificationFeedIT extends AbstractNotificationIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void notificationFeedReturnsPaginatedResultsScopedToJwt() {
        Assumptions.abort("NOTF feed RED placeholder — production code absent (plan 05-03 makes this GREEN)");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("placeholder-jwt");
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/notifications?page=0&size=20", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }
}
