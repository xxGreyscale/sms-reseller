package com.opendesk.wallet;

// Requirement: WLET-05 — ExpiryWarningJob + ExpirySweepJob

import com.opendesk.wallet.lot.CreditLot;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotType;
import com.opendesk.wallet.outbox.OutboxRepository;
import com.opendesk.wallet.sweep.ExpirySweepJob;
import com.opendesk.wallet.sweep.ExpiryWarningJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WLET-05:
 * - ExpiryWarningJob emits ExpiryWarning outbox event for PURCHASED lots expiring within 7 days.
 * - ExpirySweepJob marks lots past expires_at as expired (writes EXPIRE transactions as belt-and-suspenders).
 *
 * <p>TDD RED: fails before ExpiryWarningJob and ExpirySweepJob are created.
 */
class ExpiryWarningIT extends AbstractWalletIntegrationTest {

    @Autowired
    private ExpiryWarningJob expiryWarningJob;

    @Autowired
    private ExpirySweepJob expirySweepJob;

    @Autowired
    private CreditLotRepository lotRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private com.opendesk.wallet.transaction.CreditTransactionRepository transactionRepository;

    @BeforeEach
    void cleanData() {
        transactionRepository.deleteAll();
        outboxRepository.deleteAll();
        lotRepository.deleteAll();
    }

    /**
     * A PURCHASED lot expiring within 7 days produces one ExpiryWarning outbox event.
     */
    @Test
    void expiryWarningJobEmitsWarningForLotDueSoon() {
        UUID userId = UUID.randomUUID();
        // Lot expiring in 3 days (within 7-day window)
        CreditLot lot = seedLot(userId, 100, LotType.PURCHASED, Instant.now().plus(3, ChronoUnit.DAYS));

        expiryWarningJob.warnExpiringSoon();

        List<com.opendesk.wallet.outbox.OutboxEntry> warnings = outboxRepository.findBySentFalse().stream()
                .filter(e -> "ExpiryWarning".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .toList();
        assertThat(warnings).hasSize(1);
        // Payload should reference the lot
        assertThat(warnings.get(0).getPayload()).contains(lot.getId().toString());
    }

    /**
     * A PURCHASED lot expiring after 7 days does NOT produce a warning.
     */
    @Test
    void expiryWarningJobDoesNotEmitForLotExpiringLater() {
        UUID userId = UUID.randomUUID();
        // Lot expiring in 30 days (outside 7-day window)
        seedLot(userId, 100, LotType.PURCHASED, Instant.now().plus(30, ChronoUnit.DAYS));

        expiryWarningJob.warnExpiringSoon();

        List<com.opendesk.wallet.outbox.OutboxEntry> warnings = outboxRepository.findBySentFalse().stream()
                .filter(e -> "ExpiryWarning".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .toList();
        assertThat(warnings).isEmpty();
    }

    /**
     * Running the warning job twice does NOT re-warn for the same lot in the same cycle (dedup).
     */
    @Test
    void expiryWarningJobDeduplicatesPerLot() {
        UUID userId = UUID.randomUUID();
        seedLot(userId, 100, LotType.PURCHASED, Instant.now().plus(2, ChronoUnit.DAYS));

        expiryWarningJob.warnExpiringSoon(); // first run
        expiryWarningJob.warnExpiringSoon(); // second run — same lot, must not re-warn

        long warningCount = outboxRepository.findBySentFalse().stream()
                .filter(e -> "ExpiryWarning".equals(e.getEventType()))
                .filter(e -> e.getAggregateId().equals(userId.toString()))
                .count();
        assertThat(warningCount).isEqualTo(1);
    }

    /**
     * ExpirySweepJob marks expired lots and writes EXPIRE transactions (belt-and-suspenders).
     * Lots already past expires_at should be marked so balance queries remain consistent.
     */
    @Test
    void expirySweepJobWritesExpireTransactionForPastLot() {
        UUID userId = UUID.randomUUID();
        // Lot that expired 1 hour ago
        CreditLot expiredLot = seedLot(userId, 50, LotType.PURCHASED,
                Instant.now().minus(1, ChronoUnit.HOURS));

        expirySweepJob.sweep();

        // Verify an EXPIRE transaction was written for the expired lot
        var expireTransactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(t -> com.opendesk.wallet.transaction.TxnType.EXPIRE.equals(t.getTxnType()))
                .filter(t -> expiredLot.getId().equals(t.getLotId()))
                .toList();
        assertThat(expireTransactions).hasSize(1);
        // Delta should be the remaining credits in the lot
        assertThat(expireTransactions.get(0).getDelta()).isEqualTo(-50);
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private CreditLot seedLot(UUID userId, int credits, LotType type, Instant expiresAt) {
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
        return lotRepository.save(lot);
    }
}
