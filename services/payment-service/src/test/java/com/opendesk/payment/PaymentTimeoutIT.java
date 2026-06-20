package com.opendesk.payment;

// Requirements: PYMT-03 + PYMT-07
//   PYMT-03 — Payment times out (EXPIRED status) if no callback within 2 minutes (D-06)
//   PYMT-07 — Expired payment cannot be re-used; user must initiate a fresh payment
// Covered by: 03-05 (ReconciliationJob + TIMEOUT stub outcome path)

import org.junit.jupiter.api.Test;

class PaymentTimeoutIT {

    @Test
    void paymentMarkedExpiredWhenNocallbackWithinTwoMinutes() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }

    @Test
    void expiredPaymentCannotBeReactivated() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }
}
