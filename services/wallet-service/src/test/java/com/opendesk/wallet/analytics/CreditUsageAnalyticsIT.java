package com.opendesk.wallet.analytics;

// ANLX-02 — credit usage over time with spend trend
// RED: fails until CreditUsageController + CreditTransactionRepository.findDailyUsageByUser exist (plan 05-04)

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
 * Integration tests for ANLX-02: JWT-scoped daily credit-usage aggregates.
 *
 * <p>GET /api/v1/analytics/credit-usage returns daily consumption totals for the
 * authenticated caller only. Another user's debits are never exposed (no IDOR).
 */
class CreditUsageAnalyticsIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @Autowired
    CreditTransactionRepository txnRepository;

    private UUID callerUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        callerUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        txnRepository.deleteAll();
    }

    @Test
    void creditUsageEndpointReturnsDailyAggregatesScopedToJwt() {
        // Seed a CONSUME (debit) transaction for the caller
        txnRepository.save(new CreditTransaction(
                callerUserId, UUID.randomUUID(), TxnType.CONSUME, 50, UUID.randomUUID()));

        String token = jwtTestHelper.createToken(callerUserId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analytics/credit-usage",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("date");
        assertThat(response.getBody()).contains("consumed");
    }

    @Test
    void anotherUsersDebitsAreExcluded() {
        // Seed CONSUME for otherUser only
        txnRepository.save(new CreditTransaction(
                otherUserId, UUID.randomUUID(), TxnType.CONSUME, 200, UUID.randomUUID()));

        String token = jwtTestHelper.createToken(callerUserId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analytics/credit-usage",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Caller has no debits — result should be empty array
        assertThat(response.getBody()).contains("[]");
    }

    @Test
    void grantTransactionsAreExcluded() {
        // Seed only a GRANT (credit) transaction for the caller — should NOT appear in usage
        txnRepository.save(new CreditTransaction(
                callerUserId, UUID.randomUUID(), TxnType.GRANT, 100, UUID.randomUUID()));

        String token = jwtTestHelper.createToken(callerUserId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analytics/credit-usage",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Only debits count — GRANT should not appear in usage
        assertThat(response.getBody()).contains("[]");
    }
}
