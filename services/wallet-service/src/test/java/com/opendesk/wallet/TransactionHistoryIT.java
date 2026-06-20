package com.opendesk.wallet;

// Requirement: WLET-02 — GET /api/v1/wallet/transactions returns paginated credit transactions
// Covered by: 03-04 (WalletController + CreditTransactionRepository)

import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotService;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WLET-02: GET /api/v1/wallet/transactions.
 *
 * <p>T-03-09: cross-user IDOR — user must only see own transactions (never another user's rows).
 */
class TransactionHistoryIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    LotService lotService;

    @Autowired
    CreditLotRepository creditLotRepository;

    @Autowired
    CreditTransactionRepository creditTransactionRepository;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @BeforeEach
    void cleanData() {
        creditTransactionRepository.deleteAll();
        creditLotRepository.deleteAll();
    }

    @Test
    void transactionHistoryReturnsPaginatedResults() throws Exception {
        UUID userId = UUID.randomUUID();
        String jwt = jwtTestHelper.createToken(userId.toString());

        // Grant 2 bonus lots — each grant writes a GRANT CreditTransaction
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 50, expiry);
        lotService.grantBonus(userId, 25, expiry);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/wallet/transactions?page=0&size=10",
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = response.getBody();
        assertThat(body).isNotNull();

        // Paginated response: content array with 2 items (one per grantBonus call)
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(2);

        // Each item has txnType=GRANT
        for (Object item : content) {
            Map<?, ?> txn = (Map<?, ?>) item;
            assertThat(txn.get("txnType")).isEqualTo("GRANT");
        }

        // Most-recent first: second grant (delta=25) should come first
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("delta")).isEqualTo(25);
    }

    @Test
    void transactionHistoryExcludesOtherUsersTransactions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String jwt = jwtTestHelper.createToken(userId.toString());

        // Grant a lot to ANOTHER user
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(otherUserId, 100, expiry);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/wallet/transactions?page=0&size=10",
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // User's transaction list must be empty — other user's GRANT must not appear
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @Test
    void transactionHistoryRequiresAuthentication() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/wallet/transactions", Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
