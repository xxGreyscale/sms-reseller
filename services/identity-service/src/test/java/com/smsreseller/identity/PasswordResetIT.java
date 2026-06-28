package com.smsreseller.identity;

import com.smsreseller.identity.password.StubEmailSender;
import com.smsreseller.identity.token.RefreshTokenService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: IDEN-07 — Reset password via email link; revokes all sessions on success (D-09, D-11, D-13).
 *
 * <p>Uses StubEmailSender (@Profile("stub") — active in AbstractIntegrationTest) to capture
 * the reset URL without a real SMTP server.
 */
class PasswordResetIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StubEmailSender stubEmailSender;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private static final String EMAIL = "reset-test@example.com";
    private static final String PASSWORD = "OldPass1!";
    private static final String NEW_PASSWORD = "NewPass99!";

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email(EMAIL)
                .phone("+255700111222")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(VerificationStatus.VERIFIED)
                .build();
        userRepository.save(user);
    }

    /**
     * /auth/forgot ALWAYS returns 200 regardless of whether the email exists (no enumeration).
     */
    @Test
    void forgotPassword_doesNotRevealEmailExistence() {
        // Known email
        ResponseEntity<String> known = restTemplate.postForEntity(
                "/api/v1/auth/forgot", Map.of("email", EMAIL), String.class);
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Unknown email — same response shape
        ResponseEntity<String> unknown = restTemplate.postForEntity(
                "/api/v1/auth/forgot", Map.of("email", "nobody@example.com"), String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Full happy-path: forgot → read stub link → reset → login with new password succeeds.
     */
    @Test
    void resetsPasswordAndRevokesAllSessions() throws Exception {
        // Step 1: trigger forgot
        restTemplate.postForEntity("/api/v1/auth/forgot", Map.of("email", EMAIL), String.class);

        // Step 2: retrieve token from stub email sender
        String resetUrl = stubEmailSender.getLastResetUrl();
        assertThat(resetUrl).isNotNull();
        String token = extractToken(resetUrl);
        assertThat(token).isNotBlank();

        // Step 3: reset password with valid token
        ResponseEntity<String> reset = restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", token, "newPassword", NEW_PASSWORD),
                String.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 4: old password rejected
        ResponseEntity<String> oldLogin = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", EMAIL, "password", PASSWORD, "deviceId", "dev-old"),
                String.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Step 5: new password accepted
        ResponseEntity<String> newLogin = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", EMAIL, "password", NEW_PASSWORD, "deviceId", "dev-new"),
                String.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Token is single-use — reusing the same token returns 400.
     */
    @Test
    void resetToken_isSingleUse() {
        // Trigger forgot
        restTemplate.postForEntity("/api/v1/auth/forgot", Map.of("email", EMAIL), String.class);

        String resetUrl = stubEmailSender.getLastResetUrl();
        assertThat(resetUrl).isNotNull();
        String token = extractToken(resetUrl);

        // First use: success
        ResponseEntity<String> first = restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", token, "newPassword", NEW_PASSWORD),
                String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second use: rejected (single-use, already deleted from Redis)
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", token, "newPassword", "AnotherPass1!"),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * An invalid/expired/bogus token is rejected with 400.
     */
    @Test
    void resetWithInvalidToken_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", "bogus-nonexistent-token", "newPassword", NEW_PASSWORD),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * After a successful reset, existing refresh tokens for the user are revoked (D-09).
     * Verified by issuing a refresh token via RefreshTokenService directly, then confirming
     * it cannot be rotated after the password reset.
     */
    @Test
    void successfulReset_revokesAllRefreshTokens() {
        // Issue a refresh token for the user before the reset
        String existingRefreshToken = refreshTokenService.issue(userId, "device-pre-reset");

        // Trigger reset
        restTemplate.postForEntity("/api/v1/auth/forgot", Map.of("email", EMAIL), String.class);
        String resetUrl = stubEmailSender.getLastResetUrl();
        String token = extractToken(resetUrl);

        restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", token, "newPassword", NEW_PASSWORD),
                String.class);

        // Attempt to rotate the pre-reset refresh token — must fail (revokeAll was called D-09)
        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", existingRefreshToken),
                String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Password shorter than 8 characters is rejected with 400.
     */
    @Test
    void resetWithShortPassword_returns400() {
        restTemplate.postForEntity("/api/v1/auth/forgot", Map.of("email", EMAIL), String.class);
        String resetUrl = stubEmailSender.getLastResetUrl();
        String token = extractToken(resetUrl);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/reset",
                Map.of("token", token, "newPassword", "short"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Extracts the {@code token} query parameter from the reset URL. */
    private String extractToken(String url) {
        Matcher m = Pattern.compile("[?&]token=([^&]+)").matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        // Fallback: treat the whole URL as the token (if the impl uses path param)
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }
}
