package com.smsreseller.payment.infrastructure.callback;

import com.smsreseller.payment.application.port.AzampayCallbackPayload;
import com.smsreseller.payment.application.port.WebhookSignatureValidator;
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
 * <p>Active under the {@code stub} Spring profile (dev, CI, staging) and the {@code sandbox}
 * profile. Sandbox runs the real Azampay gateway but no HMAC scheme is available yet, so this
 * permissive validator stands in. This is safe even against a forged/duplicate callback because
 * {@code CallbackController} additionally rejects any {@code utilityRef} not present in the
 * payments table, and wallet crediting is idempotent (INSERT … ON CONFLICT DO NOTHING). The
 * {@code prod} profile deliberately has NO WebhookSignatureValidator bean until a real
 * HmacSignatureValidator(@Profile("prod")) is written.
 */
@Profile({"stub", "sandbox"})
@Component
@Slf4j
public class StubSignatureValidator implements WebhookSignatureValidator {

    @Override
    public boolean isValid(AzampayCallbackPayload payload, HttpHeaders headers) {
        log.debug("StubSignatureValidator: always-valid (stub/sandbox profile) utilityRef={}", payload.utilityRef());
        return true;
    }
}
