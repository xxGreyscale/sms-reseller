package com.opendesk.payment;

// Cross-service: ReconciliationJob handles EXPIRED payments that receive a late-success callback
// Requirement: PYMT-03 (timeout) + PYMT-04 (late SUCCESS from Azampay after EXPIRED transition)
// Covered by: 03-05 (ReconciliationJob + late-success callback path in CallbackProcessor)

import org.junit.jupiter.api.Test;

class ReconciliationIT {

    @Test
    void reconciliationJobProcessesLateSuccessCallbackForExpiredPayment() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }
}
