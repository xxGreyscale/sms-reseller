package com.opendesk.identity.verification;

import java.util.UUID;

/**
 * Seam for finalizing a successful NIDA identity verification.
 *
 * <p>This interface is defined in plan 02-03 but implemented in plan 02-06.
 * The 02-06 implementation will:
 * <ol>
 *   <li>Flip {@code user.status = VERIFIED} in the same DB transaction</li>
 *   <li>INSERT a default sender ID row (SNDR-01)</li>
 *   <li>INSERT an outbox row ({@code UserVerified} event with 50 free credits) (IDEN-03)</li>
 * </ol>
 *
 * <p>The {@link VerificationOrchestrator} depends only on this interface so that it can
 * compile and run in Wave 2 while the real implementation arrives in Wave 3 (02-06).
 *
 * <p>Until 02-06 lands, a no-op placeholder bean is provided in this plan so the
 * application context loads correctly.
 */
public interface VerificationFinalizer {

    /**
     * Finalize a successful NIDA verification for the given user.
     *
     * <p>MUST be called inside a {@code @Transactional} boundary (the implementation
     * in 02-06 will enforce this).
     *
     * @param userId UUID of the user whose identity was successfully verified
     */
    void finalizeVerification(UUID userId);
}
