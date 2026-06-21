package com.opendesk.messaging.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Campaign service — campaign lifecycle management.
 *
 * <p>Dispatch logic (QUEUED → SENDING → COMPLETED) is in 04-05.
 * Scheduled dispatch job is in 04-08.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;

    /**
     * Create a campaign in DRAFT state (MESG-01).
     * Recipients are NOT expanded here — expansion happens at dispatch time (04-05).
     */
    @Transactional
    public Campaign create(UUID userId, CreateCampaignRequest request) {
        CampaignStatus initialStatus = request.scheduledAt() != null
                ? CampaignStatus.SCHEDULED
                : CampaignStatus.DRAFT;

        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(request.name())
                .body(request.body())
                .senderId(request.senderId())
                .status(initialStatus)
                .groupIds(request.groupIds())
                .scheduledAt(request.scheduledAt())
                .build();

        campaign = campaignRepository.save(campaign);
        log.info("Campaign created: id={} userId={} status={}", campaign.getId(), userId, campaign.getStatus());
        return campaign;
    }

    /**
     * Paginated list of campaigns for a user.
     */
    @Transactional(readOnly = true)
    public Page<Campaign> list(UUID userId, Pageable pageable) {
        return campaignRepository.findByUserId(userId, pageable);
    }

    /**
     * IDOR-safe campaign lookup. Returns empty if id belongs to another user.
     */
    @Transactional(readOnly = true)
    public Optional<Campaign> findByIdAndUser(UUID id, UUID userId) {
        return campaignRepository.findByIdAndUserId(id, userId);
    }
}
