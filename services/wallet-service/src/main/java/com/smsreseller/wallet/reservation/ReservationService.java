package com.smsreseller.wallet.reservation;

import com.smsreseller.wallet.lot.CreditLot;
import com.smsreseller.wallet.lot.CreditLotRepository;
import com.smsreseller.wallet.transaction.CreditTransaction;
import com.smsreseller.wallet.transaction.CreditTransactionRepository;
import com.smsreseller.wallet.transaction.TxnType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
// LotAllocation is in the same package — no import needed

/**
 * Pessimistic expiry-soonest-first credit reservation (D-01, D-02, WLET-03).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Acquire pessimistic write locks on all available lots for the user, ordered by
 *       {@code expiresAt ASC} (consistent lock order prevents deadlocks — Pitfall 3).</li>
 *   <li>Walk lots in that order; take {@code min(available, remaining)} from each.</li>
 *   <li>Write a {@code RESERVE} {@link CreditTransaction} row per lot touched (append-only).</li>
 *   <li>If remaining credits exceed all available lots, throw
 *       {@link InsufficientCreditsException} — the transaction rolls back, leaving all lots
 *       and the transaction table unchanged.</li>
 * </ol>
 *
 * <p>Isolation is {@code READ_COMMITTED} (PostgreSQL default) — {@code REPEATABLE_READ}
 * is unnecessary and increases contention (Pitfall 3, 03-RESEARCH).
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    /** Maximum lots to lock per reservation call — bounds the SELECT FOR UPDATE result set. */
    private static final int MAX_LOTS_PAGE = 50;

    private final CreditLotRepository lotRepository;
    private final CreditTransactionRepository txnRepository;

    /**
     * Reserves {@code count} credits for the given user in a single atomic transaction.
     *
     * @param userId      the user whose credits to reserve
     * @param count       number of SMS credits to reserve (must be positive)
     * @param referenceId campaign or job reference — recorded on each RESERVE transaction row
     * @return {@link ReservationResult} with the lot IDs touched and total reserved count
     * @throws InsufficientCreditsException if available credits are less than {@code count};
     *                                      the transaction is rolled back — no state changes
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResult reserve(UUID userId, int count, UUID referenceId) {
        Instant now = Instant.now();
        List<CreditLot> lots = lotRepository.findAvailableByUserIdOrderByExpiresAtAsc(
                userId, now, PageRequest.ofSize(MAX_LOTS_PAGE));

        int remaining = count;
        List<UUID> lotIds = new ArrayList<>();
        List<LotAllocation> allocations = new ArrayList<>();

        for (CreditLot lot : lots) {
            if (remaining <= 0) break;

            int available = lot.getGranted() - lot.getConsumed() - lot.getReserved();
            if (available <= 0) continue; // query filter should prevent this, but be defensive

            int take = Math.min(available, remaining);
            lot.setReserved(lot.getReserved() + take);
            remaining -= take;
            lotIds.add(lot.getId());
            allocations.add(new LotAllocation(lot.getId(), take));

            txnRepository.save(
                    new CreditTransaction(userId, lot.getId(), TxnType.RESERVE, take, referenceId));
        }

        if (remaining > 0) {
            // Not enough credits — throw to trigger rollback of all lot.setReserved changes
            throw new InsufficientCreditsException(
                    "Requested " + count + " credits but only " + (count - remaining) + " available");
        }

        return new ReservationResult(lotIds, count, allocations);
    }
}
