package com.opendesk.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /auth/register.
 *
 * <p>Security notes:
 * <ul>
 *   <li>NIN (National ID number) is PII — MUST NOT be logged anywhere in the call chain.
 *       The NIN is passed to the NIDA verification service only, never stored.</li>
 *   <li>password is hashed immediately in RegistrationService; the plain-text value
 *       MUST NOT appear in any log, DTO, or response object.</li>
 *   <li>UTF-8 names: Tanzania names may use Swahili characters — Spring/Jackson reads
 *       JSON as UTF-8 by default; no extra configuration required.</li>
 * </ul>
 */
public record RegisterRequest(

        @Email(message = "email must be a valid email address")
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "phone is required")
        String phone,

        @NotBlank(message = "nin is required")
        String nin,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {}
