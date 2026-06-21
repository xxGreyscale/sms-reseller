package com.opendesk.wallet.reservation;

import java.util.UUID;

/**
 * Per-lot credit count from a reservation (D-13).
 *
 * <p>Carried by {@link ReservationResult#allocations()}. Each entry records how many
 * credits were reserved from a specific lot, enabling messaging-service to assign
 * each recipient to the correct lot (expiry-soonest-first ordering is preserved).
 *
 * @param lotId the credit lot from which credits were reserved
 * @param count the number of credits reserved from that lot
 */
public record LotAllocation(UUID lotId, int count) {
}
