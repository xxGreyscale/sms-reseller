package com.opendesk.identity.password;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stub {@link EmailSender} for the "stub" profile (D-13).
 *
 * <p>Records the last-sent reset URL in an injectable in-memory holder so integration
 * tests can retrieve the link without spinning up a real SMTP server.
 *
 * <p>Logs the reset URL at DEBUG level — acceptable in dev/test, where the stub profile
 * is active. The real implementation ({@link RealEmailSender}) NEVER logs the URL (V7).
 */
@Slf4j
@Service
@Profile("stub")
public class StubEmailSender implements EmailSender {

    private final AtomicReference<String> lastResetUrl = new AtomicReference<>();

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        lastResetUrl.set(resetUrl);
        log.debug("[STUB] Password reset link for {}: {}", toEmail, resetUrl);
    }

    /**
     * Returns the most recently sent reset URL, or {@code null} if no link has been sent yet.
     *
     * <p>Called by integration tests to extract the token from the URL without a real email.
     *
     * @return last reset URL recorded, or null
     */
    public String getLastResetUrl() {
        return lastResetUrl.get();
    }
}
