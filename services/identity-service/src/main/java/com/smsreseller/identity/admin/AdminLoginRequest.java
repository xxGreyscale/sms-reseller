package com.smsreseller.identity.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for POST /api/v1/auth/admin/login (ADMN-01).
 *
 * <p>Validated with @Valid in AdminLoginController.
 */
public record AdminLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
