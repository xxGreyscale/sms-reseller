package com.smsreseller.identity.user;

/**
 * Verification status of a user's identity against the NIDA national ID database.
 *
 * <p>This enum is the durable source of truth stored in Postgres.
 * The JWT carries this as the {@code verification_status} claim (D-02) so
 * downstream modules enforce feature gating locally without calling identity at runtime.
 *
 * <p>IMPORTANT: The literal names MUST stay in sync with
 * {@code com.smsreseller.shared.security.VerificationStatus} in libs/shared-security.
 * Changing a literal here without changing the shared-security copy WILL break the claim contract.
 */
public enum VerificationStatus {

    /**
     * User is registered but NIDA verification has not yet completed (or failed transiently).
     * The app shell is accessible but all features are gated (D-01).
     */
    PENDING_VERIFICATION,

    /**
     * NIDA verification succeeded. Full feature access is unlocked.
     * 50 free SMS credits are granted via outbox event (IDEN-03).
     */
    VERIFIED
}
