package com.opendesk.identity.outbox;

import java.util.UUID;

/**
 * Domain event emitted when a user's NIDA identity verification succeeds.
 *
 * <p>This event is the Phase 3 wallet credit-grant trigger (IDEN-03):
 * the wallet service consumes it to credit the user's account with 50 free SMS credits.
 *
 * <p>Consumers MUST deduplicate by {@link #eventId} (Pitfall 5 from 02-RESEARCH.md).
 *
 * @param eventId     unique event identifier — consumers deduplicate on this field
 * @param userId      the user whose identity was verified
 * @param freeCredits number of free SMS credits to grant (50 at MVP)
 */
public record UserVerifiedEvent(
        UUID eventId,
        UUID userId,
        int freeCredits
) {
    /** Standard credit grant amount at MVP (IDEN-03, D-03). */
    public static final int DEFAULT_FREE_CREDITS = 50;
}
