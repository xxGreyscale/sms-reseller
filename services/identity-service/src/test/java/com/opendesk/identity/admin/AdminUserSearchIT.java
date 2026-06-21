package com.opendesk.identity.admin;

// Wave 0 RED placeholder — made GREEN by plan 05-04
// Requirement: ADMN-02 — admin search and view user accounts

import com.opendesk.identity.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/admin/users?q=search with a valid ROLE_ADMIN JWT
 * returns a paginated list of users matching the query.
 *
 * <p>Will FAIL until plan 05-04 implements AdminUserSearchController.
 */
class AdminUserSearchIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void adminCanSearchUsersByEmail() {
        Assumptions.abort("ADMN-02 RED placeholder — production code absent (plan 05-04 makes this GREEN)");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/users?q=test@example.com", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }
}
