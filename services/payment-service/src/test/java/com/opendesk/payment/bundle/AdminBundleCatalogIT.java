package com.opendesk.payment.bundle;

// Wave 0 RED placeholder — made GREEN by plan 05-08
// Requirement: ADMN-07 — admin view and update bundle catalog (CRUD)

import com.opendesk.payment.AbstractPaymentIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that admin bundle catalog CRUD endpoints are accessible
 * with a ROLE_ADMIN JWT and perform the expected operations on sms_bundles.
 *
 * <p>Will FAIL until plan 05-08 implements AdminBundleController with CRUD operations.
 */
class AdminBundleCatalogIT extends AbstractPaymentIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void adminCanCreateAndListBundles() {
        Assumptions.abort("ADMN-07 RED placeholder — production code absent (plan 05-08 makes this GREEN)");

        // POST — create new bundle
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                "/api/v1/admin/bundles",
                Map.of("name", "Starter Pack", "smsCount", 100, "priceTzs", 5000),
                Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET — list bundles (includes newly created)
        ResponseEntity<String> listResponse = restTemplate.getForEntity(
                "/api/v1/admin/bundles", String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains("Starter Pack");
    }
}
