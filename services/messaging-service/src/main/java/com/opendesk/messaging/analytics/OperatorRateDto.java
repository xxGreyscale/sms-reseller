package com.opendesk.messaging.analytics;

/**
 * ANLX-03: Operator-level delivery rate row — one row per (operator, status) pair.
 *
 * @param operator TZ MNO label ("Vodacom", "Tigo", "Airtel", "Halotel", "UNKNOWN", or null)
 * @param status   message status string ("DELIVERED", "FAILED", "SENT", "PENDING")
 * @param count    number of messages in this (operator, status) bucket
 */
public record OperatorRateDto(
        String operator,
        String status,
        long count
) {}
