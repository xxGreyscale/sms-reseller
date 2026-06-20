package com.opendesk.payment;

// Requirement: PYMT-01 — Bundle catalog returns all active SMS bundles seeded in V2 migration
// Covered by: 03-03 (BundleController + SmsBundle seed data — Taster/Starter/Growth/Pro/Scale)

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PYMT-01: GET /api/v1/bundles returns the 5 seeded active bundles with correct raw-TZS prices.
 *
 * <p>Assertions:
 * <ul>
 *   <li>5 bundles total</li>
 *   <li>Starter: smsCount=200, priceTzs=3200 (raw TZS per D-11 — NOT 320000)</li>
 *   <li>Taster: priceTzs=0, isPurchasable=false</li>
 *   <li>Response includes name, smsCount, priceTzs, isPurchasable fields</li>
 * </ul>
 */
class BundleCatalogIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Test
    void bundleCatalogReturnsAllActiveSeededBundles() {
        String token = jwtTestHelper.generateToken("test-user-id");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        @SuppressWarnings("rawtypes")
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/bundles",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bundles = (List<Map<String, Object>>) response.getBody();
        assertThat(bundles).isNotNull().hasSize(5);

        // Starter: 200 SMS, 3200 TZS raw (D-11 — whole shillings, NOT ×100)
        Map<String, Object> starter = bundles.stream()
                .filter(b -> "Starter".equals(b.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Starter bundle not found"));
        assertThat(starter.get("smsCount")).isEqualTo(200);
        assertThat(((Number) starter.get("priceTzs")).longValue()).isEqualTo(3200L);
        assertThat(starter.get("isPurchasable")).isEqualTo(true);

        // Taster: FREE, not purchasable via Azampay
        Map<String, Object> taster = bundles.stream()
                .filter(b -> "Taster".equals(b.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Taster bundle not found"));
        assertThat(((Number) taster.get("priceTzs")).longValue()).isEqualTo(0L);
        assertThat(taster.get("isPurchasable")).isEqualTo(false);
    }

    @Test
    void bundleCatalogRequiresAuthentication() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<List> response = restTemplate.getForEntity("/api/v1/bundles", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
