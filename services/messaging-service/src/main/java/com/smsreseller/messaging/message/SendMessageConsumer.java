package com.smsreseller.messaging.message;

import com.smsreseller.messaging.sms.SmsProvider;
import com.smsreseller.messaging.sms.SmsResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.smsreseller.messaging.config.RabbitMqConfig.DEAD_QUEUE;
import static com.smsreseller.messaging.config.RabbitMqConfig.DLX;
import static com.smsreseller.messaging.config.RabbitMqConfig.RETRY_1_QUEUE;
import static com.smsreseller.messaging.config.RabbitMqConfig.RETRY_2_QUEUE;
import static com.smsreseller.messaging.config.RabbitMqConfig.RETRY_3_QUEUE;
import static com.smsreseller.messaging.config.RabbitMqConfig.SEND_QUEUE;

/**
 * Consumes {@link SendMessagePayload} from the {@code messaging.send} quorum queue.
 *
 * <p>Implements Approach A retry ladder (D-06, 04-RESEARCH §Pattern 1):
 * <ul>
 *   <li>ACCEPTED: OutboundMessage → SENT, write MessageAccepted outbox, ack.</li>
 *   <li>TRANSIENT_FAIL: if attemptCount &lt; 3, republish to next retry queue
 *       ({@code messaging.retry.1m/5m/15m} via DLX), ack original.
 *       If attemptCount == 3 (exhausted), publish to {@code messaging.dead}, ack original.</li>
 *   <li>HARD_FAIL: publish directly to {@code messaging.dead}, ack original
 *       (no retry ladder — T-04-14: bounded retries).</li>
 * </ul>
 *
 * <p>Using Approach A (explicit republish + ack) rather than nack-based DLX ensures:
 * <ul>
 *   <li>HARD_FAIL reaches {@code messaging.dead} immediately (no TTL delay).</li>
 *   <li>The idempotency guard (already SENT/FAILED) can ack safely without stranding messages.</li>
 *   <li>Pitfall 3 (infinite loop) is avoided: attemptCount is incremented explicitly, not
 *       inferred from the unreliable {@code x-death} header count.</li>
 * </ul>
 *
 * <p>T-04-13 / D-04: this consumer MUST NOT call wallet-service synchronously.
 * Credit effects are communicated only via AMQP events written to the outbox.
 *
 * <p>Queue is declared programmatically in {@link com.smsreseller.messaging.config.RabbitMqConfig}
 * (quorum queue with deliveryLimit(3)). No {@code @QueueBinding} here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendMessageConsumer {

    /** Max total attempts before a TRANSIENT_FAIL is considered permanent (D-06). */
    private static final int MAX_ATTEMPTS = 3;

    private final SmsProvider smsProvider;
    private final OutboundMessageRepository outboundMessageRepository;
    private final MessageEventPublisher messageEventPublisher;
    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = SEND_QUEUE, ackMode = "MANUAL")
    @Transactional
    public void onSendMessage(
            SendMessagePayload payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {

        log.info("Processing message: messageId={} phone={} lotId={} attempt={}",
                payload.messageId(), payload.phoneE164(), payload.lotId(), payload.attemptCount());

        OutboundMessage message = outboundMessageRepository.findById(payload.messageId())
                .orElse(null);

        if (message == null) {
            log.warn("OutboundMessage not found for messageId={} — acking to discard", payload.messageId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Already SENT or DELIVERED (idempotency guard — duplicate delivery)
        if (message.getStatus() == MessageStatus.SENT || message.getStatus() == MessageStatus.DELIVERED) {
            log.info("Duplicate delivery for messageId={} status={} — acking no-op", payload.messageId(), message.getStatus());
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Already FAILED (e.g. set by a previous HARD_FAIL attempt) — ack to discard
        if (message.getStatus() == MessageStatus.FAILED) {
            log.info("Message already FAILED messageId={} — acking to discard", payload.messageId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        SmsResult result = smsProvider.send(payload.phoneE164(), payload.body(), payload.senderId());

        switch (result.outcome()) {
            case ACCEPTED -> {
                message.setStatus(MessageStatus.SENT);
                message.setExternalId(result.externalId());
                outboundMessageRepository.save(message);
                messageEventPublisher.accepted(payload.messageId(), payload.userId(), payload.lotId());
                channel.basicAck(deliveryTag, false);
                log.info("Message SENT: messageId={} externalId={}", payload.messageId(), result.externalId());
            }
            case TRANSIENT_FAIL -> {
                int nextAttempt = payload.attemptCount() + 1;
                if (nextAttempt >= MAX_ATTEMPTS) {
                    // Retries exhausted — route to dead queue for permanent failure handling
                    log.warn("TRANSIENT_FAIL exhausted retries for messageId={} (attempt {}/{}) — routing to dead",
                            payload.messageId(), payload.attemptCount(), MAX_ATTEMPTS);
                    routeToDead(payload, nextAttempt);
                } else {
                    // Still have retries — advance to next ladder rung
                    String retryQueue = nextRetryQueue(nextAttempt);
                    log.warn("TRANSIENT_FAIL for messageId={} (attempt {}/{}) — republishing to {}",
                            payload.messageId(), nextAttempt, MAX_ATTEMPTS, retryQueue);
                    republishToRetry(payload, nextAttempt, retryQueue);
                }
                channel.basicAck(deliveryTag, false);
            }
            case HARD_FAIL -> {
                // Permanent failure — skip retry ladder, route directly to dead queue
                log.warn("HARD_FAIL for messageId={}: {} — routing directly to dead queue",
                        payload.messageId(), result.reason());
                routeToDead(payload, payload.attemptCount());
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    /**
     * Emit MessageReleased for a reserved-but-never-sent slot.
     *
     * <p>Called by CampaignService or any component that determines a reserved credit slot
     * will never be dispatched (e.g. campaign cancelled, 0 recipients after filter).
     * Emitting RELEASE prevents the reservation from being stranded in the wallet ledger (MESG-09).
     *
     * <p>Note: this method is NOT a @RabbitListener — it is a seam for CampaignService to
     * programmatically emit the release event.
     */
    public void releaseReservation(OutboundMessage message) {
        messageEventPublisher.released(message.getId(), message.getUserId(), message.getLotId());
        log.info("MessageReleased emitted: messageId={} lotId={}", message.getId(), message.getLotId());
    }

    // ── Private routing helpers ───────────────────────────────────────────────

    /** Route a message to the dead queue via the DLX (Approach A). */
    private void routeToDead(SendMessagePayload original, int finalAttempt) {
        SendMessagePayload dead = new SendMessagePayload(
                original.messageId(), original.campaignId(), original.userId(),
                original.phoneE164(), original.body(), original.senderId(),
                original.lotId(), finalAttempt
        );
        amqpTemplate.convertAndSend(DLX, DEAD_QUEUE, dead);
    }

    /** Republish to the appropriate retry queue for the given attempt count. */
    private void republishToRetry(SendMessagePayload original, int nextAttempt, String routingKey) {
        SendMessagePayload retry = new SendMessagePayload(
                original.messageId(), original.campaignId(), original.userId(),
                original.phoneE164(), original.body(), original.senderId(),
                original.lotId(), nextAttempt
        );
        amqpTemplate.convertAndSend(DLX, routingKey, retry);
    }

    /**
     * Map attempt number to the retry queue routing key.
     * Attempt 1 → retry.1m (shortest wait), 2 → retry.5m, 3+ → retry.15m.
     */
    private static String nextRetryQueue(int attempt) {
        return switch (attempt) {
            case 1 -> RETRY_1_QUEUE;
            case 2 -> RETRY_2_QUEUE;
            default -> RETRY_3_QUEUE;
        };
    }
}
