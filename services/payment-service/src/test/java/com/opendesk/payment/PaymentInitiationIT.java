package com.opendesk.payment;

// Requirement: PYMT-02 — Initiating payment creates a PENDING record and triggers STK push via gateway
// Also covers D-05/D-13: partial unique index prevents two PENDING payments for same user
// Covered by: 03-05 (PaymentService + StubPaymentGateway)

import org.junit.jupiter.api.Test;

class PaymentInitiationIT {

    @Test
    void initiatePaymentCreatessPendingRecordAndTriggersGateway() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }

    @Test
    void secondConcurrentPaymentInitiationRejectedForSameUser() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }
}
