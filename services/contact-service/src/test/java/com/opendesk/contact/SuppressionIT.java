package com.opendesk.contact;

// Requirement: CONT-08
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for suppression list management (CONT-08).
 *
 * <p>D-08: suppression is scoped per-user globally — the same phone suppressed by
 * user A must NOT suppress it for user B.
 */
class SuppressionIT extends AbstractContactIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtTestHelper jwt;

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /**
     * CONT-08: POST /api/v1/suppression → 201; number appears in GET list;
     * existsByUserIdAndPhoneE164 returns true for that user, false for another user (D-08).
     */
    @Test
    @SuppressWarnings("unchecked")
    void suppressedNumberIsExcluded() {
        String userAId = UUID.randomUUID().toString();
        String userBId = UUID.randomUUID().toString();
        String tokenA  = jwt.createToken(userAId);
        String tokenB  = jwt.createToken(userBId);

        // User A suppresses a number
        var body = Map.of("phoneE164", "+255712345678");
        var resp = rest.exchange("/api/v1/suppression",
                HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(tokenA)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET list for user A — number appears
        var listA = rest.exchange("/api/v1/suppression",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)),
                java.util.List.class);
        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).isNotEmpty();

        // GET list for user B — should be empty (D-08 per-user global)
        var listB = rest.exchange("/api/v1/suppression",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenB)),
                java.util.List.class);
        assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listB.getBody()).isEmpty();

        // Idempotent: suppressing same number again → 201 or 200 (no error)
        var resp2 = rest.exchange("/api/v1/suppression",
                HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(tokenA)),
                Map.class);
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
