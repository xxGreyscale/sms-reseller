package com.opendesk.wallet.lot;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditLotRepository extends JpaRepository<CreditLot, UUID> {

    /**
     * Pessimistic write lock — returns available lots in expiry-ascending order for reservation.
     *
     * <p>Only includes lots where {@code (granted - consumed - reserved) > 0} and
     * {@code expiresAt > now} (D-01, D-02). The consistent lock ordering (ASC) prevents
     * deadlocks when two transactions race for the same user's lots (Pitfall 3, 03-RESEARCH).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM CreditLot l WHERE l.userId = :userId " +
           "AND (l.granted - l.consumed - l.reserved) > 0 " +
           "AND l.expiresAt > :now ORDER BY l.expiresAt ASC")
    List<CreditLot> findAvailableByUserIdOrderByExpiresAtAsc(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Derives the available credit balance for a user — never a stored column (D-02).
     *
     * <p>Excludes expired lots via {@code expiresAt > now} (WLET-06/07, Pitfall 4).
     */
    @Query("SELECT COALESCE(SUM(l.granted - l.consumed - l.reserved), 0) FROM CreditLot l " +
           "WHERE l.userId = :userId AND l.expiresAt > :now")
    int sumAvailableCredits(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Finds PURCHASED lots expiring before the given cutoff (expiry warning sweep).
     *
     * <p>Used by {@code ExpiryWarningJob} to find lots expiring within 7 days.
     *
     * @param cutoff   lots with expiresAt before this instant are returned
     * @param lotType  lot type to filter by (PURCHASED)
     * @param pageable bounded page to prevent unbounded queries
     */
    @Query("SELECT l FROM CreditLot l WHERE l.expiresAt > :now AND l.expiresAt < :cutoff " +
           "AND l.lotType = :lotType ORDER BY l.expiresAt ASC")
    List<CreditLot> findExpiringBefore(
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff,
            @Param("lotType") LotType lotType,
            Pageable pageable);

    /**
     * Finds lots that have already expired (past {@code expiresAt}) — used by ExpirySweepJob.
     *
     * @param cutoff  lots with expiresAt before this instant are returned
     * @param pageable bounded page
     */
    @Query("SELECT l FROM CreditLot l WHERE l.expiresAt < :cutoff ORDER BY l.expiresAt ASC")
    List<CreditLot> findExpiredBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Returns all distinct userIds with available balance below the given threshold.
     * Used by LowCreditAlertJob to find users needing an alert.
     *
     * @param threshold alert if balance < threshold
     * @param now       filter out expired lots
     */
    @Query("SELECT DISTINCT l.userId FROM CreditLot l WHERE l.expiresAt > :now " +
           "GROUP BY l.userId HAVING COALESCE(SUM(l.granted - l.consumed - l.reserved), 0) < :threshold")
    List<UUID> findUserIdsWithBalanceBelow(@Param("threshold") int threshold, @Param("now") Instant now);
}
