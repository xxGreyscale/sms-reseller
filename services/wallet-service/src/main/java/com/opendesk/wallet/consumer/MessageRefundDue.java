package com.opendesk.wallet.consumer;

import java.util.UUID;

/**
 * Local event record: messaging-service signals that a send failed permanently and
 * the credit must be refunded to the user's wallet.
 *
 * <p>Service-boundary local copy — no import from {@code com.opendesk.messaging}.
 * Mirrors the payload published by messaging-service on {@code messaging.events} with
 * routing key {@code messaging.MessageRefundDue}.
 *
 * @param eventId         unique event identifier (used for processed_events idempotency guard)
 * @param messageId       the OutboundMessage that failed and triggered the refund
 * @param userId          owner whose wallet receives the refund credit
 * @param lotId           originating lot (carried for audit; refund creates a new REFUND lot)
 * @param creditsToRefund number of credits to credit back (usually 1 per failed message)
 */
public record MessageRefundDue(String eventId, UUID messageId, UUID userId, UUID lotId, int creditsToRefund) {
}
