package com.smsreseller.payment.gateway;

import java.util.UUID;

/**
 * Request record for initiating an Azampay STK push (Plan 05).
 *
 * @param paymentId  our payment UUID (used as Azampay externalId for idempotency)
 * @param msisdn     customer phone number in E.164 format (e.g. "255712345678")
 * @param amountTzs  amount in raw TZS whole shillings (D-11); must be > 0
 * @param provider   mobile money provider code (e.g. "MPESA", "TIGOPESA", "AIRTELMONEY")
 */
public record StkPushRequest(
        UUID paymentId,
        String msisdn,
        long amountTzs,
        String provider
) {}
