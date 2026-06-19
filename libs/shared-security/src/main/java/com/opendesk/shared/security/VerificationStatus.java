package com.opendesk.shared.security;

/**
 * Cross-module copy of the user's NIDA verification status.
 *
 * <p><strong>Contract (D-02):</strong> The literal string values of this enum MUST stay
 * in sync with {@code com.opendesk.identity.user.VerificationStatus} in identity-service.
 * The JWT carries these values as the {@code verification_status} claim. Any mismatch
 * between enum literals will silently break the cross-module contract — all downstream
 * modules will fail to parse the claim correctly.
 *
 * <p>To add a new status:
 * <ol>
 *   <li>Add the literal here AND in identity-service's VerificationStatus.</li>
 *   <li>Update {@link AuthClaims} if new status affects feature gating.</li>
 *   <li>Update the Flyway migration for the {@code status} column DEFAULT if needed.</li>
 * </ol>
 */
public enum VerificationStatus {

    /**
     * User is registered but NIDA verification has not yet completed (or failed transiently).
     * Feature access is gated until status transitions to VERIFIED.
     */
    PENDING_VERIFICATION,

    /**
     * NIDA verification succeeded. Full feature access is unlocked.
     */
    VERIFIED
}
