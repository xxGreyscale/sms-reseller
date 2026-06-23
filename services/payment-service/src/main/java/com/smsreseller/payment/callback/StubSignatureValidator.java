package com.smsreseller.payment.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Stub signature validator for dev/test environments.
 *
 * <p>Always returns {@code true} — real HMAC validation is deferred to merchant onboarding
 * when Azampay documents their signature scheme (03-RESEARCH.md Open Question 1).
 *
 * <p>Active under the {@code stub} Spring profile (dev, CI, staging).
 */
@Profile("stub")
@Component
@Slf4j
public class StubSignatureValidator implements WebhookSignatureValidator {

    @Override
    public boolean isValid(AzampayCallbackPayload payload, HttpHeaders headers) {
        log.debug("StubSignatureValidator: always-valid (stub profile) utilityRef={}", payload.utilityRef());
        return true;
    }
}
