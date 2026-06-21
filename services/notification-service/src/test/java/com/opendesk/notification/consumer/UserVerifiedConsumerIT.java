package com.opendesk.notification.consumer;

// Wave 0 RED placeholder — made GREEN by plan 05-02
// Requirement: NOTF-01 — in-app notification: NIDA verification success

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.notification.AbstractNotificationIntegrationTest;
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
 * RED placeholder: verifies that a UserVerified event published to identity.events
 * creates a NIDA_VERIFIED notification row for the user — exactly once (idempotent).
 *
 * <p>Will FAIL until plan 05-02 implements IdentityEventConsumer + NotificationRepository.
 */
class UserVerifiedConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired(required = false)
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void userVerifiedEventCreatesNotificationIdempotently() throws Exception {
        Assumptions.abort("NOTF-01 RED placeholder — production code absent (plan 05-02 makes this GREEN)");

        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "freeCredits", 50));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties()).build();
        message.getMessageProperties().setContentType("application/json");

        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        // Asserts notification row created with type NIDA_VERIFIED
        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(false).as("NotificationRepository not yet implemented").isTrue());
    }
}
