package com.opendesk.messaging.campaign;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for campaign endpoints.
 *
 * <p>Includes aggregate message counts (MESG-06):
 * <ul>
 *   <li>{@code totalCount} — total outbound_messages for this campaign</li>
 *   <li>{@code sentCount} — messages that reached SENT or beyond (DELIVERED)</li>
 *   <li>{@code deliveredCount} — messages confirmed DELIVERED via DLR</li>
 *   <li>{@code failedCount} — messages permanently FAILED</li>
 * </ul>
 *
 * <p>Counts are 0 for campaigns not yet dispatched (DRAFT/SCHEDULED).
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
        Instant updatedAt,
        int totalCount,
        int sentCount,
        int deliveredCount,
        int failedCount
) {
    /** Build a response with zero aggregate counts (for list endpoints where counts are expensive). */
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
                campaign.getUpdatedAt(),
                0, 0, 0, 0
        );
    }

    /** Build a response with pre-computed aggregate counts (for single-campaign detail endpoint). */
    public static CampaignResponse from(Campaign campaign, int totalCount, int sentCount,
                                        int deliveredCount, int failedCount) {
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
                campaign.getUpdatedAt(),
                totalCount,
                sentCount,
                deliveredCount,
                failedCount
        );
    }
}
