package com.opendesk.identity.token;

import java.time.Instant;
import java.util.UUID;

/**
 * Value object carrying the result of a refresh token issuance or rotation.
 *
 * <p>The {@link #rawToken} is the opaque, high-entropy token sent to the client
 * (in the response body — never in a cookie or URL). It is NEVER stored in Redis;
 * only its SHA-256 hash is persisted (anti-pattern avoidance, RESEARCH lines 263-267).
 *
 * <p>The {@link #deviceId} and {@link #userId} pair identifies the Redis key:
 * {@code refresh:{userId}:{deviceId}}.
 */
public record RefreshToken(
        String rawToken,
        UUID userId,
        String deviceId,
        Instant expiresAt
) {}
