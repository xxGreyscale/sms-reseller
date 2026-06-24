package com.smsreseller.wallet.consumer;

import java.util.UUID;

/**
 * Local mirror of the {@code UserVerified} event published by identity-service.
 *
 * <p>Intentionally NOT imported from the identity module — cross-module source dependencies
 * violate the service ownership boundary (CLAUDE.md: no cross-service DB joins or source deps).
 * The wallet-service deserialises the JSON payload into this local record.
 *
 * <p>Published by identity-service OutboxRelay to exchange {@code identity.events} with routing
 * key {@code identity.UserVerified}. Payload contract (from 02-06-SUMMARY.md):
 * {@code {eventId: string, userId: UUID string, freeCredits: int (default 50)}}.
 */
public record UserVerifiedEvent(String eventId, UUID userId, int freeCredits) {

    /** Default free credits granted on NIDA verification (D-03). */
    public static final int DEFAULT_FREE_CREDITS = 50;
}
