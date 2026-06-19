package com.opendesk.identity.verification;

/**
 * Result of a NIDA identity verification call.
 *
 * <p>Returned by {@link NidaVerificationService#verify(String)}.
 * Transient failures (timeout, NIDA unavailable) are signalled via
 * {@link NidaTransientException} rather than a result value.
 */
public enum NidaResult {

    /**
     * NIDA confirmed the NIN belongs to the registered user.
     * The orchestrator will call {@link VerificationFinalizer#finalizeVerification(java.util.UUID)}.
     */
    VERIFIED,

    /**
     * NIDA rejected the NIN — identity mismatch or invalid NIN.
     * The user stays PENDING; no finalizer call is made.
     */
    REJECTED
}
