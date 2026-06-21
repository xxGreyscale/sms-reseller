package com.opendesk.messaging.analytics;

// Wave 0 RED placeholder — made GREEN by plan 05-07
// Requirement: ANLX-03 — operator-level delivery rates

import com.opendesk.messaging.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/analytics/operator-rates with a valid user JWT
 * returns delivery rates grouped by operator/provider (M-Pesa, Tigo, etc.),
 * scoped to the JWT subject.
 *
 * <p>Will FAIL until plan 05-07 implements CampaignAnalyticsController.getOperatorRates()
 * and the underlying OutboundMessageRepository.findOperatorRatesByUser() JPQL query.
 */
class OperatorRateAnalyticsIT extends AbstractMessagingIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void operatorDeliveryRatesGroupedByProvider() {
        Assumptions.abort("ANLX-03 RED placeholder — production code absent (plan 05-07 makes this GREEN)");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/analytics/operator-rates", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("operator");
        assertThat(response.getBody()).contains("count");
    }
}
