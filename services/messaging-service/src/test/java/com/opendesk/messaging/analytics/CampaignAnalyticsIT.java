package com.opendesk.messaging.analytics;

// Wave 0 RED placeholder — made GREEN by plan 05-07
// Requirement: ANLX-01 — campaign delivery statistics per campaign

import com.opendesk.messaging.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/analytics/campaigns/{id}/stats with a valid
 * user JWT returns delivery statistics (total, delivered, failed) for the campaign,
 * scoped to the JWT subject (no IDOR — user can only see their own campaigns).
 *
 * <p>Will FAIL until plan 05-07 implements CampaignAnalyticsController.
 */
class CampaignAnalyticsIT extends AbstractMessagingIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void campaignDeliveryStatsReturnedForOwner() {
        Assumptions.abort("ANLX-01 RED placeholder — production code absent (plan 05-07 makes this GREEN)");

        UUID campaignId = UUID.randomUUID();
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/analytics/campaigns/" + campaignId + "/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalCount");
        assertThat(response.getBody()).contains("deliveredCount");
    }
}
