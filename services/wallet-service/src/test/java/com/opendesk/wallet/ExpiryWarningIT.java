package com.opendesk.wallet;

// Requirement: WLET-05 — ExpirySweepJob emits expiry-warning event for lots expiring within 7 days
// Covered by: 03-03 (ExpirySweepJob + outbox event)

import org.junit.jupiter.api.Test;

class ExpiryWarningIT {

    @Test
    void expirySweepJobEmitsWarningForLotsDueSoon() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-03");
    }
}
