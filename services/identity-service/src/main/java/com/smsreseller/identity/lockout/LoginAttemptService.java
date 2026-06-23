package com.smsreseller.identity.lockout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed login lockout service (D-12, T-02-04).
 *
 * <p>Key schema: {@code lockout:{email}} — stores the failed attempt counter as a string.
 * On the first failure, {@link #increment(String)} sets a TTL equal to the cooldown window so
 * the key auto-expires and the counter resets without a scheduled job.
 *
 * <p>Threshold: {@value #DEFAULT_MAX_ATTEMPTS} consecutive failures trigger a lockout
 * (configurable via {@code app.lockout.max-attempts}).
 *
 * <p>Security note: lockout check MUST happen before the authentication attempt (LoginService)
 * so an attacker cannot probe account existence via the lockout state alone. The generic 401
 * message is returned for both wrong-password and unknown-email (T-02-ENUM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String KEY_PREFIX = "lockout:";
    static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.lockout.cooldown-minutes:15}")
    private long cooldownMinutes;

    /**
     * Increments the failed-attempt counter for the given email.
     *
     * <p>On the first failure (count transitions from null → 1), sets the TTL to the cooldown
     * window. Subsequent increments preserve the original TTL (auto-expiry handles the reset).
     *
     * @param email login identifier that failed
     */
    public void increment(String email) {
        String key = KEY_PREFIX + email;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First failure: set the cooldown TTL so the key auto-expires
            stringRedisTemplate.expire(key, cooldownMinutes, TimeUnit.MINUTES);
        }
        log.debug("Login attempt incremented for email={} count={}", email, count);
    }

    /**
     * Returns {@code true} if the given email has reached or exceeded the lockout threshold.
     *
     * @param email login identifier to check
     */
    public boolean isLocked(String email) {
        String key = KEY_PREFIX + email;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= maxAttempts;
        } catch (NumberFormatException e) {
            log.warn("Non-numeric lockout counter for email={}: {}", email, value);
            return false;
        }
    }

    /**
     * Resets the failed-attempt counter for the given email (called on successful login).
     *
     * @param email login identifier that succeeded
     */
    public void reset(String email) {
        stringRedisTemplate.delete(KEY_PREFIX + email);
        log.debug("Login attempt counter reset for email={}", email);
    }
}
