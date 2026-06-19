package com.opendesk.identity;

import com.opendesk.identity.token.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers: IDEN-05 — Session persists; refresh tokens rotate on each use (D-08).
 *
 * <p>Tests refresh token issuance, rotation, reuse detection, and revoke-all.
 */
class RefreshRotationIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private UUID userId;
    private String deviceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deviceId = "device-" + UUID.randomUUID();
        // clean up Redis keys for this user before each test
        refreshTokenService.revokeAll(userId);
    }

    @Test
    void rotatesRefreshTokenOnUse() {
        // GIVEN — issue a token
        String rawToken = refreshTokenService.issue(userId, deviceId);

        // WHEN — rotate it (simulate refresh call)
        String newRawToken = refreshTokenService.rotate(rawToken);

        // THEN — old token no longer valid; new token is different
        assertThat(newRawToken).isNotEqualTo(rawToken);
        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void reuseOfRotatedTokenRevokesAllSessions() {
        // GIVEN — two devices for the same user
        String device2 = "device2-" + UUID.randomUUID();
        String token1 = refreshTokenService.issue(userId, deviceId);
        refreshTokenService.issue(userId, device2);

        // rotate once to advance the chain
        String newToken = refreshTokenService.rotate(token1);
        assertThat(newToken).isNotBlank();

        // WHEN — reuse the old (already-rotated) token
        // this should trigger revokeAll and throw
        assertThatThrownBy(() -> refreshTokenService.rotate(token1))
                .isInstanceOf(RuntimeException.class);

        // THEN — the new token also no longer works (all sessions revoked)
        assertThatThrownBy(() -> refreshTokenService.rotate(newToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void revokeAllDeletesAllDeviceSessions() {
        // GIVEN — three devices
        String t1 = refreshTokenService.issue(userId, "dev-a");
        String t2 = refreshTokenService.issue(userId, "dev-b");
        String t3 = refreshTokenService.issue(userId, "dev-c");

        // WHEN
        refreshTokenService.revokeAll(userId);

        // THEN — none of the tokens rotate successfully
        assertThatThrownBy(() -> refreshTokenService.rotate(t1)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(t2)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(t3)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void revokeCurrentLeavesOtherDevicesIntact() {
        // GIVEN — two devices
        String device2 = "device2-" + UUID.randomUUID();
        String token1 = refreshTokenService.issue(userId, deviceId);
        String token2 = refreshTokenService.issue(userId, device2);

        // WHEN — revoke only device1
        refreshTokenService.revokeCurrent(userId, deviceId);

        // THEN — device1 is gone, device2 still works
        assertThatThrownBy(() -> refreshTokenService.rotate(token1)).isInstanceOf(RuntimeException.class);
        String rotated2 = refreshTokenService.rotate(token2);
        assertThat(rotated2).isNotBlank();
    }

    @Test
    void storedValueIsHashedNotRawToken() {
        // GIVEN
        String rawToken = refreshTokenService.issue(userId, deviceId);

        // WHEN — look at the Redis key
        String redisKey = "refresh:" + userId + ":" + deviceId;
        String storedValue = stringRedisTemplate.opsForValue().get(redisKey);

        // THEN — stored value must not equal the raw token (it's a SHA-256 hash)
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).isNotEqualTo(rawToken);
    }
}
