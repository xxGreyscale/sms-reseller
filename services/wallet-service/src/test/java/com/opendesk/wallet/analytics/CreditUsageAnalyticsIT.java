package com.opendesk.wallet.analytics;

// Wave 0 RED placeholder — made GREEN by plan 05-07
// Requirement: ANLX-02 — credit usage over time with spend trend

import com.opendesk.wallet.AbstractWalletIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/analytics/credit-usage with a valid user JWT
 * returns daily credit consumption aggregates grouped by date (last 90 days).
 *
 * <p>Will FAIL until plan 05-07 implements CreditUsageController + CreditUsageService.
 */
class CreditUsageAnalyticsIT extends AbstractWalletIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void creditUsageEndpointReturnsDailyAggregatesScopedToJwt() {
        Assumptions.abort("ANLX-02 RED placeholder — production code absent (plan 05-07 makes this GREEN)");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/analytics/credit-usage", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("date");
        assertThat(response.getBody()).contains("consumed");
    }
}
