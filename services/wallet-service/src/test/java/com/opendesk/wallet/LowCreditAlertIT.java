package com.opendesk.wallet;

// Requirement: WLET-04 — LowCreditAlertJob emits an outbox event when balance drops below threshold (D-08: 20 credits)

import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.lot.CreditLot;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotType;
import com.opendesk.wallet.outbox.OutboxRepository;
import com.opendesk.wallet.sweep.LowCreditAlertJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WLET-04: LowCreditAlertJob emits exactly one LowCreditAlert
 * outbox event per cycle when a user's balance is below the configured threshold (D-08: 20).
 *
 * <p>TDD RED: fails before LowCreditAlertJob and sweep package are created.
 */
class LowCreditAlertIT extends AbstractWalletIntegrationTest {

    @Autowired
    private LowCreditAlertJob lowCreditAlertJob;

    @Autowired
    private CreditLotRepository lotRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BalanceService balanceService;

    @BeforeEach
    void cleanData() {
        outboxRepository.deleteAll();
        lotRepository.deleteAll();
    }

    /**
     * A user whose balance is below the threshold (default 20) receives exactly one
     * LowCreditAlert outbox event when the job runs.
     */
    @Test
    void alertJobEmitsEventWhenBalanceBelowThreshold() {
        UUID userId = UUID.randomUUID();
        // Seed a lot with 10 credits (< threshold of 20)
        seedLot(userId, 10, LotType.BONUS, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(balanceService.getBalance(userId)).isEqualTo(10);

        // Run the job
        lowCreditAlertJob.alert();

        // Expect exactly one LowCreditAlert outbox entry for this user
        List<com.opendesk.wallet.outbox.OutboxEntry> alerts = outboxRepository.findBySentFalse().stream()
                .filter(e -> "LowCreditAlert".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .toList();
        assertThat(alerts).hasSize(1);
    }

    /**
     * A user whose balance is at or above the threshold gets no alert.
     */
    @Test
    void alertJobDoesNotEmitEventWhenBalanceAboveThreshold() {
        UUID userId = UUID.randomUUID();
        // Seed a lot with 50 credits (> threshold of 20)
        seedLot(userId, 50, LotType.BONUS, Instant.now().plus(30, ChronoUnit.DAYS));

        lowCreditAlertJob.alert();

        List<com.opendesk.wallet.outbox.OutboxEntry> alerts = outboxRepository.findBySentFalse().stream()
                .filter(e -> "LowCreditAlert".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .toList();
        assertThat(alerts).isEmpty();
    }

    /**
     * Running the job twice in the same cycle must NOT emit a second alert for the same user
     * (dedup by processed_events per cycle key — T-03-20 acceptance, MVP-simple).
     */
    @Test
    void alertJobDoesNotReAlertInSameCycle() {
        UUID userId = UUID.randomUUID();
        seedLot(userId, 5, LotType.BONUS, Instant.now().plus(30, ChronoUnit.DAYS));

        lowCreditAlertJob.alert(); // first run
        lowCreditAlertJob.alert(); // second run — same cycle, same processed_events key

        long alertCount = outboxRepository.findBySentFalse().stream()
                .filter(e -> "LowCreditAlert".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .count();
        assertThat(alertCount).isEqualTo(1);
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private void seedLot(UUID userId, int credits, LotType type, Instant expiresAt) {
        CreditLot lot = CreditLot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .lotType(type)
                .granted(credits)
                .consumed(0)
                .reserved(0)
                .expiresAt(expiresAt)
                .paymentId(null)
                .build();
        lotRepository.save(lot);
    }
}
