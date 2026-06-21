package com.opendesk.wallet.admin;

// ADMN-03 — admin inspect any user's credit ledger
// RED: fails until AdminLedgerController + SecurityConfig ADMIN guard exist (plan 05-04)

import com.opendesk.wallet.AbstractWalletIntegrationTest;
import com.opendesk.wallet.JwtTestHelper;
import com.opendesk.wallet.transaction.CreditTransaction;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import com.opendesk.wallet.transaction.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ADMN-03: admin ledger inspection endpoint.
 *
 * <p>GET /api/v1/admin/ledger/{userId} returns that user's transaction history
 * to a ROLE_ADMIN caller. ROLE_USER callers receive 403.
 */
class AdminLedgerIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @Autowired
    CreditTransactionRepository txnRepository;

    private UUID targetUserId;

    @BeforeEach
    void setUp() {
        targetUserId = UUID.randomUUID();
        txnRepository.deleteAll();
    }

    @Test
    void adminCanInspectAnyUsersLedger() {
        // Seed one transaction for targetUserId
        txnRepository.save(new CreditTransaction(
                targetUserId, UUID.randomUUID(), TxnType.GRANT, 100, UUID.randomUUID()));

        String adminToken = jwtTestHelper.createAdminToken(UUID.randomUUID().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/ledger/" + targetUserId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
        assertThat(response.getBody()).contains("GRANT");
    }

    @Test
    void userTokenIsRejectedWithForbidden() {
        String userToken = jwtTestHelper.createToken(UUID.randomUUID().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/ledger/" + targetUserId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanInspectAnyUserLedgerEvenWithNoTransactions() {
        String adminToken = jwtTestHelper.createAdminToken(UUID.randomUUID().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/ledger/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }
}
