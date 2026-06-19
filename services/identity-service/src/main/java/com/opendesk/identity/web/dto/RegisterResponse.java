package com.opendesk.identity.web.dto;

import java.util.UUID;

/**
 * Response DTO for POST /auth/register.
 *
 * <p>Contains only the information safe to expose to the client:
 * <ul>
 *   <li>userId — the new user's UUID (client uses this for subsequent requests)</li>
 *   <li>status — always PENDING_VERIFICATION at registration time (D-01)</li>
 *   <li>accessToken — a PENDING access JWT so the client is "logged in but walled"</li>
 * </ul>
 *
 * <p>Notably absent: passwordHash, NIN, or any other PII.
 */
public record RegisterResponse(
        UUID userId,
        String status,
        String accessToken
) {}
