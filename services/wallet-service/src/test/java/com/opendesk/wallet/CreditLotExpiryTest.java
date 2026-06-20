package com.opendesk.wallet;

// Requirements: WLET-06 + WLET-07
//   WLET-06 — Purchased credit lots expire after 12 months; bonus lots expire after 30 days
//   WLET-07 — Expired lots are excluded from balance and reservation queries
// Covered by: 03-03 (ExpirySweepJob zeroing expired lots + balance/reservation query filters)

import org.junit.jupiter.api.Test;

class CreditLotExpiryTest {

    @Test
    void purchasedLotsExpireAfter12MonthsBonusAfter30Days() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-03");
    }

    @Test
    void expiredLotsExcludedFromBalanceAndReservation() {
        org.junit.jupiter.api.Assumptions.abort("placeholder — implemented in 03-03");
    }
}
