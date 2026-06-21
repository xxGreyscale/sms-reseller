package com.opendesk.messaging.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for creating a campaign (MESG-01).
 */
public record CreateCampaignRequest(
        String name,

        @NotBlank(message = "body must not be blank")
        String body,

        @NotBlank(message = "senderId must not be blank")
        @Size(max = 11, message = "senderId must be at most 11 characters")
        String senderId,

        @NotNull(message = "groupIds must not be null")
        @Size(min = 1, message = "at least one groupId is required")
        Set<UUID> groupIds,

        /** Optional future dispatch time for scheduled campaigns (MESG-04). */
        Instant scheduledAt
) {}
