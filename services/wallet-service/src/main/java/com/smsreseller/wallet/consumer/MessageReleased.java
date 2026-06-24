package com.smsreseller.wallet.consumer;

import java.util.UUID;

/**
 * Local event record: messaging-service signals that one message was released
 * (campaign cancelled or recipient skipped before send).
 *
 * <p>Service-boundary local copy — no import from {@code com.smsreseller.messaging}.
 * Mirrors the payload published by messaging-service on {@code messaging.events} with
 * routing key {@code messaging.MessageReleased}.
 *
 * @param eventId   unique event identifier (used for processed_events idempotency guard)
 * @param messageId the OutboundMessage whose reservation was released
 * @param userId    owner of the credit lot to release from
 * @param lotId     specific credit lot from which the credit was reserved (D-13)
 */
public record MessageReleased(String eventId, UUID messageId, UUID userId, UUID lotId) {
}
