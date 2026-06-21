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
 * Passive consumer for messaging.events — NOTF-05 (CampaignCompleted) + NOTF-06 (SenderIdDecided).
 *
 * <p>CampaignCompleted payload contract from 05-02:
 * {eventId, campaignId, userId, totalCount, deliveredCount, failedCount}.
 *
 * <p>T-05-15: ignoreDeclarationExceptions="true" on both @Exchange annotations.
 * T-05-14: idempotency gate on both handlers.
 * No synchronous HTTP calls in this consumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final NotificationChannel notificationChannel;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.messaging.CampaignCompleted", durable = "true"),
            exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "messaging.CampaignCompleted"
    ))
    @Transactional
    public void onCampaignCompleted(Events.CampaignCompletedEvent event) {
        log.debug("Received CampaignCompleted event: eventId={}, campaignId={}, userId={}",
                event.eventId(), event.campaignId(), event.userId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate CampaignCompleted event ignored: eventId={}", event.eventId());
            return;
        }

        int total = event.totalCount() != null ? event.totalCount() : 0;
        int delivered = event.deliveredCount() != null ? event.deliveredCount() : 0;
        int failed = event.failedCount() != null ? event.failedCount() : 0;

        String body = String.format(
                "Campaign completed: %d/%d messages delivered (%d failed).",
                delivered, total, failed);

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.CAMPAIGN_COMPLETED,
                "Campaign Completed",
                body,
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-05: CAMPAIGN_COMPLETED notification created for userId={} (eventId={})",
                event.userId(), event.eventId());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.messaging.SenderIdDecided", durable = "true"),
            exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "messaging.SenderIdDecided"
    ))
    @Transactional
    public void onSenderIdDecided(Events.SenderIdDecidedEvent event) {
        log.debug("Received SenderIdDecided event: eventId={}, userId={}, senderId={}, decision={}",
                event.eventId(), event.userId(), event.senderId(), event.decision());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate SenderIdDecided event ignored: eventId={}", event.eventId());
            return;
        }

        boolean approved = "APPROVED".equalsIgnoreCase(event.decision());
        String title = approved ? "Sender ID Approved" : "Sender ID Rejected";
        String body = approved
                ? String.format("Your sender ID '%s' has been approved. You can now use it for campaigns.", event.senderId())
                : String.format("Your sender ID '%s' was rejected. Please contact support for details.", event.senderId());

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.SENDER_ID_DECIDED,
                title,
                body,
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-06: SENDER_ID_DECIDED notification created for userId={} decision={} (eventId={})",
                event.userId(), event.decision(), event.eventId());
    }
}
