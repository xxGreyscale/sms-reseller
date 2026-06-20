package com.opendesk.wallet.reservation;

import java.util.List;
import java.util.UUID;

/**
 * Result of a successful credit reservation.
 *
 * @param lotIds        IDs of all lots that were partially or fully reserved
 * @param reservedCount total credits reserved (equals the requested count on success)
 */
public record ReservationResult(List<UUID> lotIds, int reservedCount) {
}
