package com.opendesk.notification.consumer;

// Requirement: NOTF-04 — 7-day credit expiry warning notification
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
 * Verifies that an ExpiryWarning event published to wallet.events creates exactly one
 * EXPIRY_WARNING notification row (idempotent).
 */
class ExpiryWarningConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void expiryWarningEventCreatesNotificationIdempotently() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(),
                "remainingCredits", 80, "expiresAt", "2026-06-28T08:00:00Z"));
        var props = new MessageProperties();
        props.setContentType("application/json");
        var message = MessageBuilder.withBody(payload.getBytes()).andProperties(props).build();

        rabbitTemplate.send("wallet.events", "wallet.ExpiryWarning", message);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var notifications = notificationRepository.findByUserId(userId);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.EXPIRY_WARNING);
        });

        // Idempotency check
        rabbitTemplate.send("wallet.events", "wallet.ExpiryWarning", message);
        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(userId)).hasSize(1));
    }
}
