package com.opendesk.identity.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Async NIDA verification orchestrator.
 *
 * <p>{@link #verifyAsync(UUID, String)} is annotated {@code @Async("nidaExecutor")} so it
 * runs on the bounded NIDA thread pool (AsyncConfig: core=4, max=8, queue=50).
 * The calling registration thread returns immediately (IDEN-02 — must not block on NIDA).
 *
 * <p>Flow on each invocation:
 * <ol>
 *   <li>Call {@link NidaVerificationService#verify(String)} (stub or real, by profile)</li>
 *   <li>VERIFIED → call {@link VerificationFinalizer#finalizeVerification(UUID)}</li>
 *   <li>REJECTED → leave user PENDING (no finalizer call; no retry — permanent rejection)</li>
 *   <li>{@link NidaTransientException} → swallow exception, leave user PENDING;
 *       {@link VerificationRetryJob} will re-dispatch on its next tick (IDEN-08)</li>
 * </ol>
 *
 * <p>PII: the NIN is never logged. The orchestrator logs only the userId and outcome type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationOrchestratorImpl implements VerificationOrchestrator {

    private final NidaVerificationService nidaVerificationService;
    private final VerificationFinalizer verificationFinalizer;

    @Async("nidaExecutor")
    @Override
    public void verifyAsync(UUID userId, String nin) {
        // PII: nin is never logged; userId is safe to log (internal UUID)
        log.debug("Starting NIDA verification for userId={}", userId);

        try {
            NidaResult result = nidaVerificationService.verify(nin);

            if (result == NidaResult.VERIFIED) {
                log.info("NIDA verification VERIFIED for userId={}", userId);
                verificationFinalizer.finalizeVerification(userId);
            } else {
                // REJECTED — permanent; do not retry
                log.info("NIDA verification REJECTED for userId={} — user stays PENDING (permanent rejection)", userId);
            }

        } catch (NidaTransientException e) {
            // Transient failure — do NOT propagate; leave user PENDING for retry job (IDEN-08)
            log.warn("NIDA verification transient failure for userId={} — user stays PENDING, retry job will re-dispatch: {}",
                    userId, e.getMessage());
        } catch (Exception e) {
            // Unexpected error — log and swallow to prevent polluting the executor thread
            log.error("Unexpected error during NIDA verification for userId={} — user stays PENDING", userId, e);
        }
    }
}
