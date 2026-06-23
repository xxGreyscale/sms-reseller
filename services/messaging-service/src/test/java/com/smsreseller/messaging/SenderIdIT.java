package com.smsreseller.messaging;

// Requirements: SNDR-02, SNDR-03, SNDR-04
// Implementing plan: 04-08

import com.smsreseller.messaging.outbox.OutboxRepository;
import com.smsreseller.messaging.senderid.SenderIdRepository;
import com.smsreseller.messaging.senderid.SenderIdStatus;
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
 * Integration tests for sender-ID request state machine (SNDR-02, SNDR-03, SNDR-04).
 */
class SenderIdIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private SenderIdRepository senderIdRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    /**
     * SNDR-02: User can request a custom alphanumeric sender ID (max 11 chars).
     *
     * <p>Valid request → 200/201 with status=REQUESTED.
     * senderName > 11 chars → 400 (bean validation).
     * Non-alphanumeric senderName → 400 (bean validation).
     */
    @Test
    void userCanSubmitRequest() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTestHelper.createToken(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Valid request: 11-char alphanumeric sender name
        Map<String, Object> body = Map.of("senderName", "RESELLER001");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sender-ids/requests",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode().value()).isIn(200, 201);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("status")).isEqualTo("REQUESTED");
        assertThat(responseBody.get("senderName")).isEqualTo("RESELLER001");

        // Verify persisted in DB
        UUID requestId = UUID.fromString(responseBody.get("id").toString());
        var saved = senderIdRepository.findById(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SenderIdStatus.REQUESTED);
        assertThat(saved.getUserId()).isEqualTo(UUID.fromString(userId));

        // senderName > 11 chars → 400
        Map<String, Object> tooLong = Map.of("senderName", "TOOLONGNAME1");
        ResponseEntity<Map> badResponse = restTemplate.exchange(
                "/api/v1/sender-ids/requests",
                HttpMethod.POST,
                new HttpEntity<>(tooLong, headers),
                Map.class
        );
        assertThat(badResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * SNDR-03: Admin can approve a sender-ID request → status APPROVED.
     * Non-admin JWT → 403.
     */
    @Test
    void adminApproveTransitionsToApproved() {
        String userId = UUID.randomUUID().toString();
        String adminId = UUID.randomUUID().toString();
        String userToken = jwtTestHelper.createToken(userId);
        String adminToken = jwtTestHelper.createAdminToken(adminId);

        // Submit a sender-ID request as regular user
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        userHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> submitResponse = restTemplate.exchange(
                "/api/v1/sender-ids/requests",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("senderName", "MYORG"), userHeaders),
                Map.class
        );
        assertThat(submitResponse.getStatusCode().value()).isIn(200, 201);
        UUID requestId = UUID.fromString(submitResponse.getBody().get("id").toString());

        // Non-admin user tries to approve → 403
        ResponseEntity<Map> forbiddenResponse = restTemplate.exchange(
                "/api/v1/internal/sender-ids/" + requestId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                Map.class
        );
        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Admin approves → 200
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> approveResponse = restTemplate.exchange(
                "/api/v1/internal/sender-ids/" + requestId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(adminHeaders),
                Map.class
        );
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify status in DB
        var updated = senderIdRepository.findById(requestId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SenderIdStatus.APPROVED);
        assertThat(updated.getDecidedAt()).isNotNull();
    }

    /**
     * SNDR-04: Approve or reject writes a SenderIdDecided outbox entry
     * with decision value in payload.
     */
    @Test
    void senderIdDecidedEventPublished() {
        String userId = UUID.randomUUID().toString();
        String adminId = UUID.randomUUID().toString();
        String userToken = jwtTestHelper.createToken(userId);
        String adminToken = jwtTestHelper.createAdminToken(adminId);

        // Submit sender-ID request
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        userHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> submitResponse = restTemplate.exchange(
                "/api/v1/sender-ids/requests",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("senderName", "REJTEST"), userHeaders),
                Map.class
        );
        assertThat(submitResponse.getStatusCode().value()).isIn(200, 201);
        UUID requestId = UUID.fromString(submitResponse.getBody().get("id").toString());

        long outboxCountBefore = outboxRepository.findBySentFalseOrderByCreatedAtAsc(
                org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .filter(e -> e.getEventType().equals("SenderIdDecided"))
                .count();

        // Admin rejects
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> rejectResponse = restTemplate.exchange(
                "/api/v1/internal/sender-ids/" + requestId + "/reject",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "Name not compliant"), adminHeaders),
                Map.class
        );
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify outbox entry created with SenderIdDecided event type
        var outboxEntries = outboxRepository.findBySentFalseOrderByCreatedAtAsc(
                org.springframework.data.domain.PageRequest.of(0, 100));

        var decidedEntries = outboxEntries.stream()
                .filter(e -> e.getEventType().equals("SenderIdDecided"))
                .toList();

        assertThat(decidedEntries.size()).isGreaterThan((int) outboxCountBefore);

        // Latest entry payload must contain REJECTED decision and senderName
        var latestDecided = decidedEntries.stream()
                .filter(e -> e.getAggregateId().equals(requestId.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SenderIdDecided outbox entry for requestId=" + requestId));

        assertThat(latestDecided.getPayload()).contains("REJECTED");
        assertThat(latestDecided.getPayload()).contains("REJTEST");
        assertThat(latestDecided.getPayload()).contains(userId);
    }
}
