package com.smsreseller.contact;

// Requirement: CONT-04
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for contact group management (CONT-04).
 */
class ContactGroupIT extends AbstractContactIntegrationTest {

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
    private Map<String, Object> createContact(String token, String name, String phone) {
        var body = Map.of("name", name, "phoneE164", phone);
        var resp = rest.exchange("/api/v1/contacts", HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createGroup(String token, String name) {
        var body = Map.of("name", name);
        var resp = rest.exchange("/api/v1/groups", HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    /**
     * CONT-04: Create group; add 2 contacts; membership persists; remove 1; reflects.
     * Also verifies ON DELETE CASCADE: deleting a contact removes it from groups.
     */
    @Test
    @SuppressWarnings("unchecked")
    void groupMembershipIsPersisted() {
        String token = jwt.createToken(UUID.randomUUID().toString());

        var c1 = createContact(token, "Alice", "+255712100001");
        var c2 = createContact(token, "Bob",   "+255712100002");
        String c1Id = (String) c1.get("id");
        String c2Id = (String) c2.get("id");

        var group = createGroup(token, "Friends");
        String groupId = (String) group.get("id");

        // Add both contacts
        rest.exchange("/api/v1/groups/" + groupId + "/members/" + c1Id,
                HttpMethod.PUT, new HttpEntity<>(bearerHeaders(token)), Void.class);
        rest.exchange("/api/v1/groups/" + groupId + "/members/" + c2Id,
                HttpMethod.PUT, new HttpEntity<>(bearerHeaders(token)), Void.class);

        // List members — both present
        var membersResp = rest.exchange(
                "/api/v1/groups/" + groupId + "/members",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                java.util.List.class);
        assertThat(membersResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(membersResp.getBody()).hasSize(2);

        // Remove c1
        var removeResp = rest.exchange(
                "/api/v1/groups/" + groupId + "/members/" + c1Id,
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)),
                Void.class);
        assertThat(removeResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // List members — only c2
        var afterRemove = rest.exchange(
                "/api/v1/groups/" + groupId + "/members",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                java.util.List.class);
        assertThat(afterRemove.getBody()).hasSize(1);

        // Delete c2 — ON DELETE CASCADE removes from group
        rest.exchange("/api/v1/contacts/" + c2Id, HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), Void.class);

        var afterDelete = rest.exchange(
                "/api/v1/groups/" + groupId + "/members",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                java.util.List.class);
        assertThat(afterDelete.getBody()).isEmpty();
    }
}
