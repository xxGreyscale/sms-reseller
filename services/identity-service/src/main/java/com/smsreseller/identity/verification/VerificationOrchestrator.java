package com.smsreseller.identity.verification;

import java.util.UUID;

/**
 * Orchestrates async NIDA identity verification for a registered user.
 *
 * <p>The {@link #verifyAsync(UUID, String)} method is annotated {@code @Async("nidaExecutor")}
 * so it executes on the bounded NIDA thread pool (AsyncConfig) and DOES NOT block the calling
 * registration thread (IDEN-02).
 *
 * <p>On successful NIDA verification, the orchestrator calls
 * {@link VerificationFinalizer#finalizeVerification(UUID)} to commit the VERIFIED status
 * transition and outbox event (02-06 implements the finalizer; this plan defines only the seam).
 *
 * <p>On transient failure (NIDA unavailable / timeout), the exception is swallowed here and
 * the user stays PENDING. The {@link VerificationRetryJob} will re-dispatch pending users
 * on its next schedule tick (IDEN-08).
 */
public interface VerificationOrchestrator {

    /**
     * Dispatch verification asynchronously for the given user.
     *
     * <p>Implementations annotate this method with {@code @Async("nidaExecutor")}.
     *
     * @param userId the registered user's UUID
     * @param nin    the National ID Number to verify against NIDA (PII — must NOT be logged)
     */
    void verifyAsync(UUID userId, String nin);
}
