package com.smsreseller.wallet.consumer;

import com.smsreseller.wallet.lot.LotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer for messaging-service credit lifecycle events (MESG-10, D-15).
 *
 * <p>Listens on {@code messaging.events} TopicExchange (declared by messaging-service).
 * Wallet-service does NOT redeclare this exchange — {@code @QueueBinding} creates the
 * three wallet queues and binds them passively to the already-declared exchange (same
 * pattern as {@link UserVerifiedConsumer} and {@code PaymentConfirmedConsumer}).
 *
 * <p><b>Idempotency (T-04-08):</b> every handler begins with a
 * {@code processedEventRepository.tryInsert(eventId)} guard. If the event_id was already
 * inserted (duplicate delivery), the method returns early without mutating the ledger.
 *
 * <p><b>Atomicity (T-04-09):</b> {@code @Transactional} on each handler ensures the
 * processed_events INSERT and the ledger mutation commit together or both roll back.
 *
 * <p><b>Exchange ownership:</b> wallet-service does NOT declare a {@code messaging.events}
 * {@code TopicExchange} bean. The exchange is owned by messaging-service. The
 * {@code @Exchange} annotation on each {@code @QueueBinding} performs a passive declare
 * (sets up the binding without creating the exchange). In tests, RabbitMQ auto-creates
 * the exchange on first use.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final LotService lotService;

    /**
     * Applies a CONSUME delta when a message was accepted by the upstream SMS provider.
     *
     * <p>Decrements {@code reserved} and increments {@code consumed} on the specified lot.
     *
     * @param event the MessageAccepted payload from messaging-service
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "wallet.messaging.MessageAccepted", durable = "true"),
            exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true"),
            key = "messaging.MessageAccepted"
    ))
    @Transactional
    public void onMessageAccepted(MessageAccepted event) {
        log.debug("Received MessageAccepted: eventId={}, userId={}, lotId={}",
                event.eventId(), event.userId(), event.lotId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate MessageAccepted event ignored: eventId={}", event.eventId());
            return;
        }

        lotService.consumeFromLot(event.userId(), event.lotId());

        log.info("CONSUME applied: userId={} lotId={} (eventId={})",
                event.userId(), event.lotId(), event.eventId());
    }

    /**
     * Applies a RELEASE delta when a message was released (not sent).
     *
     * <p>Decrements {@code reserved} on the specified lot; {@code consumed} is unchanged.
     *
     * @param event the MessageReleased payload from messaging-service
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "wallet.messaging.MessageReleased", durable = "true"),
            exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true"),
            key = "messaging.MessageReleased"
    ))
    @Transactional
    public void onMessageReleased(MessageReleased event) {
        log.debug("Received MessageReleased: eventId={}, userId={}, lotId={}",
                event.eventId(), event.userId(), event.lotId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate MessageReleased event ignored: eventId={}", event.eventId());
            return;
        }

        lotService.releaseFromLot(event.userId(), event.lotId());

        log.info("RELEASE applied: userId={} lotId={} (eventId={})",
                event.userId(), event.lotId(), event.eventId());
    }

    /**
     * Creates a REFUND lot via {@code creditBack} when a message failed permanently.
     *
     * <p>Credits are returned to the user's available balance by creating a new
     * 30-day REFUND lot. The originating {@code messageId} serves as the reference.
     *
     * @param event the MessageRefundDue payload from messaging-service
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "wallet.messaging.MessageRefundDue", durable = "true"),
            exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true"),
            key = "messaging.MessageRefundDue"
    ))
    @Transactional
    public void onMessageRefundDue(MessageRefundDue event) {
        log.debug("Received MessageRefundDue: eventId={}, userId={}, creditsToRefund={}",
                event.eventId(), event.userId(), event.creditsToRefund());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate MessageRefundDue event ignored: eventId={}", event.eventId());
            return;
        }

        lotService.creditBack(event.userId(), event.creditsToRefund(), event.messageId());

        log.info("REFUND lot created: userId={} credits={} messageId={} (eventId={})",
                event.userId(), event.creditsToRefund(), event.messageId(), event.eventId());
    }
}
