package com.smsreseller.messaging.wallet;

import java.util.List;
import java.util.UUID;

/**
 * Result of a successful credit reservation from wallet-service.
 *
 * <p>Mirrors the wallet-service ReservationResult (04-07).
 *
 * @param lotIds         distinct lot IDs touched by the reservation (legacy, back-compat)
 * @param reservedCount  total credits reserved
 * @param allocations    per-lot counts, expiry-soonest-first — used by CampaignService to
 *                       zip each recipient to the correct lotId (D-13)
 */
public record ReservationResult(List<UUID> lotIds, int reservedCount, List<LotAllocation> allocations) {}
