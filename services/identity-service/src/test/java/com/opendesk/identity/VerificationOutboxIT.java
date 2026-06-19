package com.opendesk.identity;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Covers: IDEN-03 — 50 free SMS credits granted via transactional outbox on NIDA verification.
 *
 * <p>Placeholder stub. Rewritten in plan 02-03 with full container-backed assertions,
 * verifying outbox row written in same TX as VERIFIED status flip.
 */
class VerificationOutboxIT {

    @Test
    void writesOutboxRowInSameTransactionAsVerifiedFlip() {
        Assumptions.abort("pending impl: IDEN-03 transactional outbox");
    }
}
