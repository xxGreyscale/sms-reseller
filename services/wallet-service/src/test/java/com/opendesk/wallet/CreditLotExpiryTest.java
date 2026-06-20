package com.opendesk.wallet;

// Requirements: WLET-06 + WLET-07
//   WLET-06 — Purchased credit lots expire after 12 months
//   WLET-07 — Bonus lots expire after 30 days; expired lots excluded from balance

import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.lot.LotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditLotExpiryTest extends AbstractWalletIntegrationTest {

    @Autowired
    private LotService lotService;

    @Autowired
    private BalanceService balanceService;

    @Test
    void purchasedLotsExpireAfter12MonthsBonusAfter30Days() {
        UUID userId = UUID.randomUUID();

        // Grant a purchased lot — should get 12-month expiry
        var purchased = lotService.grantPurchased(userId, 100, UUID.randomUUID());
        Instant expectedPurchasedExpiry = purchased.getCreatedAt().plus(365, ChronoUnit.DAYS);
        assertThat(purchased.getExpiresAt())
                .isAfterOrEqualTo(expectedPurchasedExpiry.minus(5, ChronoUnit.SECONDS))
                .isBeforeOrEqualTo(expectedPurchasedExpiry.plus(5, ChronoUnit.SECONDS));

        // Grant a bonus lot — explicit 30-day expiry passed by caller
        Instant bonusExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        var bonus = lotService.grantBonus(userId, 50, bonusExpiry);
        assertThat(bonus.getExpiresAt())
                .isAfterOrEqualTo(bonusExpiry.minus(5, ChronoUnit.SECONDS))
                .isBeforeOrEqualTo(bonusExpiry.plus(5, ChronoUnit.SECONDS));
    }

    @Test
    void expiredLotsExcludedFromBalanceAndReservation() {
        UUID userId = UUID.randomUUID();

        // Grant a lot that is already expired (expires in the past)
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        lotService.grantBonus(userId, 200, pastExpiry);

        // Grant a live lot that has not expired
        Instant futureExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 75, futureExpiry);

        // Balance must only reflect the live lot
        int balance = balanceService.getBalance(userId);
        assertThat(balance).isEqualTo(75);
    }

    @Test
    void balanceSumsTwoLiveLots() {
        UUID userId = UUID.randomUUID();

        Instant future30 = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant future60 = Instant.now().plus(60, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 100, future30);
        lotService.grantBonus(userId, 150, future60);

        assertThat(balanceService.getBalance(userId)).isEqualTo(250);
    }
}
