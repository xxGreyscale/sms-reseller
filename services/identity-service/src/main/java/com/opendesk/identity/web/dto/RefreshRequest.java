package com.opendesk.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the token refresh endpoint (IDEN-05, D-08).
 *
 * <p>The {@code refreshToken} is the opaque per-device token issued at login or rotation.
 * It embeds the userId and deviceId in its structure so the service can derive the Redis key
 * without a secondary lookup.
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {}
