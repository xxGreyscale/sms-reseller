package com.opendesk.messaging.campaign;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for campaign endpoints.
 */
public record CampaignResponse(
        UUID id,
        UUID userId,
        String name,
        String body,
        String senderId,
        String status,
        Set<UUID> groupIds,
        Instant scheduledAt,
        Instant dispatchedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getUserId(),
                campaign.getName(),
                campaign.getBody(),
                campaign.getSenderId(),
                campaign.getStatus().name(),
                campaign.getGroupIds(),
                campaign.getScheduledAt(),
                campaign.getDispatchedAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
