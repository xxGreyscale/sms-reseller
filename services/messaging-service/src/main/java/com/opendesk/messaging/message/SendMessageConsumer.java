package com.opendesk.messaging.message;

import com.opendesk.messaging.sms.SmsProvider;
import com.opendesk.messaging.sms.SmsResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.opendesk.messaging.config.RabbitMqConfig.SEND_QUEUE;

/**
 * Consumes {@link SendMessagePayload} from the {@code messaging.send} quorum queue.
 *
 * <p>On SmsProvider.ACCEPTED → set OutboundMessage SENT + write MessageAccepted outbox entry.
 * On SmsProvider.TRANSIENT_FAIL → nack(requeue=false) so DLX routes to retry ladder (04-06).
 * On SmsProvider.HARD_FAIL → mark FAILED + nack(requeue=false); DeadLetterConsumer (04-06) emits refund.
 *
 * <p>T-04-13 / D-04: this consumer MUST NOT call wallet-service synchronously.
 * Credit effects are communicated only via AMQP events written to the outbox.
 *
 * <p>The {@code @Transactional} annotation ensures that OutboundMessage status update and outbox
 * write happen atomically — either both commit or neither does.
 *
 * <p>Queue is declared programmatically in {@link com.opendesk.messaging.config.RabbitMqConfig}
 * (quorum queue with deliveryLimit(3)). No {@code @QueueBinding} here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendMessageConsumer {

    private final SmsProvider smsProvider;
    private final OutboundMessageRepository outboundMessageRepository;
    private final MessageEventPublisher messageEventPublisher;

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

        // Already SENT or FAILED (idempotency guard — duplicate delivery)
        if (message.getStatus() == MessageStatus.SENT || message.getStatus() == MessageStatus.FAILED) {
            log.info("Duplicate delivery for messageId={} status={} — acking no-op", payload.messageId(), message.getStatus());
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
                // nack(requeue=false) → DLX routes to retry queue (04-06 wires the DLX counter ladder)
                log.warn("TRANSIENT_FAIL for messageId={}: {} — nacking for DLX retry",
                        payload.messageId(), result.reason());
                channel.basicNack(deliveryTag, false, false);
            }
            case HARD_FAIL -> {
                // Mark FAILED — DeadLetterConsumer (04-06) emits MessageRefundDue when this reaches dead queue
                message.setStatus(MessageStatus.FAILED);
                outboundMessageRepository.save(message);
                log.warn("HARD_FAIL for messageId={}: {} — nacking, will dead-letter",
                        payload.messageId(), result.reason());
                channel.basicNack(deliveryTag, false, false);
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
}
