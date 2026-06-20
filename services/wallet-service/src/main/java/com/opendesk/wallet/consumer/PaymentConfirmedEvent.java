package com.opendesk.wallet.consumer;

import java.util.UUID;

/**
 * Local mirror of the PaymentConfirmed event published by payment-service.
 *
 * <p>This record is a local copy — NO import from {@code com.opendesk.payment}.
 * Service boundary is respected: wallet-service mirrors the contract defined in 03-05-SUMMARY.md.
 *
 * <p>Contract (payment.events exchange, routing key payment.PaymentConfirmed):
 * {@code { "eventId": "<UUID>", "userId": "<UUID>", "paymentId": "<UUID>", "smsCount": 200 }}
 *
 * @param eventId   idempotency key for processed_events guard (T-03-16)
 * @param userId    user to credit with a PURCHASED lot
 * @param paymentId FK to the completed payment record (stored on CreditLot)
 * @param smsCount  SMS credits to grant as a PURCHASED lot (12-month expiry, D-03)
 */
public record PaymentConfirmedEvent(String eventId, UUID userId, UUID paymentId, int smsCount) {
}
