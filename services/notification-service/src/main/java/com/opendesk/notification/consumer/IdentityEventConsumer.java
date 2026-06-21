package com.opendesk.notification.consumer;

import com.opendesk.notification.idempotency.ProcessedEventRepository;
import com.opendesk.notification.notification.NotificationService;
import com.opendesk.notification.notification.NotificationType;
import com.opendesk.notification.push.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Passive consumer for identity.events / identity.UserVerified — NOTF-01.
 *
 * <p>T-05-15: {@code ignoreDeclarationExceptions="true"} — notification-service does NOT own
 * identity.events exchange; passive bind avoids PRECONDITION_FAILED on topology mismatch.
 * T-05-14: idempotency gate via {@link ProcessedEventRepository#tryInsert}.
 * No synchronous HTTP calls in this consumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final NotificationChannel notificationChannel;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.identity.UserVerified", durable = "true"),
            exchange = @Exchange(name = "identity.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(Events.UserVerifiedEvent event) {
        log.debug("Received UserVerified event: eventId={}, userId={}", event.eventId(), event.userId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate UserVerified event ignored: eventId={}", event.eventId());
            return;
        }

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.NIDA_VERIFIED,
                "Identity Verified",
                "Your identity has been successfully verified.",
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-01: NIDA_VERIFIED notification created for userId={} (eventId={})",
                event.userId(), event.eventId());
    }
}
