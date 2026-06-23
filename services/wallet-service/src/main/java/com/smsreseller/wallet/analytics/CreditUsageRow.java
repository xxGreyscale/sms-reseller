package com.smsreseller.wallet.analytics;

import java.time.LocalDate;

/**
 * JPQL projection for the daily credit usage aggregate query.
 *
 * <p>Used as a constructor expression in {@code CreditTransactionRepository.findDailyUsageByUser}.
 */
public record CreditUsageRow(LocalDate date, long consumed) {
}
