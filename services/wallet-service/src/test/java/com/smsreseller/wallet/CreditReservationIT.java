package com.smsreseller.wallet;

// Requirement: WLET-03 — Credit reservation is expiry-soonest-first with pessimistic lock;
//              balance cannot go below zero even under concurrent load.

import com.smsreseller.wallet.balance.BalanceService;
import com.smsreseller.wallet.lot.LotService;
import com.smsreseller.wallet.reservation.InsufficientCreditsException;
import com.smsreseller.wallet.reservation.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditReservationIT extends AbstractWalletIntegrationTest {

    @Autowired
    private LotService lotService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BalanceService balanceService;

    @Test
    void reservingExactBalanceSucceeds() {
        UUID userId = UUID.randomUUID();
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 50, future);

        var result = reservationService.reserve(userId, 50, UUID.randomUUID());

        assertThat(result.reservedCount()).isEqualTo(50);
        assertThat(result.lotIds()).hasSize(1);
        // After reservation, balance (available = granted - consumed - reserved) is 0
        assertThat(balanceService.getBalance(userId)).isZero();
    }

    @Test
    void reservingMoreThanBalanceThrowsInsufficientCreditsAndMutatesNothing() {
        UUID userId = UUID.randomUUID();
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 30, future);

        assertThatThrownBy(() -> reservationService.reserve(userId, 31, UUID.randomUUID()))
                .isInstanceOf(InsufficientCreditsException.class);

        // Nothing mutated — full balance still available
        assertThat(balanceService.getBalance(userId)).isEqualTo(30);
    }

    @Test
    void reservationDrawsFromSoonestExpiringLotFirst() {
        UUID userId = UUID.randomUUID();
        // Bonus lot expires sooner (30 days)
        Instant bonusExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        // Purchased lot expires later (12 months)
        Instant purchasedExpiry = Instant.now().plus(365, ChronoUnit.DAYS);

        var bonusLot = lotService.grantBonus(userId, 40, bonusExpiry);
        var purchasedLot = lotService.grantPurchased(userId, 100, UUID.randomUUID());

        // Reserve 30 credits — should come from bonus lot (sooner expiry, D-01)
        var result = reservationService.reserve(userId, 30, UUID.randomUUID());

        assertThat(result.lotIds()).contains(bonusLot.getId());
        assertThat(result.lotIds()).doesNotContain(purchasedLot.getId());
    }

    @Test
    void concurrentReservationsCannotProduceNegativeBalance() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        // Grant only 100 credits
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(userId, 100, future);

        int threadCount = 2;
        // Two threads each try to reserve 100 credits — only one should succeed
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.reserve(userId, 100, UUID.randomUUID());
                    successes.incrementAndGet();
                } catch (InsufficientCreditsException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release both threads simultaneously
        doneLatch.await();
        executor.shutdown();

        // Exactly one must succeed; the other must fail with InsufficientCreditsException
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        // After the successful reservation, balance is 0 (reserved = granted)
        assertThat(balanceService.getBalance(userId)).isZero();
    }
}
