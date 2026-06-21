package com.opendesk.identity.admin;

// Wave 0 RED placeholder — made GREEN by plan 05-06
// Requirement: ADMN-06 — admin view full audit log (admin mutations + domain events)
// Note: AuditLogIT moved here to identity-service to test admin mutation audit entries.
// admin-service AuditLogIT tests the event-driven audit path.

import com.opendesk.identity.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that admin mutations (e.g. user search) create audit log entries.
 *
 * <p>Will FAIL until plan 05-06 implements AuditEntry + AuditRepository + AuditAspect.
 */
class AuditLogIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void adminMutationCreatesAuditEntry() {
        Assumptions.abort("ADMN-06 RED placeholder — production code absent (plan 05-06 makes this GREEN)");

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/audit", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }
}
