package com.opendesk.wallet.analytics;

import java.time.LocalDate;

/**
 * Daily credit consumption aggregate for ANLX-02 spend trend.
 *
 * @param date     the date (UTC) for this aggregate bucket
 * @param consumed total credits consumed on this date (sum of absolute CONSUME/RESERVE/EXPIRE deltas)
 */
public record CreditUsageDto(LocalDate date, long consumed) {
}
