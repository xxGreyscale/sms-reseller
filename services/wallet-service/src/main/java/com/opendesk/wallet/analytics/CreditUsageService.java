package com.opendesk.wallet.analytics;

import com.opendesk.wallet.transaction.CreditTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for ANLX-02 credit-usage spend trend.
 *
 * <p>Aggregates daily credit consumption for a single user — no cross-service joins,
 * no data from other users. The userId must be derived from the JWT subject by the caller.
 */
@Service
@RequiredArgsConstructor
public class CreditUsageService {

    private final CreditTransactionRepository creditTransactionRepository;

    /**
     * Returns daily credit consumption aggregates for the given user, newest-first.
     *
     * <p>Only debit transactions (CONSUME, EXPIRE) are included. GRANT and REFUND are excluded.
     * Days with no consumption are simply absent — gap-fill is the client's responsibility at MVP.
     *
     * @param userId the caller's UUID (derived from JWT subject — never accepted as a query param)
     * @return list of daily aggregates, newest-first, capped at last 90 days
     */
    @Transactional(readOnly = true)
    public List<CreditUsageDto> getDailyUsage(UUID userId) {
        return creditTransactionRepository.findDailyUsageByUser(userId).stream()
                .map(row -> new CreditUsageDto(row.date(), row.consumed()))
                .toList();
    }
}
