package com.smsreseller.wallet.consumer;

import java.util.UUID;

/**
 * Local event record: messaging-service signals that one message was accepted by the
 * upstream SMS provider (send confirmed).
 *
 * <p>Service-boundary local copy — no import from {@code com.smsreseller.messaging}.
 * Mirrors the payload published by messaging-service on {@code messaging.events} with
 * routing key {@code messaging.MessageAccepted}.
 *
 * @param eventId   unique event identifier (used for processed_events idempotency guard)
 * @param messageId the OutboundMessage that was accepted
 * @param userId    owner of the credit lot to consume from
 * @param lotId     specific credit lot from which the credit was reserved (D-13)
 */
public record MessageAccepted(String eventId, UUID messageId, UUID userId, UUID lotId) {
}
