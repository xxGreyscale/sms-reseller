package com.smsreseller.identity.password;

import com.smsreseller.identity.token.RefreshTokenService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Forgotten-password reset service (IDEN-07).
 *
 * <p>Security design:
 * <ul>
 *   <li>High-entropy token generated via {@link SecureRandom} (≥192 bits, Base64URL encoded).</li>
 *   <li>Token stored in Redis at {@code reset:{token}} → userId string, with a short TTL (D-11).</li>
 *   <li>Token is deleted atomically BEFORE the new password is applied — prevents race-condition
 *       reuse: even if two requests arrive concurrently, only the first GET+DEL succeeds (T-02-07).</li>
 *   <li>Successful reset calls {@link RefreshTokenService#revokeAll(UUID)} to invalidate all
 *       existing sessions (D-09, T-02-SESS).</li>
 *   <li>{@link #forgot(String)} always returns normally regardless of whether the email exists
 *       (no enumeration — T-02-ENUM).</li>
 *   <li>The reset URL is NEVER logged in production (T-02-LOG / V7). Logging is done by
 *       {@link StubEmailSender} at DEBUG level under the "stub" profile only.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String KEY_PREFIX = "reset:";
    private static final int TOKEN_BYTES = 24; // 192 bits of entropy → 32-char Base64URL string

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.password-reset.ttl-minutes:30}")
    private long resetTtlMinutes;

    @Value("${app.password-reset.base-url:http://localhost:3000}")
    private String resetBaseUrl;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initiates the forgotten-password flow for the given email.
     *
     * <p>If the email is registered: generates a single-use reset token, stores it in Redis
     * with a short TTL, and delivers the reset link via {@link EmailSender}.
     *
     * <p>If the email is NOT registered: does nothing silently — same return path (no enumeration).
     *
     * @param email email address submitted by the user
     */
    public void forgot(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Silently ignore: do not reveal whether the email is registered (T-02-ENUM).
            log.debug("forgot-password: email not found — ignoring silently");
            return;
        }

        User user = userOpt.get();
        String token = generateToken();
        String key = KEY_PREFIX + token;

        // SETEX reset:{token} = userId, TTL = resetTtlMinutes
        stringRedisTemplate.opsForValue().set(
                key,
                user.getId().toString(),
                resetTtlMinutes,
                TimeUnit.MINUTES);

        String resetUrl = resetBaseUrl + "/reset-password?token=" + token;
        // NOTE: resetUrl NOT logged here — only StubEmailSender logs it at DEBUG in dev (T-02-LOG/V7).
        emailSender.sendPasswordResetLink(email, resetUrl);
    }

    /**
     * Completes the password reset using the given token.
     *
     * <ol>
     *   <li>Looks up {@code reset:{token}} in Redis.</li>
     *   <li>If absent: token is invalid, expired, or already used → throws 400.</li>
     *   <li>Deletes the token key FIRST (single-use guarantee, prevents concurrent reuse).</li>
     *   <li>Hashes the new password with BCrypt and persists it on the user entity.</li>
     *   <li>Revokes all of the user's refresh tokens (D-09).</li>
     * </ol>
     *
     * @param token       single-use reset token from the email link
     * @param newPassword plain-text new password (min 8 chars, D-12)
     * @throws ResponseStatusException 400 if the token is invalid, expired, or already used
     */
    public void reset(String token, String newPassword) {
        String key = KEY_PREFIX + token;
        String userIdStr = stringRedisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            // Token absent: invalid, expired, or already consumed (single-use).
            log.debug("reset-password: token not found in Redis — invalid or already used");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reset token is invalid or has expired");
        }

        // Delete the key BEFORE applying the new password — atomically consumes the token.
        // Even if two concurrent requests both passed the GET check, only one can proceed
        // after DEL: the second would find a null value on its own GET (T-02-07).
        Boolean deleted = stringRedisTemplate.delete(key);
        if (Boolean.FALSE.equals(deleted)) {
            // Extremely rare race: key was deleted by a concurrent request between our GET and DEL.
            log.debug("reset-password: token was concurrently consumed — rejecting");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reset token is invalid or has expired");
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("reset-password: Redis value is not a valid UUID: {}", userIdStr);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("reset-password: user not found for userId={} despite valid token", userId);
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unexpected error");
                });

        // Hash and persist new password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke ALL existing sessions for this user (D-09, T-02-SESS)
        refreshTokenService.revokeAll(userId);
        log.info("Password reset successful for userId={}; all sessions revoked", userId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a high-entropy URL-safe token using {@link SecureRandom}.
     * Produces ≥192 bits of entropy encoded as a Base64URL string without padding.
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
