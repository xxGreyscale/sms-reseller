package com.opendesk.messaging;

// Requirements: MESG-01, MESG-03, MESG-08
// Implementing plan: 04-04

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
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for campaign creation and dispatch.
 */
class CampaignIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    /**
     * MESG-01: User can create a bulk SMS campaign targeting one or more contact groups.
     * POST /api/v1/campaigns with group IDs → 201; campaign in DRAFT state.
     */
    @Test
    void createCampaignTargetingGroups() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTestHelper.createToken(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "Test Campaign",
                "body", "Hello from open-desk",
                "senderId", "OPENDESK",
                "groupIds", List.of(UUID.randomUUID().toString())
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("status")).isEqualTo("DRAFT");
        assertThat(responseBody.get("id")).isNotNull();
    }

    /**
     * MESG-03: System reserves credits before campaign QUEUED; refuses with clear error if insufficient.
     */
    @Test
    void insufficientCreditsBlocksQueuedTransition() {
        abort("04-05 plan — requires send pipeline and wallet client stub");
    }

    /**
     * MESG-08: User sees post-send confirmation including credits deducted and messages queued.
     */
    @Test
    void dispatchResponseIncludesCreditsAndCount() {
        abort("04-05 plan — requires send pipeline and wallet client stub");
    }
}
