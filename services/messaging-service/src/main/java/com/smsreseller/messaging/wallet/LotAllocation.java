package com.smsreseller.messaging.wallet;

import java.util.UUID;

/**
 * Per-lot credit allocation returned by the wallet reservation endpoint.
 *
 * <p>Mirrors the wallet-service LotAllocation record (04-07). Used by CampaignService to
 * zip each recipient to the correct credit lot (D-13, Pitfall 4).
 *
 * <p>allocations.stream().mapToInt(LotAllocation::count).sum() == reservedCount
 */
public record LotAllocation(UUID lotId, int count) {}
