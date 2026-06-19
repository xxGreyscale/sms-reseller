package com.opendesk.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /auth/reset.
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code token} — non-blank, high-entropy single-use value from the reset email link</li>
 *   <li>{@code newPassword} — minimum 8 characters (D-12)</li>
 * </ul>
 */
public record ResetPasswordRequest(

        @NotBlank(message = "token is required")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must be at least 8 characters")
        String newPassword
) {}
