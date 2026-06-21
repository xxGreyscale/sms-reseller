package com.opendesk.wallet.admin;

// Wave 0 RED placeholder — made GREEN by plan 05-05
// Requirement: ADMN-03 — admin inspect any user's credit ledger

import com.opendesk.wallet.AbstractWalletIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED placeholder: verifies that GET /api/v1/admin/ledger/{userId} with a valid ROLE_ADMIN JWT
 * returns the full credit ledger for the specified user.
 *
 * <p>Will FAIL until plan 05-05 implements AdminLedgerController.
 */
class AdminLedgerIT extends AbstractWalletIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void adminCanInspectAnyUsersLedger() {
        Assumptions.abort("ADMN-03 RED placeholder — production code absent (plan 05-05 makes this GREEN)");

        UUID userId = UUID.randomUUID();
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/ledger/" + userId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("transactions");
    }
}
