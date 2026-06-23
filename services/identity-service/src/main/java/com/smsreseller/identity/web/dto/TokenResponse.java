package com.smsreseller.identity.web.dto;

/**
 * Response body for successful login and refresh operations (IDEN-04, IDEN-05).
 *
 * <p>The {@code accessToken} is a 15-minute RS256 JWT carrying {@code verification_status} (D-02).
 * The {@code refreshToken} is an opaque per-device token stored hashed in Redis (D-08).
 * The {@code status} field echoes the verification_status so the client does not need to
 * decode the JWT just to check verification state.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String status
) {}
