package com.smsreseller.payment;

// Requirement: PYMT-08 — Failed payment results in REFUND credit lot if credits were pre-reserved
// Covered by: 03-05 (PaymentService failure path + REFUND lot creation via outbox event)

import org.junit.jupiter.api.Test;

class RefundIT {

    @Test
    void failedPaymentCallbackCreatesRefundCreditLotForUser() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-05");
    }
}
