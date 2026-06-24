package com.smsreseller.identity.web.dto;

import java.util.UUID;

/**
 * Response DTO for {@code GET /auth/me} (D-13, MOBL-02).
 *
 * <p>Returns the caller's userId and current verification_status re-read from the database —
 * without issuing or rotating any tokens. Used by the Flutter PENDING screen to poll
 * verification state cheaply on a 10-second timer.
 *
 * @param userId the authenticated user's UUID (from JWT subject)
 * @param status the user's current {@code VerificationStatus} as a string
 *               (e.g. {@code "PENDING_VERIFICATION"}, {@code "VERIFIED"})
 */
public record MeResponse(UUID userId, String status) {}
