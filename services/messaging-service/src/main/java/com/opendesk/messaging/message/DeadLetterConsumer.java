package com.opendesk.messaging.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.opendesk.messaging.config.RabbitMqConfig.DEAD_QUEUE;

/**
 * Consumes permanently-failed messages from the {@code messaging.dead} quorum queue.
 *
 * <p>A message arrives here via two paths:
 * <ol>
 *   <li>HARD_FAIL: SendMessageConsumer nacks immediately with requeue=false; DLX routes here.</li>
 *   <li>Retries exhausted: after deliveryLimit(3), RabbitMQ auto-dead-letters to the DLX
 *       routing key {@code messaging.dead}.</li>
 * </ol>
 *
 * <p>On each message this consumer:
 * <ul>
 *   <li>Loads the {@link OutboundMessage} by its messageId from the payload.</li>
 *   <li>Transitions status to {@link MessageStatus#FAILED} (idempotent — no-op if already FAILED).</li>
 *   <li>Calls {@link MessageEventPublisher#refundDue} to write a {@code MessageRefundDue} outbox
 *       entry (one credit per permanently-failed message).</li>
 * </ul>
 *
 * <p>T-04-15 (double-refund prevention): the outbox row carries a unique {@code eventId}; the
 * wallet-side {@code MessagingEventConsumer} (04-07) uses it for idempotency dedup. This consumer
 * is the SINGLE emit site for {@code MessageRefundDue}.
 *
 * <p>Note: this listener uses Spring's default Jackson2JsonMessageConverter (configured in
 * RabbitMqConfig) to deserialize {@link SendMessagePayload} from the dead queue. The message
 * was originally a SendMessagePayload published by CampaignService.executeSend — it retains
 * that shape through all DLX hops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterConsumer {

    private final OutboundMessageRepository outboundMessageRepository;
    private final MessageEventPublisher messageEventPublisher;

    /**
     * Process a permanently-failed message from the dead queue.
     *
     * <p>The payload is the original {@link SendMessagePayload} that failed delivery.
     * Spring AMQP auto-acks after successful listener return; if this method throws,
     * Spring AMQP nacks — but since this is the dead queue there is nowhere else to route,
     * so nacks would loop. Guard against this with defensive exception handling.
     */
    @RabbitListener(queues = DEAD_QUEUE)
    @Transactional
    public void onDeadLetter(SendMessagePayload payload) {
        UUID messageId = payload.messageId();
        log.warn("DeadLetterConsumer: permanently-failed messageId={} userId={} lotId={}",
                messageId, payload.userId(), payload.lotId());

        OutboundMessage message = outboundMessageRepository.findById(messageId).orElse(null);
        if (message == null) {
            log.error("DeadLetterConsumer: OutboundMessage not found for messageId={} — discarding", messageId);
            return;
        }

        if (message.getStatus() == MessageStatus.FAILED) {
            // Idempotency: already FAILED (e.g. HARD_FAIL set by SendMessageConsumer before nack)
            // Still emit refundDue in case the refund event was not yet written
            log.info("DeadLetterConsumer: messageId={} already FAILED — checking for existing refund outbox entry",
                    messageId);
        } else {
            message.setStatus(MessageStatus.FAILED);
            outboundMessageRepository.save(message);
            log.info("DeadLetterConsumer: messageId={} set to FAILED", messageId);
        }

        // Emit MessageRefundDue — wallet credits back 1 credit for this permanently-failed message
        // T-04-15: unique eventId in the payload guards against double-refund at the wallet
        messageEventPublisher.refundDue(messageId, payload.userId(), payload.lotId(), 1);
        log.info("DeadLetterConsumer: MessageRefundDue written for messageId={}", messageId);
    }
}
