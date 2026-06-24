package com.smsreseller.payment.bundle;

import com.smsreseller.payment.AbstractPaymentIntegrationTest;
import com.smsreseller.payment.JwtTestHelper;
import com.smsreseller.payment.presentation.BundleDto;
import com.smsreseller.payment.presentation.BundleSaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ADMN-07: admin bundle catalog CRUD.
 *
 * <p>Verifies:
 * <ul>
 *   <li>ROLE_ADMIN can create, list, update, delete bundles</li>
 *   <li>smsCount or priceTzs &lt;= 0 → 400 validation error</li>
 *   <li>ROLE_USER → 403 on any /api/v1/admin/bundles mutation</li>
 *   <li>Public GET /api/v1/bundles (PYMT-01) still works for authenticated users</li>
 * </ul>
 */
class AdminBundleCatalogIT extends AbstractPaymentIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtTestHelper jwtTestHelper;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpHeaders adminHeaders() {
        String token = jwtTestHelper.generateAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders userHeaders() {
        String token = jwtTestHelper.generateToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // -------------------------------------------------------------------------
    // ADMN-07: admin CRUD round-trip
    // -------------------------------------------------------------------------

    @Test
    void adminCanCreateBundle() {
        Map<String, Object> body = Map.of(
                "name", "Test Pack " + UUID.randomUUID(),
                "smsCount", 200,
                "priceTzs", 10000,
                "active", true);

        ResponseEntity<BundleDto> response = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                BundleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().smsCount()).isEqualTo(200);
        assertThat(response.getBody().priceTzs()).isEqualTo(10000L);
    }

    @Test
    void adminCanListAllBundles() {
        ResponseEntity<List<BundleDto>> response = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<BundleDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void adminCanUpdateBundle() {
        // Create a bundle first
        String uniqueName = "Update Target " + UUID.randomUUID();
        Map<String, Object> createBody = Map.of(
                "name", uniqueName,
                "smsCount", 100,
                "priceTzs", 5000,
                "active", true);

        ResponseEntity<BundleDto> created = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders()),
                BundleDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = created.getBody().id();

        // Update it
        Map<String, Object> updateBody = Map.of(
                "name", uniqueName + " Updated",
                "smsCount", 150,
                "priceTzs", 7500,
                "active", true);

        ResponseEntity<BundleDto> updated = restTemplate.exchange(
                "/api/v1/admin/bundles/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(updateBody, adminHeaders()),
                BundleDto.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().smsCount()).isEqualTo(150);
        assertThat(updated.getBody().priceTzs()).isEqualTo(7500L);
    }

    @Test
    void adminCanDeleteBundle() {
        String uniqueName = "Delete Target " + UUID.randomUUID();
        Map<String, Object> createBody = Map.of(
                "name", uniqueName,
                "smsCount", 50,
                "priceTzs", 2000,
                "active", true);

        ResponseEntity<BundleDto> created = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders()),
                BundleDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = created.getBody().id();

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/admin/bundles/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void updateNonExistentBundleReturns404() {
        Map<String, Object> body = Map.of(
                "name", "Ghost Bundle",
                "smsCount", 100,
                "priceTzs", 5000,
                "active", true);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/admin/bundles/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(body, adminHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // T-05-13: validation — non-positive price / smsCount
    // -------------------------------------------------------------------------

    @Test
    void zeroPriceIsRejectedWith400() {
        Map<String, Object> body = Map.of(
                "name", "Zero Price Bundle",
                "smsCount", 100,
                "priceTzs", 0,
                "active", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void negativeSmsCountIsRejectedWith400() {
        Map<String, Object> body = Map.of(
                "name", "Negative Count Bundle",
                "smsCount", -10,
                "priceTzs", 5000,
                "active", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // T-05-12: elevation of privilege — ROLE_USER blocked
    // -------------------------------------------------------------------------

    @Test
    void userTokenIsBlockedFromAdminEndpoints() {
        Map<String, Object> body = Map.of(
                "name", "Unauthorized Bundle",
                "smsCount", 100,
                "priceTzs", 5000,
                "active", true);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/admin/bundles",
                HttpMethod.POST,
                new HttpEntity<>(body, userHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------------------
    // PYMT-01 regression: public catalog read still works
    // -------------------------------------------------------------------------

    @Test
    void publicCatalogReadStillWorksForAuthenticatedUser() {
        ResponseEntity<List<BundleDto>> response = restTemplate.exchange(
                "/api/v1/bundles",
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<List<BundleDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
