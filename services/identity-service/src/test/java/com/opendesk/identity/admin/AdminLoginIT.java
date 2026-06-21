package com.opendesk.identity.admin;

// Wave 0 RED placeholder — made GREEN by plan 05-04
// Requirement: ADMN-01 — admin login with ROLE_ADMIN credentials

import com.opendesk.identity.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that POST /api/v1/auth/admin/login with valid admin credentials
 * returns a JWT containing roles: [ROLE_ADMIN].
 *
 * <p>Will FAIL until plan 05-04 implements AdminLoginController + JwtIssuer.issueAdminToken.
 */
class AdminLoginIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void adminLoginReturnsJwtWithAdminRole() {
        Assumptions.abort("ADMN-01 RED placeholder — production code absent (plan 05-04 makes this GREEN)");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/admin/login",
                Map.of("email", "admin@open-desk.app", "password", "Admin1234!"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
    }
}
