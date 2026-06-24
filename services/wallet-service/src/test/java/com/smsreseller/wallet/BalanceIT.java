package com.smsreseller.wallet;

// Requirement: WLET-01 — GET /api/v1/wallet/balance returns derived available balance for JWT user
// Covered by: 03-04 (WalletController + BalanceService)

import com.smsreseller.wallet.lot.CreditLotRepository;
import com.smsreseller.wallet.transaction.CreditTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WLET-01: GET /api/v1/wallet/balance.
 *
 * <p>T-03-09: cross-user IDOR — user must only see own balance.
 */
class BalanceIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

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
    void balanceEndpointReturnsDerivedSumOfNonExpiredLotsForJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        String jwt = jwtTestHelper.createToken(userId.toString());

        // Grant a bonus lot directly via service (already tested in 03-02)
        // The balance endpoint must return availableCredits=50 for this user
        seedBonusLot(userId, 50, Instant.now().plus(30, ChronoUnit.DAYS));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/wallet/balance",
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("availableCredits");
        assertThat(response.getBody().get("availableCredits")).isEqualTo(50);
    }

    @Test
    void balanceExcludesOtherUsersLots() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String jwt = jwtTestHelper.createToken(userId.toString());

        // Seed a lot for a DIFFERENT user
        seedBonusLot(otherUserId, 100, Instant.now().plus(30, ChronoUnit.DAYS));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/wallet/balance",
                HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // User has zero credits — other user's lot must not appear
        assertThat(response.getBody().get("availableCredits")).isEqualTo(0);
    }

    @Test
    void balanceRequiresAuthentication() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/wallet/balance", Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void seedBonusLot(UUID userId, int credits, Instant expiresAt) {
        com.smsreseller.wallet.lot.CreditLot lot = com.smsreseller.wallet.lot.CreditLot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .lotType(com.smsreseller.wallet.lot.LotType.BONUS)
                .granted(credits)
                .consumed(0)
                .reserved(0)
                .expiresAt(expiresAt)
                .paymentId(null)
                .build();
        creditLotRepository.save(lot);
    }
}
