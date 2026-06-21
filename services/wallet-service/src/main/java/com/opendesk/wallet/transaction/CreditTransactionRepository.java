package com.opendesk.wallet.transaction;

import com.opendesk.wallet.analytics.CreditUsageRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {

    /** Returns paginated transaction history for a user, most-recent first. */
    Page<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Returns daily credit consumption aggregates for the given user over the last 90 days.
     *
     * <p>Only CONSUME, RESERVE, and EXPIRE transactions count as consumption (debits).
     * GRANT and REFUND are credits — excluded here. Results ordered newest-first, capped at 90 days.
     *
     * <p>ANLX-02 spend-trend query. No cross-service joins — wallet-service owns credit_transactions.
     *
     * @param userId the user to scope the aggregate to (from JWT subject — no IDOR)
     * @return list of daily aggregates, newest-first
     */
    @Query("""
            SELECT new com.opendesk.wallet.analytics.CreditUsageRow(
                CAST(t.createdAt AS java.time.LocalDate),
                SUM(t.delta)
            )
            FROM CreditTransaction t
            WHERE t.userId = :userId
              AND t.txnType IN (
                com.opendesk.wallet.transaction.TxnType.CONSUME,
                com.opendesk.wallet.transaction.TxnType.EXPIRE
              )
              AND t.createdAt >= CURRENT_TIMESTAMP - 90 DAY
            GROUP BY CAST(t.createdAt AS java.time.LocalDate)
            ORDER BY CAST(t.createdAt AS java.time.LocalDate) DESC
            """)
    List<CreditUsageRow> findDailyUsageByUser(@Param("userId") UUID userId);
}
