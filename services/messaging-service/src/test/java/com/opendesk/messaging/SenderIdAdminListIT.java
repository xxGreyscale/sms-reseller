package com.opendesk.messaging;

// Requirement: ADMN-04 — admin can view the sender-ID approval queue (gap-closure for Phase 5 05-09)

import com.opendesk.messaging.senderid.SenderIdRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADMN-04: Admin can list the sender-ID approval queue.
 *
 * <p>GET /api/v1/internal/sender-ids?status=REQUESTED returns a Spring Page
 * ({content, totalElements}) of requests, newest-first, ADMIN-guarded. Non-admin → 403.
 */
class SenderIdAdminListIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private SenderIdRepository senderIdRepository;

    private void submitRequest(String senderName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTestHelper.createToken(UUID.randomUUID().toString()));
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/sender-ids/requests",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("senderName", senderName), headers),
                Map.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 201);
    }

    @Test
    void adminCanListRequestedQueueNewestFirst() {
        submitRequest("ALPHA001");
        submitRequest("BETA0002");

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(jwtTestHelper.createAdminToken(UUID.randomUUID().toString()));

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/internal/sender-ids?status=REQUESTED&page=0&size=50",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = resp.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        // newest-first: first element is the most recently created (BETA0002)
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("senderName")).isEqualTo("BETA0002");
        assertThat(first.get("status")).isEqualTo("REQUESTED");
    }

    @Test
    void nonAdminGetsForbidden() {
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(jwtTestHelper.createToken(UUID.randomUUID().toString()));

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/internal/sender-ids?status=REQUESTED",
                HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
