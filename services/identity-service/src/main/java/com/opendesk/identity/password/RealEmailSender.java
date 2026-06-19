package com.opendesk.identity.password;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Production {@link EmailSender} implementation that delivers the reset link via SMTP (D-13, Open Q3).
 *
 * <p>Active only under the "prod" Spring profile. The reset URL is NEVER logged — it contains
 * a single-use high-entropy token equivalent to a temporary password (T-02-LOG, V7).
 *
 * <p>Requires {@code spring.mail.*} properties to be configured (host, port, credentials).
 */
@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class RealEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        // NOTE: resetUrl is intentionally NOT logged here (T-02-LOG / V7).
        log.info("Sending password reset email to {}", toEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Reset your Open Desk password");
        message.setText(
                "You requested a password reset.\n\n"
                + "Click the link below to set a new password (valid for 30 minutes):\n\n"
                + resetUrl
                + "\n\nIf you did not request this, you can safely ignore this email."
        );

        javaMailSender.send(message);
        log.info("Password reset email sent to {}", toEmail);
    }
}
