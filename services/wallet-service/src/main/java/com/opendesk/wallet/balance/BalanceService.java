package com.opendesk.wallet.balance;

import com.opendesk.wallet.lot.CreditLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Derives the available SMS credit balance for a user.
 *
 * <p>Balance is never stored — it is always computed as:
 * {@code SUM(granted - consumed - reserved)} over non-expired lots (D-02).
 * Expired lots ({@code expiresAt <= now}) are excluded (WLET-06/07).
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final CreditLotRepository lotRepository;

    /**
     * Returns the current available credit balance for the given user.
     *
     * <p>Available = granted - consumed - reserved, summed over lots where
     * {@code expiresAt > now()}. Expired lots contribute zero to the balance.
     *
     * @param userId the user whose balance to derive
     * @return available SMS credits (never negative)
     */
    @Transactional(readOnly = true)
    public int getBalance(UUID userId) {
        return lotRepository.sumAvailableCredits(userId, Instant.now());
    }
}
