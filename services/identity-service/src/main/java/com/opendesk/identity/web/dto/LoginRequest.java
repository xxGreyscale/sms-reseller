package com.opendesk.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO (IDEN-04).
 *
 * <p>{@code deviceId} is a stable per-device identifier supplied by the client.
 * The refresh token issued on login is scoped to this deviceId (D-07, D-08).
 * Clients MUST persist this identifier across app restarts.
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotBlank String deviceId
) {}
