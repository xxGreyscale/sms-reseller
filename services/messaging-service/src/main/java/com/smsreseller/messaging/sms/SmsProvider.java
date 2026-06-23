package com.smsreseller.messaging.sms;

/**
 * Mock-first SMS provider interface (D-12).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link StubSmsProvider} — {@code @Profile("stub")} for dev/test. Configurable outcomes
 *       by phone number magic suffix. Simulates DLR callbacks after a configurable delay.</li>
 *   <li>RealSmsProvider — {@code @Profile("prod")} — RestClient + Resilience4j CB (04-06/08).</li>
 * </ul>
 *
 * <p>Analog: {@code PaymentGateway} in payment-service (04-PATTERNS.md).
 */
public interface SmsProvider {

    /**
     * Send a single SMS message.
     *
     * @param phoneE164 recipient phone number in E.164 format (e.g. +255712345678)
     * @param body      message text (character count validated by SmsEncoder before call)
     * @param senderId  alphanumeric sender ID (max 11 chars)
     * @return result encoding ACCEPTED, HARD_FAIL, or TRANSIENT_FAIL + optional provider ref
     */
    SmsResult send(String phoneE164, String body, String senderId);
}
