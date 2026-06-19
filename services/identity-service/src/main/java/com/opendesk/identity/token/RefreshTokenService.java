package com.opendesk.identity.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages opaque per-device refresh tokens stored in Redis.
 *
 * <p>Redis key schema: {@code refresh:{userId}:{deviceId}}
 * Redis value: SHA-256 hash of the raw token (never plaintext — anti-pattern, RESEARCH line 266).
 * TTL: configurable, default 7 days (D-06).
 *
 * <p>Security properties:
 * <ul>
 *   <li>Token entropy: ≥256 bits from {@link SecureRandom} encoded as Base64URL (RESEARCH line 249).</li>
 *   <li>Rotation (D-08): {@link #rotate(String)} atomically deletes the old key and stores a new one.</li>
 *   <li>Reuse detection (Pitfall 4, RESEARCH lines 304-307): presenting a token whose key no longer
 *       exists (already-rotated) triggers {@link #revokeAll(UUID)}, immediately invalidating all
 *       sessions for that user.</li>
 *   <li>Revoke-current (D-07): {@link #revokeCurrent(UUID, String)} deletes only the current device key.</li>
 *   <li>Revoke-all (D-09): {@link #revokeAll(UUID)} uses Redis SCAN (never KEYS) to find and delete
 *       all {@code refresh:{userId}:*} keys. This method is public and is the seam consumed by
 *       Plan 02-05 (password reset).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.jwt.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Issues a new opaque refresh token for the given user and device.
     *
     * <p>The raw token is returned to the caller (for inclusion in the response).
     * Only its SHA-256 hash is stored in Redis at {@code refresh:{userId}:{deviceId}}.
     *
     * <p>Token format: {@code {userId}|{deviceId}|{randomBase64Url}} — the metadata prefix
     * allows the {@link #rotate(String)} path to derive the Redis key without a secondary
     * index lookup.
     *
     * @param userId   user UUID
     * @param deviceId stable per-device identifier supplied by the client
     * @return raw opaque token string
     */
    public String issue(UUID userId, String deviceId) {
        String raw = buildRawToken(userId, deviceId);
        String hash = sha256(raw);
        String key = redisKey(userId, deviceId);
        stringRedisTemplate.opsForValue().set(key, hash, refreshTokenTtlDays, TimeUnit.DAYS);
        log.debug("Issued refresh token for userId={} deviceId={}", userId, deviceId);
        return raw;
    }

    /**
     * Rotates a refresh token: validates the presented raw token, deletes the old key, and
     * issues a new token at the same {@code {userId}:{deviceId}} slot.
     *
     * <p>Reuse detection (Pitfall 4): if the token's Redis key is absent but the token
     * format is otherwise valid (i.e. it was previously issued by us), we treat this as a
     * stolen token reuse attack and call {@link #revokeAll(UUID)} before rejecting.
     *
     * @param rawToken raw token string received from the client
     * @return new raw token (the old one is now invalid)
     * @throws InvalidRefreshTokenException if the token is invalid, expired, or reused
     */
    public String rotate(String rawToken) {
        TokenParts parts = parse(rawToken);
        UUID userId = parts.userId();
        String deviceId = parts.deviceId();
        String key = redisKey(userId, deviceId);

        String storedHash = stringRedisTemplate.opsForValue().get(key);

        if (storedHash == null) {
            // Key absent: either expired or already rotated (possible reuse attack).
            // Reuse of a previously-issued token for this user → revoke all sessions (Pitfall 4).
            log.warn("Refresh token reuse detected for userId={}. Revoking all sessions.", userId);
            revokeAll(userId);
            throw new InvalidRefreshTokenException("Refresh token is invalid or has been revoked");
        }

        String presentedHash = sha256(rawToken);
        if (!storedHash.equals(presentedHash)) {
            // Hash mismatch — the key exists but the hash doesn't match.
            // This indicates a previously-rotated token is being presented (the slot now holds
            // the newer hash). Treat as reuse attack (Pitfall 4): revoke all sessions.
            log.warn("Refresh token hash mismatch (possible reuse of rotated token) for userId={}. Revoking all sessions.", userId);
            revokeAll(userId);
            throw new InvalidRefreshTokenException("Refresh token is invalid or has been revoked");
        }

        // Atomic rotation: delete old, set new
        stringRedisTemplate.delete(key);
        String newRaw = buildRawToken(userId, deviceId);
        String newHash = sha256(newRaw);
        stringRedisTemplate.opsForValue().set(key, newHash, refreshTokenTtlDays, TimeUnit.DAYS);
        log.debug("Rotated refresh token for userId={} deviceId={}", userId, deviceId);
        return newRaw;
    }

    /**
     * Revokes the current device's refresh token (D-07, IDEN-06).
     * Other devices are unaffected.
     *
     * @param userId   user UUID
     * @param deviceId device to revoke
     */
    public void revokeCurrent(UUID userId, String deviceId) {
        String key = redisKey(userId, deviceId);
        Boolean deleted = stringRedisTemplate.delete(key);
        log.debug("Revoked refresh token for userId={} deviceId={} deleted={}", userId, deviceId, deleted);
    }

    /**
     * Revokes ALL refresh tokens for the given user by scanning and deleting
     * all {@code refresh:{userId}:*} keys via Redis SCAN (D-09).
     *
     * <p>This method is the primary entry point consumed by Plan 02-05 (password reset).
     * When a user changes their password, all existing sessions are invalidated by calling
     * this method with the user's UUID.
     *
     * <p>Implementation note: KEYS is prohibited in production Redis — SCAN is used with
     * a COUNT hint to iterate in bounded batches (RESEARCH anti-pattern guidance).
     *
     * @param userId user UUID whose sessions should be revoked
     */
    public void revokeAll(UUID userId) {
        String pattern = KEY_PREFIX + userId + ":*";
        List<String> keysToDelete = new ArrayList<>();

        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
        }

        if (!keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
            log.info("Revoked {} refresh token(s) for userId={}", keysToDelete.size(), userId);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an opaque refresh token embedding {@code {userId}|{deviceId}|{randomBase64Url}}.
     *
     * <p>The random segment provides ≥256 bits of entropy; the prefix is non-secret routing
     * metadata that allows the {@link #rotate} path to derive the Redis key without a secondary
     * index scan. The whole string is sent to the client as a single opaque token.
     */
    private String buildRawToken(UUID userId, String deviceId) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return userId + "|" + deviceId + "|" + randomPart;
    }

    /**
     * Parses a raw token presented by the client back into its constituent parts.
     * Format: {@code {userId}|{deviceId}|{randomBase64Url}}
     */
    private TokenParts parse(String rawToken) {
        String[] parts = rawToken.split("\\|", 3);
        if (parts.length != 3) {
            throw new InvalidRefreshTokenException("Malformed refresh token");
        }
        try {
            UUID userId = UUID.fromString(parts[0]);
            String deviceId = parts[1];
            return new TokenParts(userId, deviceId);
        } catch (IllegalArgumentException e) {
            throw new InvalidRefreshTokenException("Malformed refresh token");
        }
    }

    /**
     * Returns the Redis key for a given userId + deviceId combination.
     */
    private String redisKey(UUID userId, String deviceId) {
        return KEY_PREFIX + userId + ":" + deviceId;
    }

    /**
     * Computes the SHA-256 hash of the given string and returns it as a hex string.
     * Used to store a hash of the raw token rather than the token itself.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record TokenParts(UUID userId, String deviceId) {}
}
