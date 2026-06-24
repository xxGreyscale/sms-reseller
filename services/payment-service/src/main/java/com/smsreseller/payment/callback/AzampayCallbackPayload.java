package com.smsreseller.payment.callback;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Azampay webhook callback payload.
 *
 * <p>Azampay POSTs this JSON when an STK push transaction resolves.
 * Field names match the Azampay Go SDK / Python SDK (03-RESEARCH.md Azampay API Shape).
 *
 * <p>Key fields:
 * <ul>
 *   <li>{@code utilityRef} — our payment UUID (the externalId we sent to Azampay); idempotency key</li>
 *   <li>{@code transactionStatus} — "success" or "fail" (case-insensitive)</li>
 *   <li>{@code reference} — Azampay internal reference (stored as operatorReference on success)</li>
 * </ul>
 */
public record AzampayCallbackPayload(
        String msisdn,
        String amount,
        String message,
        String utilityRef,
        String operator,
        String reference,
        String transactionStatus,
        String submerchantAcc
) {}
