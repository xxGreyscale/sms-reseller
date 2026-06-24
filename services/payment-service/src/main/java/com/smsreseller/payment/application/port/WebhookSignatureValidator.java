package com.smsreseller.payment.application.port;

import org.springframework.http.HttpHeaders;

/**
 * Validates Azampay webhook callback signatures.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code StubSignatureValidator} — @Profile("stub") always returns true for dev/test (Open Question 1)</li>
 *   <li>HmacSignatureValidator — @Profile("prod") validates HMAC-SHA256 when Azampay provides it;
 *       deferred to merchant onboarding (03-RESEARCH.md Open Question 1)</li>
 * </ul>
 *
 * <p>Defense in depth: even without HMAC, the callback is validated by checking
 * that {@code utilityRef} exists in our payments table (T-03-12).
 */
public interface WebhookSignatureValidator {

    /**
     * Returns true if the webhook payload is from a trusted source.
     *
     * @param payload the Azampay callback payload
     * @param headers HTTP request headers (may contain HMAC signature)
     * @return true if valid; false if suspected forgery
     */
    boolean isValid(AzampayCallbackPayload payload, HttpHeaders headers);
}
