package com.opendesk.wallet;

// Per-lot consume/release operations in LotService (D-15)
// Requirement: MESG-10 wallet side — consumeFromLot / releaseFromLot
// Called by MessagingEventConsumer when MessageAccepted / MessageReleased arrive.

import com.opendesk.wallet.lot.CreditLot;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotService;
import com.opendesk.wallet.reservation.ReservationService;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import com.opendesk.wallet.transaction.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: consumeFromLot and releaseFromLot ledger mutations.
 *
 * <p>D-15 — after a reservation, the messaging-service signals each message
 * outcome via AMQP events. The wallet-service applies per-lot deltas:
 * <ul>
 *   <li>CONSUME: reserved--, consumed++ (send confirmed)</li>
 *   <li>RELEASE: reserved-- (send cancelled/skipped)</li>
 * </ul>
 *
 * <p>Both operations write an append-only CreditTransaction row.
 */
class LotConsumeReleaseTest extends AbstractWalletIntegrationTest {

    @Autowired
    LotService lotService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    CreditLotRepository creditLotRepository;

    @Autowired
    CreditTransactionRepository creditTransactionRepository;

    @BeforeEach
    void clean() {
        creditTransactionRepository.deleteAll();
        creditLotRepository.deleteAll();
    }

    @Test
    void consumeDecrementsReservedIncrementsConsumed() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // Grant 5 credits, reserve 2
        var lot = lotService.grantBonus(userId, 5, Instant.now().plus(30, ChronoUnit.DAYS));
        reservationService.reserve(userId, 2, campaignId);

        // Before consume
        CreditLot before = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(before.getReserved()).isEqualTo(2);
        assertThat(before.getConsumed()).isEqualTo(0);

        // Consume 1 from lot
        lotService.consumeFromLot(userId, lot.getId());

        // After consume
        CreditLot after = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(after.getReserved()).isEqualTo(1);    // reserved decremented
        assertThat(after.getConsumed()).isEqualTo(1);    // consumed incremented

        // CONSUME CreditTransaction must be written
        var txns = creditTransactionRepository.findAll().stream()
                .filter(t -> t.getLotId().equals(lot.getId()) && t.getTxnType() == TxnType.CONSUME)
                .toList();
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getDelta()).isEqualTo(1);
        assertThat(txns.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void releaseDecrementsReservedLeavesConsumedUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // Grant 5 credits, reserve 2
        var lot = lotService.grantBonus(userId, 5, Instant.now().plus(30, ChronoUnit.DAYS));
        reservationService.reserve(userId, 2, campaignId);

        // Before release
        CreditLot before = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(before.getReserved()).isEqualTo(2);
        assertThat(before.getConsumed()).isEqualTo(0);

        // Release 1 from lot
        lotService.releaseFromLot(userId, lot.getId());

        // After release
        CreditLot after = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(after.getReserved()).isEqualTo(1);    // reserved decremented
        assertThat(after.getConsumed()).isEqualTo(0);    // consumed unchanged

        // RELEASE CreditTransaction must be written
        var txns = creditTransactionRepository.findAll().stream()
                .filter(t -> t.getLotId().equals(lot.getId()) && t.getTxnType() == TxnType.RELEASE)
                .toList();
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getDelta()).isEqualTo(1);
        assertThat(txns.get(0).getUserId()).isEqualTo(userId);
    }
}
