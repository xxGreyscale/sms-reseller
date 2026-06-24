package com.smsreseller.messaging.analytics;

import com.smsreseller.messaging.message.MessageStatus;
import com.smsreseller.messaging.message.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Analytics service for messaging-service — ANLX-01 and ANLX-03.
 *
 * <p>All queries are scoped to the JWT subject's userId — no cross-user data exposure (T-05-02).
 */
@Service
@RequiredArgsConstructor
public class CampaignAnalyticsService {

    private final OutboundMessageRepository outboundMessageRepository;

    /**
     * ANLX-01: Get delivery stats for a campaign owned by userId.
     *
     * @param userId     JWT subject (IDOR guard — only owner's data)
     * @param campaignId the campaign to query
     * @return stats DTO, or null if campaign not found / not owned by userId
     */
    public CampaignStatsDto getStats(UUID userId, UUID campaignId) {
        long total = outboundMessageRepository.countByCampaignIdAndUserId(campaignId, userId);
        if (total == 0) {
            return null;
        }

        List<Object[]> rows = outboundMessageRepository.countByStatusForCampaignAndUser(campaignId, userId);

        int delivered = 0;
        int failed = 0;
        for (Object[] row : rows) {
            MessageStatus status = (MessageStatus) row[0];
            long count = (Long) row[1];
            if (status == MessageStatus.DELIVERED) delivered = (int) count;
            else if (status == MessageStatus.FAILED) failed = (int) count;
        }

        return new CampaignStatsDto(campaignId.toString(), (int) total, delivered, failed);
    }

    /**
     * ANLX-03: Get operator-level delivery rates for the given user.
     *
     * @param userId JWT subject — rates scoped to this user only
     * @return list of (operator, status, count) rows
     */
    public List<OperatorRateDto> getOperatorRates(UUID userId) {
        return outboundMessageRepository.findOperatorRatesByUser(userId);
    }
}
