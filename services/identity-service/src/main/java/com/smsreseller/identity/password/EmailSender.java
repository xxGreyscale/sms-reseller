package com.smsreseller.identity.password;

/**
 * Mock-first email abstraction (D-13).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link StubEmailSender} — {@code @Profile("stub")} — records the reset URL so tests
 *       and local development can read it without a real SMTP server.</li>
 *   <li>{@link RealEmailSender} — {@code @Profile("prod")} — sends via JavaMailSender (SMTP).
 *       The reset URL is NEVER logged in production (T-02-LOG, V7).</li>
 * </ul>
 */
public interface EmailSender {

    /**
     * Delivers a password-reset link to the given recipient.
     *
     * <p>Implementations MUST NOT log the {@code resetUrl} in production environments — the
     * URL contains a high-entropy single-use token that is equivalent to a temporary password.
     *
     * @param toEmail  recipient email address
     * @param resetUrl full reset URL containing the single-use token
     */
    void sendPasswordResetLink(String toEmail, String resetUrl);
}
