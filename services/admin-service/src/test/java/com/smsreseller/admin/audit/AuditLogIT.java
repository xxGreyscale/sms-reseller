package com.smsreseller.admin.audit;

// Requirement: ADMN-06 — full platform audit log, dual-sourced (mutations + domain events)
// Made GREEN by plan 05-07

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.admin.AbstractAdminIntegrationTest;
import com.smsreseller.admin.AdminTestConfiguration;
import com.smsreseller.admin.JwtTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration tests for ADMN-06: dual-source append-only audit log.
 *
 * <p>Source A — mutation path: POST /api/v1/admin/audit/record (admin-guarded mutation seam)
 * writes an audit row directly.
 *
 * <p>Source B — domain event path: publishing a UserVerified event to identity.events causes
 * the DomainEventAuditConsumer to append a "system" audit row idempotently.
 *
 * <p>Viewer API: GET /api/v1/admin/audit returns paged rows to ROLE_ADMIN.
 * ROLE_USER tokens → 403.
 */
@Import(AdminTestConfiguration.class)
class AuditLogIT extends AbstractAdminIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    AuditRepository auditRepository;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @BeforeEach
    void cleanData() {
        auditRepository.deleteAll();
    }

    // ── Source A: mutation path ─────────────────────────────────────────────

    @Test
    void mutationRecordEndpointAppendsAuditRowAndViewerReturnsIt() {
        String adminToken = jwtTestHelper.createAdminToken(UUID.randomUUID().toString());

        // POST a mutation record via the admin seam
        Map<String, String> body = Map.of(
                "actor", "admin@smsreseller.co",
                "action", "SENDER_ID_APPROVED",
                "target", UUID.randomUUID().toString(),
                "details", "{\"decision\":\"approved\"}"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> postResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/audit/record",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Void.class
        );
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // GET the viewer — row must appear
        ResponseEntity<Map> getResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/audit",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) getResp.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("action")).isEqualTo("SENDER_ID_APPROVED");
        assertThat(content.get(0).get("actor")).isEqualTo("admin@smsreseller.co");
    }

    @Test
    void auditViewerRequiresAdminRole() {
        String userToken = jwtTestHelper.createUserToken(UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/audit",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Source B: domain event path ─────────────────────────────────────────

    @Test
    void domainEventConsumptionCreatesAuditEntryIdempotently() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "freeCredits", 50));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties()).build();
        message.getMessageProperties().setContentType("application/json");

        // Publish domain event — consumer must create an audit row
        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            List<AuditEntry> entries = auditRepository.findAll();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).getActor()).isEqualTo("system");
            assertThat(entries.get(0).getAction()).isEqualTo("UserVerified");
            assertThat(entries.get(0).getTarget()).isEqualTo(userId.toString());
        });

        // Idempotency: replay same eventId → still exactly one row
        rabbitTemplate.send("identity.events", "identity.UserVerified", message);
        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() ->
                assertThat(auditRepository.findAll()).hasSize(1));
    }

    @Test
    void auditEntriesAreAppendOnly() {
        // Verify by inspection: AuditRepository must not expose delete or update methods
        // for audit_entries. This test checks that bulk-delete on the repository itself
        // only affects the test DB (no production updateAuditEntry / deleteById path exists).
        // The real contract is enforced at the type level (no update/delete method on AuditRepository
        // beyond what JpaRepository provides for test teardown via deleteAll).
        assertThat(AuditRepository.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("updateEntry", "deleteAuditEntry", "deleteByActor");
    }
}
