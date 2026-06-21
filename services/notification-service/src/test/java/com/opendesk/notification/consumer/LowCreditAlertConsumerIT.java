package com.opendesk.notification.consumer;

// Wave 0 RED placeholder — made GREEN by plan 05-02
// Requirement: NOTF-03 — low-credit alert notification

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
 * RED placeholder: verifies that a LowCreditAlert event published to wallet.events
 * creates a LOW_CREDIT_ALERT notification row for the user — exactly once (idempotent).
 *
 * <p>Will FAIL until plan 05-02 implements WalletEventConsumer + NotificationRepository.
 */
class LowCreditAlertConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired(required = false)
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void lowCreditAlertEventCreatesNotificationIdempotently() throws Exception {
        Assumptions.abort("NOTF-03 RED placeholder — production code absent (plan 05-02 makes this GREEN)");

        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "availableCredits", 15));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties()).build();
        message.getMessageProperties().setContentType("application/json");

        rabbitTemplate.send("wallet.events", "wallet.LowCreditAlert", message);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(false).as("NotificationRepository not yet implemented").isTrue());
    }
}
