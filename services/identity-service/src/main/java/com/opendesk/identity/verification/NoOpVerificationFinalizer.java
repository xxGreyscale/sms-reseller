package com.opendesk.identity.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No-op placeholder implementation of {@link VerificationFinalizer}.
 *
 * <p>This bean is active ONLY when no other {@link VerificationFinalizer} bean is present
 * (i.e., until plan 02-06 provides the real transactional implementation).
 *
 * <p>Plan 02-06 will implement the real finalizer that:
 * <ol>
 *   <li>Flips {@code user.status = VERIFIED}</li>
 *   <li>INSERTs a default sender ID row (SNDR-01)</li>
 *   <li>INSERTs an outbox row ({@code UserVerified} event, 50 free credits, IDEN-03)</li>
 * </ol>
 *
 * <p>Tests in this plan (02-03) use a test-double spy to assert
 * {@code finalizeVerification(userId)} was called by the orchestrator.
 */
@Service
@ConditionalOnMissingBean(name = "transactionalVerificationFinalizer")
@Slf4j
public class NoOpVerificationFinalizer implements VerificationFinalizer {

    @Override
    public void finalizeVerification(UUID userId) {
        // No-op placeholder — real implementation provided by plan 02-06
        log.info("NoOpVerificationFinalizer.finalizeVerification called for userId={} " +
                 "(placeholder; 02-06 will implement the real VERIFIED transition)", userId);
    }
}
