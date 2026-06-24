package com.smsreseller.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /auth/forgot.
 *
 * <p>Validation: email must be non-blank and a syntactically valid email address.
 * The controller returns the same 200 response regardless of whether the email
 * exists in the system (no enumeration — T-02-ENUM).
 */
public record ForgotPasswordRequest(

        @Email(message = "must be a valid email address")
        @NotBlank(message = "email is required")
        String email
) {}
