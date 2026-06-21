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
 * Passive consumer for wallet.events — NOTF-03 (LowCreditAlert) + NOTF-04 (ExpiryWarning).
 *
 * <p>Two routing keys bound to two separate queues (each with its own @RabbitListener method).
 * T-05-15: ignoreDeclarationExceptions="true" on both @Exchange annotations — passive bind.
 * T-05-14: idempotency gate on both handlers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final NotificationChannel notificationChannel;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.wallet.LowCreditAlert", durable = "true"),
            exchange = @Exchange(name = "wallet.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "wallet.LowCreditAlert"
    ))
    @Transactional
    public void onLowCreditAlert(Events.LowCreditAlertEvent event) {
        log.debug("Received LowCreditAlert event: eventId={}, userId={}", event.eventId(), event.userId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate LowCreditAlert event ignored: eventId={}", event.eventId());
            return;
        }

        String body = event.availableCredits() != null
                ? String.format("You have only %d SMS credits remaining. Top up to continue sending.", event.availableCredits())
                : "Your SMS credit balance is low. Please top up.";

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.LOW_CREDIT,
                "Low Credit Alert",
                body,
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-03: LOW_CREDIT notification created for userId={} (eventId={})",
                event.userId(), event.eventId());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.wallet.ExpiryWarning", durable = "true"),
            exchange = @Exchange(name = "wallet.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "wallet.ExpiryWarning"
    ))
    @Transactional
    public void onExpiryWarning(Events.ExpiryWarningEvent event) {
        log.debug("Received ExpiryWarning event: eventId={}, userId={}", event.eventId(), event.userId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate ExpiryWarning event ignored: eventId={}", event.eventId());
            return;
        }

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.EXPIRY_WARNING,
                "Credits Expiring Soon",
                "Your SMS credits will expire within 7 days. Use them before they expire.",
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-04: EXPIRY_WARNING notification created for userId={} (eventId={})",
                event.userId(), event.eventId());
    }
}
