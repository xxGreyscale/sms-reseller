package com.smsreseller.messaging.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for creating a campaign (MESG-01).
 *
 * <p>Targeting: either {@code groupIds} OR {@code contactIds} must be provided (not both null/empty).
 * The service-layer guard in {@link CampaignService#create} enforces this at runtime
 * (D-12, MOBL-07, T-06-03-03).
 *
 * <p>D-12 change: {@code groupIds} is now nullable (was @NotNull/@Size(min=1) in 04-05).
 * Flutter MVP uses flat contacts with no groups — contactIds[] is the new targeting field.
 * Both paths are supported; the group path is preserved for regression (MESG-01).
 */
public record CreateCampaignRequest(
        String name,

        @NotBlank(message = "body must not be blank")
        String body,

        @NotBlank(message = "senderId must not be blank")
        @Size(max = 11, message = "senderId must be at most 11 characters")
        String senderId,

        /**
         * Contact group IDs — now nullable; either groupIds OR contactIds must be provided.
         * Validated at service layer (not annotation) to give a clear error message.
         */
        Set<UUID> groupIds,

        /**
         * Flat contact IDs for targeting without groups (D-12, MOBL-07).
         * Either groupIds OR contactIds must be non-empty; enforced in CampaignService.create.
         */
        Set<UUID> contactIds,

        /** Optional future dispatch time for scheduled campaigns (MESG-04). */
        Instant scheduledAt
) {}
