package com.opendesk.wallet.reservation;

import java.util.List;
import java.util.UUID;

/**
 * Result of a successful credit reservation.
 *
 * <p>Carries both the legacy {@code lotIds} list (back-compat for all Phase 3 callers)
 * and the new {@code allocations} list (D-13 — per-lot counts for messaging-service
 * to assign each recipient to the correct lot).
 *
 * @param lotIds        IDs of all lots that were partially or fully reserved (legacy, distinct per lot)
 * @param reservedCount total credits reserved (equals the requested count on success)
 * @param allocations   per-lot allocation breakdown: each entry carries (lotId, count);
 *                      sum of counts == reservedCount; ordered expiry-soonest-first
 */
public record ReservationResult(List<UUID> lotIds, int reservedCount, List<LotAllocation> allocations) {
}
