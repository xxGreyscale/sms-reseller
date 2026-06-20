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
}
