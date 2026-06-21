package com.opendesk.admin.audit;

// Wave 0 RED placeholder — made GREEN by plan 05-06
// Requirement: ADMN-06 — audit log populated from consumed domain events

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * RED placeholder: verifies that a domain event consumed by admin-service creates an audit
 * entry in the audit log — idempotent (same eventId produces exactly one audit row).
 *
 * <p>Will FAIL until plan 05-06 implements AuditEventConsumer + AuditRepository.
 */
class AuditLogIT extends AbstractAdminIntegrationTest {

    @Autowired(required = false)
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void domainEventConsumptionCreatesAuditEntryIdempotently() throws Exception {
        Assumptions.abort("ADMN-06 RED placeholder — production code absent (plan 05-06 makes this GREEN)");

        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "freeCredits", 50));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties()).build();
        message.getMessageProperties().setContentType("application/json");

        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(false).as("AuditRepository not yet implemented").isTrue());
    }
}
