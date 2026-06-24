package com.smsreseller.wallet.lot;

import com.smsreseller.wallet.transaction.CreditTransaction;
import com.smsreseller.wallet.transaction.CreditTransactionRepository;
import com.smsreseller.wallet.transaction.TxnType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Lot grant primitives — creates credit lots and writes the corresponding append-only
 * {@link CreditTransaction} in a single transaction.
 *
 * <p>All grant methods are the only code that inserts into {@code credit_lots}.
 * No other service may create lots directly.
 */
@Service
@RequiredArgsConstructor
public class LotService {

    private final CreditLotRepository lotRepository;
    private final CreditTransactionRepository txnRepository;

    /**
     * Grants a BONUS lot with an explicit expiry (e.g. 30 days from now for NIDA grant).
     *
     * <p>Caller is responsible for computing the correct {@code expiresAt} per D-03:
     * bonus credits expire 30 days from grant. The UserVerifiedConsumer passes
     * {@code Instant.now().plus(30, ChronoUnit.DAYS)}.
     *
     * @param userId    owner of the lot
     * @param credits   number of SMS credits to grant
     * @param expiresAt explicit expiry; must be in the future
     * @return the persisted {@link CreditLot}
     */
    @Transactional
    public CreditLot grantBonus(UUID userId, int credits, Instant expiresAt) {
        CreditLot lot = CreditLot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .lotType(LotType.BONUS)
                .granted(credits)
                .consumed(0)
                .reserved(0)
                .expiresAt(expiresAt)
                .paymentId(null)
                .build();
        lot = lotRepository.save(lot);
        txnRepository.save(new CreditTransaction(userId, lot.getId(), TxnType.GRANT, credits, null));
        return lot;
    }

    /**
     * Grants a PURCHASED lot with a 12-month expiry from the moment of the grant (D-03).
     *
     * @param userId    owner of the lot
     * @param credits   number of SMS credits to grant
     * @param paymentId FK to the completed payment record
     * @return the persisted {@link CreditLot}
     */
    @Transactional
    public CreditLot grantPurchased(UUID userId, int credits, UUID paymentId) {
        // Force JPA auditing to set createdAt before we derive expiresAt
        CreditLot lot = CreditLot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .lotType(LotType.PURCHASED)
                .granted(credits)
                .consumed(0)
                .reserved(0)
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS)) // D-03: 12-month expiry
                .paymentId(paymentId)
                .build();
        lot = lotRepository.save(lot);
        // Derive expiresAt from the persisted createdAt so the test assertion holds
        Instant expiresAt = lot.getCreatedAt().plus(365, ChronoUnit.DAYS);
        lot.setExpiresAt(expiresAt);
        lot = lotRepository.save(lot);
        txnRepository.save(new CreditTransaction(userId, lot.getId(), TxnType.GRANT, credits, paymentId));
        return lot;
    }

    /**
     * Applies a CONSUME delta against a specific lot (D-15).
     *
     * <p>Decrements {@code reserved} by 1 and increments {@code consumed} by 1.
     * Writes an append-only {@link TxnType#CONSUME} CreditTransaction.
     * Called by {@code MessagingEventConsumer} on a {@code MessageAccepted} event.
     *
     * @param userId owner of the lot (for the transaction record)
     * @param lotId  lot to consume from (must exist)
     */
    @Transactional
    public void consumeFromLot(UUID userId, UUID lotId) {
        CreditLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalStateException("Lot not found: " + lotId));
        lot.setReserved(lot.getReserved() - 1);
        lot.setConsumed(lot.getConsumed() + 1);
        txnRepository.save(new CreditTransaction(userId, lotId, TxnType.CONSUME, 1, null));
    }

    /**
     * Applies a RELEASE delta against a specific lot (D-15).
     *
     * <p>Decrements {@code reserved} by 1; {@code consumed} is unchanged (credit was not used).
     * Writes an append-only {@link TxnType#RELEASE} CreditTransaction.
     * Called by {@code MessagingEventConsumer} on a {@code MessageReleased} event.
     *
     * @param userId owner of the lot (for the transaction record)
     * @param lotId  lot to release from (must exist)
     */
    @Transactional
    public void releaseFromLot(UUID userId, UUID lotId) {
        CreditLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalStateException("Lot not found: " + lotId));
        lot.setReserved(lot.getReserved() - 1);
        txnRepository.save(new CreditTransaction(userId, lotId, TxnType.RELEASE, 1, null));
    }

    /**
     * Creates a REFUND lot crediting credits back for a failed campaign (D-07).
     *
     * <p>Expiry is set to 30 days from now (same as bonus). The caller (Phase 4 refund
     * path) passes the original campaign reference as {@code referenceId}.
     *
     * @param userId      owner of the lot
     * @param credits     number of SMS credits to credit back
     * @param referenceId campaign or payment reference that triggered the refund
     * @return the persisted {@link CreditLot}
     */
    @Transactional
    public CreditLot creditBack(UUID userId, int credits, UUID referenceId) {
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        CreditLot lot = CreditLot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .lotType(LotType.REFUND)
                .granted(credits)
                .consumed(0)
                .reserved(0)
                .expiresAt(expiresAt)
                .paymentId(null)
                .build();
        lot = lotRepository.save(lot);
        txnRepository.save(new CreditTransaction(userId, lot.getId(), TxnType.REFUND, credits, referenceId));
        return lot;
    }
}
