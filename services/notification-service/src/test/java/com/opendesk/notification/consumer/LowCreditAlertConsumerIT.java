package com.opendesk.notification.consumer;

// Requirement: NOTF-03 — low-credit alert notification
// RED → made GREEN by plan 05-06 Task 2

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.notification.AbstractNotificationIntegrationTest;
import com.opendesk.notification.notification.NotificationRepository;
import com.opendesk.notification.notification.NotificationType;
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
 * Verifies that a LowCreditAlert event published to wallet.events creates exactly one
 * LOW_CREDIT notification row (idempotent).
 */
class LowCreditAlertConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void lowCreditAlertEventCreatesNotificationIdempotently() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "availableCredits", 15));
        var props = new MessageProperties();
        props.setContentType("application/json");
        var message = MessageBuilder.withBody(payload.getBytes()).andProperties(props).build();

        rabbitTemplate.send("wallet.events", "wallet.LowCreditAlert", message);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var notifications = notificationRepository.findByUserId(userId);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.LOW_CREDIT);
        });

        // Idempotency check
        rabbitTemplate.send("wallet.events", "wallet.LowCreditAlert", message);
        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(userId)).hasSize(1));
    }
}
