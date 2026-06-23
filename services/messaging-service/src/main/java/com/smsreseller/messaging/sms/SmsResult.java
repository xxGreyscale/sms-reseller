package com.smsreseller.messaging.sms;

/**
 * Result of an {@link SmsProvider#send} call.
 *
 * <p>Mirrors the PaymentGateway result pattern (04-PATTERNS.md):
 * <ul>
 *   <li>ACCEPTED — provider accepted the message; {@link #externalId()} is the provider reference.</li>
 *   <li>HARD_FAIL — permanent failure (invalid number, blocked, etc.); do not retry.</li>
 *   <li>TRANSIENT_FAIL — temporary failure (provider overload, timeout); eligible for DLX retry.</li>
 * </ul>
 */
public record SmsResult(Outcome outcome, String externalId, String reason) {

    public enum Outcome {
        ACCEPTED,
        HARD_FAIL,
        TRANSIENT_FAIL
    }

    // ── Static factories ───────────────────────────────────────────────────

    public static SmsResult accepted(String externalId) {
        return new SmsResult(Outcome.ACCEPTED, externalId, null);
    }

    public static SmsResult hardFail(String reason) {
        return new SmsResult(Outcome.HARD_FAIL, null, reason);
    }

    public static SmsResult transientFail(String reason) {
        return new SmsResult(Outcome.TRANSIENT_FAIL, null, reason);
    }
}
