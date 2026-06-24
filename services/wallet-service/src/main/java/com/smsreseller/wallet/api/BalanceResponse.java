package com.smsreseller.wallet.api;

/**
 * Response DTO for {@code GET /api/v1/wallet/balance} (WLET-01).
 *
 * <p>Returns the derived available SMS credit balance for the authenticated user.
 * Balance is always non-negative and excludes expired lots (D-02, WLET-06).
 *
 * @param availableCredits derived sum of (granted - consumed - reserved) over non-expired lots
 */
public record BalanceResponse(int availableCredits) {
}
