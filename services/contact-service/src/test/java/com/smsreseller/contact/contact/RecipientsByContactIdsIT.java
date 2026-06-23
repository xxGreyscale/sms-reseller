package com.smsreseller.contact.contact;

// Requirement: MOBL-07
// Implementing plan: 06-03

import com.smsreseller.contact.AbstractContactIntegrationTest;
import com.smsreseller.contact.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GET /api/v1/internal/contacts/recipients-by-ids.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Returns E.164 phones for contactIds owned by the requested userId.</li>
 *   <li>ContactIds belonging to a different user are excluded (IDOR / T-06-03-01).</li>
 *   <li>Suppressed numbers in the requested contacts are excluded (T-06-03-02, MESG-09).</li>
 * </ol>
 *
 * <p>The endpoint is an internal service-to-service call (no JWT auth required —
 * mirrors the existing /api/v1/internal/contacts/recipients posture: permitAll for internal paths).
 */
class RecipientsByContactIdsIT extends AbstractContactIntegrationTest {

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

    @SuppressWarnings("unchecked")
    private String createContact(String token, String name, String phone) {
        var body = Map.of("name", name, "phoneE164", phone);
        var resp = rest.exchange(
                "/api/v1/contacts",
                HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private void suppressPhone(String token, String phone) {
        var body = Map.of("phoneE164", phone);
        rest.exchange("/api/v1/suppression",
                HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)),
                Map.class);
    }

    /**
     * Test 1: GET /api/v1/internal/contacts/recipients-by-ids returns phones for
     * contacts owned by the specified userId.
     */
    @Test
    void returnsPhoneForContactsOwnedByUser() {
        UUID userId = UUID.randomUUID();
        String token = jwt.createToken(userId.toString());

        String contactId1 = createContact(token, "Alice", "+255712001001");
        String contactId2 = createContact(token, "Bob", "+255712001002");

        String url = "/api/v1/internal/contacts/recipients-by-ids"
                + "?contactIds=" + contactId1 + "," + contactId2
                + "&userId=" + userId;

        ResponseEntity<List<String>> resp = rest.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsExactlyInAnyOrder("+255712001001", "+255712001002");
    }

    /**
     * Test 2: ContactIds belonging to another user are excluded (userId scope — T-06-03-01).
     */
    @Test
    void excludesContactsBelongingToAnotherUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = jwt.createToken(userA.toString());
        String tokenB = jwt.createToken(userB.toString());

        // User A's contact
        String contactA = createContact(tokenA, "Charlie", "+255712001003");
        // User B's contact
        createContact(tokenB, "Delta", "+255712001004");

        // Request userId=userA but include contactA (owned by A) — should return A's phone
        String url = "/api/v1/internal/contacts/recipients-by-ids"
                + "?contactIds=" + contactA
                + "&userId=" + userA;

        ResponseEntity<List<String>> resp = rest.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsExactly("+255712001003");

        // Now request userId=userA but pass contactId from user B — should return empty (IDOR excluded)
        // We need user B's contact id for this test
        UUID userB2 = UUID.randomUUID();
        String tokenB2 = jwt.createToken(userB2.toString());
        String contactB = createContact(tokenB2, "Echo", "+255712001005");

        String urlCross = "/api/v1/internal/contacts/recipients-by-ids"
                + "?contactIds=" + contactB
                + "&userId=" + userA;

        ResponseEntity<List<String>> crossResp = rest.exchange(
                urlCross, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(crossResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(crossResp.getBody()).isEmpty();
    }

    /**
     * Test 3: Suppressed numbers among requested contacts are excluded (T-06-03-02, MESG-09).
     */
    @Test
    void excludesSuppressedNumbers() {
        UUID userId = UUID.randomUUID();
        String token = jwt.createToken(userId.toString());

        String contactNormal = createContact(token, "Frank", "+255712001006");
        String contactSuppressed = createContact(token, "Grace", "+255712001007");

        // Suppress Grace's number
        suppressPhone(token, "+255712001007");

        String url = "/api/v1/internal/contacts/recipients-by-ids"
                + "?contactIds=" + contactNormal + "," + contactSuppressed
                + "&userId=" + userId;

        ResponseEntity<List<String>> resp = rest.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsExactly("+255712001006");
        assertThat(resp.getBody()).doesNotContain("+255712001007");
    }
}
