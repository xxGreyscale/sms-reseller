package com.opendesk.identity;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Covers: IDEN-08 — Graceful degrade when NIDA unavailable; circuit breaker + retry; stays PENDING (D-05).
 *
 * <p>Placeholder stub. Rewritten in plan 02-03 with full assertions using stub "unavailable" outcome.
 */
class NidaDegradedIT {

    @Test
    void staysPendingWhenNidaUnavailableAndScheduledRetryRecovers() {
        Assumptions.abort("pending impl: IDEN-08 NIDA degraded path (D-05)");
    }
}
