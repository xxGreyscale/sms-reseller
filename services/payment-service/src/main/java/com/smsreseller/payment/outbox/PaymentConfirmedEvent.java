package com.smsreseller.payment.outbox;

import java.util.UUID;

/**
 * Event emitted when a payment is confirmed (PENDING or EXPIRED → SUCCESS).
 *
 * <p>Published to {@code payment.events} exchange with routing key {@code payment.PaymentConfirmed}.
 * Consumed by wallet-service in Plan 06 to grant a PURCHASED credit lot.
 *
 * <p>Contract (stable across plan 05 and 06):
 * <ul>
 *   <li>{@code eventId} — UUID used as idempotency key by the wallet consumer
 *       (processed_events ON CONFLICT DO NOTHING)</li>
 *   <li>{@code userId} — the user who should receive the credits</li>
 *   <li>{@code paymentId} — references the payments row</li>
 *   <li>{@code smsCount} — number of SMS credits to grant as a PURCHASED lot</li>
 * </ul>
 */
public record PaymentConfirmedEvent(
        String eventId,
        UUID userId,
        UUID paymentId,
        int smsCount
) {}
