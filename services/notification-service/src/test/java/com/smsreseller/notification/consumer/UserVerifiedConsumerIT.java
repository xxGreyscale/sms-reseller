package com.smsreseller.notification.consumer;

// Requirement: NOTF-01 — in-app notification: NIDA verification success
// RED → made GREEN by plan 05-06 Task 2

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.notification.AbstractNotificationIntegrationTest;
import com.smsreseller.notification.notification.NotificationRepository;
import com.smsreseller.notification.notification.NotificationType;
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
 * Verifies that a UserVerified event published to identity.events creates exactly one
 * NIDA_VERIFIED notification row (idempotent — replayed eventId must not create a second row).
 * T-05-14: duplicate event double-create prevention via processed_events.tryInsert.
 */
class UserVerifiedConsumerIT extends AbstractNotificationIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void userVerifiedEventCreatesNotificationIdempotently() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId, "userId", userId.toString(), "freeCredits", 50));
        var props = new MessageProperties();
        props.setContentType("application/json");
        var message = MessageBuilder.withBody(payload.getBytes()).andProperties(props).build();

        // First delivery
        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var notifications = notificationRepository.findByUserId(userId);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.NIDA_VERIFIED);
        });

        // Idempotency: re-deliver same eventId — must not create second row
        rabbitTemplate.send("identity.events", "identity.UserVerified", message);
        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(userId)).hasSize(1));
    }
}
