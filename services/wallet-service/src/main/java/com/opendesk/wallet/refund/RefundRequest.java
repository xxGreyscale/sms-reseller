package com.opendesk.wallet.refund;

import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/wallet/refunds}.
 *
 * <p>Called by Phase 4 campaign dispatch when a campaign fails after credits were reserved.
 * The {@code idempotencyKey} ensures the same refund is not applied twice even if the
 * caller retries (D-07, T-03-17).
 *
 * @param userId         user to credit (must match JWT subject for internal callers)
 * @param credits        number of credits to credit back — must be positive (T-03-17)
 * @param referenceId    reference to the campaign or payment that triggered the refund
 * @param idempotencyKey caller-supplied key scoping this refund; duplicate calls with the
 *                       same key are silently ignored
 */
public record RefundRequest(
        UUID userId,
        @Positive int credits,
        UUID referenceId,
        String idempotencyKey
) {
}
