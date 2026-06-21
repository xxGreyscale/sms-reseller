package com.opendesk.notification.consumer;

// Wave 0 RED placeholder — made GREEN by plan 05-02
// Requirement: NOTF-02 — in-app notification: payment confirmed

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
 * RED placeholder: verifies that a PaymentConfirmed event published to payment.events
 * creates a PAYMENT_CONFIRMED notification row for the user — exactly once (idempotent).
 *
 * <p>Will FAIL until plan 05-02 implements PaymentEventConsumer + NotificationRepository.
 */
class PaymentConfirmedConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired(required = false)
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void paymentConfirmedEventCreatesNotificationIdempotently() throws Exception {
        Assumptions.abort("NOTF-02 RED placeholder — production code absent (plan 05-02 makes this GREEN)");

        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(),
                "amountTzs", 5000, "smsCredits", 100));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties()).build();
        message.getMessageProperties().setContentType("application/json");

        rabbitTemplate.send("payment.events", "payment.PaymentConfirmed", message);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(false).as("NotificationRepository not yet implemented").isTrue());
    }
}
