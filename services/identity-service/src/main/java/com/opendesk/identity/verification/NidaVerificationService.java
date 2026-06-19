package com.opendesk.identity.verification;

/**
 * Contract for NIDA (National Identification Authority of Tanzania) identity verification.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link StubNidaVerificationService} — {@code @Profile("stub")}: configurable outcomes
 *       for local dev and testing (D-04/D-05)</li>
 *   <li>{@link RealNidaVerificationService} — {@code @Profile("prod")}: actual NIDA REST call
 *       wrapped with Resilience4j circuit breaker + spring-retry exponential backoff (CLAUDE.md)</li>
 * </ul>
 *
 * <p>PII contract: the {@code nin} parameter is a National ID Number and MUST NOT be logged
 * by any implementation (T-02-PII).
 *
 * <p>Caching: NIDA results MUST NOT be cached (CLAUDE.md explicit rule — each registration
 * requires a live check).
 */
public interface NidaVerificationService {

    /**
     * Verify the given National ID Number against the NIDA database.
     *
     * @param nin the National ID Number (PII — must NOT be logged)
     * @return {@link NidaResult#VERIFIED} if NIDA confirms the identity,
     *         {@link NidaResult#REJECTED} if NIDA rejects the identity
     * @throws NidaTransientException if NIDA is temporarily unavailable (timeout / 5xx);
     *         the caller should retry later
     */
    NidaResult verify(String nin);
}
