package com.smsreseller.messaging.message;

import java.util.UUID;

/**
 * AMQP message payload published to the {@code messaging.send} quorum queue.
 *
 * <p>One payload per recipient. Consumed by {@link SendMessageConsumer}.
 * The {@link #lotId} carries the credit lot correlation (D-13) so the consumer's
 * AMQP events (MessageAccepted / MessageReleased / MessageRefundDue) reference
 * the correct lot — enabling wallet-service to CONSUME/RELEASE/REFUND without a
 * secondary lookup.
 */
public record SendMessagePayload(
        UUID messageId,
        UUID campaignId,
        UUID userId,
        String phoneE164,
        String body,
        String senderId,
        UUID lotId,
        int attemptCount
) {}
