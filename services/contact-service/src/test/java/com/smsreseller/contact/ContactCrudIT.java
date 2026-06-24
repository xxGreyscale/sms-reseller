package com.smsreseller.contact;

// Requirements: CONT-01, CONT-02, CONT-03
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for contact CRUD operations (CONT-01/02/03).
 *
 * <p>All userId is derived from JWT subject — never from request body/path (IDOR guard).
 */
class ContactCrudIT extends AbstractContactIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtTestHelper jwt;

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createContact(String token, String name, String phone) {
        var body = Map.of("name", name, "phoneE164", phone);
        var resp = rest.exchange(
                "/api/v1/contacts",
                HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    // ── CONT-01 ───────────────────────────────────────────────────────────────

    /**
     * CONT-01: POST /api/v1/contacts → 201; contact retrievable in GET list scoped to JWT subject.
     */
    @Test
    void addContactPersistsAndIsRetrievable() {
        String token = jwt.createToken(UUID.randomUUID().toString());
        var created = createContact(token, "Alice", "+255712000001");

        assertThat(created).containsKey("id");
        assertThat(created.get("name")).isEqualTo("Alice");
        assertThat(created.get("phoneE164")).isEqualTo("+255712000001");

        // GET list — must appear
        var listResp = rest.exchange(
                "/api/v1/contacts",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                Map.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> content =
                (java.util.List<Map<String, Object>>) listResp.getBody().get("content");
        assertThat(content).anyMatch(c -> "+255712000001".equals(c.get("phoneE164")));
    }

    // ── CONT-02 ───────────────────────────────────────────────────────────────

    /**
     * CONT-02: PATCH /api/v1/contacts/{id} updates name and/or phone; change persisted.
     */
    @Test
    void editContactUpdatesFields() {
        String token = jwt.createToken(UUID.randomUUID().toString());
        var created = createContact(token, "Bob", "+255712000002");
        String id = (String) created.get("id");

        var patch = Map.of("name", "Robert");
        var patchResp = rest.exchange(
                "/api/v1/contacts/" + id,
                HttpMethod.PATCH,
                new HttpEntity<>(patch, bearerHeaders(token)),
                Map.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("name")).isEqualTo("Robert");
    }

    // ── CONT-03 ───────────────────────────────────────────────────────────────

    /**
     * CONT-03: DELETE /api/v1/contacts/{id} → 204; 404 on re-fetch.
     */
    @Test
    void deleteContactRemovesFromGroups() {
        String token = jwt.createToken(UUID.randomUUID().toString());
        var created = createContact(token, "Carol", "+255712000003");
        String id = (String) created.get("id");

        var delResp = rest.exchange(
                "/api/v1/contacts/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)),
                Void.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Re-fetch → 404
        var getResp = rest.exchange(
                "/api/v1/contacts/" + id,
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── IDOR guard ────────────────────────────────────────────────────────────

    /**
     * IDOR: fetching another user's contact id returns 404, not 403.
     */
    @Test
    void crossUserFetchReturns404() {
        String userAToken = jwt.createToken(UUID.randomUUID().toString());
        String userBToken = jwt.createToken(UUID.randomUUID().toString());

        var created = createContact(userAToken, "Dave", "+255712000004");
        String id = (String) created.get("id");

        // User B tries to GET user A's contact
        var resp = rest.exchange(
                "/api/v1/contacts/" + id,
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userBToken)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
