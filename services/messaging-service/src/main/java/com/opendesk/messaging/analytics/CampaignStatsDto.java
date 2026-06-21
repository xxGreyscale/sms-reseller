package com.opendesk.messaging.analytics;

/**
 * ANLX-01: Campaign delivery statistics for a single campaign.
 *
 * @param campaignId  the campaign UUID
 * @param totalCount  total messages in the campaign
 * @param deliveredCount messages confirmed delivered
 * @param failedCount messages that failed permanently
 */
public record CampaignStatsDto(
        String campaignId,
        int totalCount,
        int deliveredCount,
        int failedCount
) {}
