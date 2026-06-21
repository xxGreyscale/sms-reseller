package com.opendesk.wallet;

// Per-lot allocation in ReservationResult (D-13)
// Requirement: MESG-10 wallet side — ReservationResult must expose List<LotAllocation>
// so messaging-service can assign each recipient to a specific credit lot.

import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotService;
import com.opendesk.wallet.reservation.LotAllocation;
import com.opendesk.wallet.reservation.ReservationResult;
import com.opendesk.wallet.reservation.ReservationService;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: per-lot allocation in ReservationResult.
 *
 * <p>D-13 — ReservationResult must expose a {@code List<LotAllocation>}
 * where each entry records (lotId, count) and the sum of all counts equals
 * {@code reservedCount}. Legacy {@code lotIds} list must remain present and
 * backward-compatible.
 */
class ReservationAllocationTest extends AbstractWalletIntegrationTest {

    @Autowired
    ReservationService reservationService;

    @Autowired
    LotService lotService;

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
    void reservationExposesPerLotAllocationsOrderedByExpiryAscending() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // lotA expires sooner (1 day from now), granted=3
        Instant expiryA = Instant.now().plus(1, ChronoUnit.DAYS);
        var lotA = lotService.grantBonus(userId, 3, expiryA);

        // lotB expires later (10 days from now), granted=5
        Instant expiryB = Instant.now().plus(10, ChronoUnit.DAYS);
        var lotB = lotService.grantBonus(userId, 5, expiryB);

        // Reserve 6 credits: 3 from lotA (exhausted), then 3 from lotB
        ReservationResult result = reservationService.reserve(userId, 6, campaignId);

        // Legacy back-compat: lotIds still works
        assertThat(result.lotIds()).containsExactly(lotA.getId(), lotB.getId());
        assertThat(result.reservedCount()).isEqualTo(6);

        // New: allocations list
        assertThat(result.allocations()).hasSize(2);

        LotAllocation firstAllocation = result.allocations().get(0);
        assertThat(firstAllocation.lotId()).isEqualTo(lotA.getId());
        assertThat(firstAllocation.count()).isEqualTo(3);

        LotAllocation secondAllocation = result.allocations().get(1);
        assertThat(secondAllocation.lotId()).isEqualTo(lotB.getId());
        assertThat(secondAllocation.count()).isEqualTo(3);

        // Counts must sum to reservedCount
        int allocationSum = result.allocations().stream()
                .mapToInt(LotAllocation::count)
                .sum();
        assertThat(allocationSum).isEqualTo(result.reservedCount());
    }

    @Test
    void singleLotReservationAllocationsHasOneEntry() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        var lot = lotService.grantBonus(userId, 10, Instant.now().plus(5, ChronoUnit.DAYS));

        ReservationResult result = reservationService.reserve(userId, 4, campaignId);

        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).lotId()).isEqualTo(lot.getId());
        assertThat(result.allocations().get(0).count()).isEqualTo(4);
        assertThat(result.reservedCount()).isEqualTo(4);

        // Legacy back-compat
        assertThat(result.lotIds()).containsExactly(lot.getId());
    }
}
