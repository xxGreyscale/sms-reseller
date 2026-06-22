package com.opendesk.messaging.campaign;

import com.opendesk.messaging.contact.ContactRecipientClient;
import com.opendesk.messaging.message.CarrierResolver;
import com.opendesk.messaging.message.MessageStatus;
import com.opendesk.messaging.message.MessageView;
import com.opendesk.messaging.message.OutboundMessage;
import com.opendesk.messaging.message.OutboundMessageRepository;
import com.opendesk.messaging.message.SendMessagePayload;
import com.opendesk.messaging.wallet.InsufficientCreditsException;
import com.opendesk.messaging.wallet.LotAllocation;
import com.opendesk.messaging.wallet.ReservationResult;
import com.opendesk.messaging.wallet.WalletReservationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opendesk.messaging.config.RabbitMqConfig.SEND_QUEUE;

/**
 * Campaign service — campaign lifecycle management.
 *
 * <p>Dispatch logic (QUEUED → SENDING → COMPLETED) is implemented here (04-05).
 * Scheduled dispatch job is in 04-08.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final WalletReservationClient walletReservationClient;
    private final ContactRecipientClient contactRecipientClient;
    private final RabbitTemplate rabbitTemplate;
    private final CarrierResolver carrierResolver;

    /**
     * Create a campaign in DRAFT state (MESG-01).
     * Recipients are NOT expanded here — expansion happens at dispatch time (04-05).
     *
     * <p>D-12: either groupIds OR contactIds must be provided (T-06-03-03).
     * Targeting-empty campaigns are rejected before persisting to prevent 0-recipient dispatches.
     */
    @Transactional
    public Campaign create(UUID userId, CreateCampaignRequest request) {
        // Guard: at least one targeting source must be present (T-06-03-03)
        boolean hasGroups = request.groupIds() != null && !request.groupIds().isEmpty();
        boolean hasContacts = request.contactIds() != null && !request.contactIds().isEmpty();
        if (!hasGroups && !hasContacts) {
            throw new IllegalStateException("At least one groupId or contactId is required");
        }

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
                .groupIds(request.groupIds() != null ? request.groupIds() : new java.util.HashSet<>())
                .contactIds(request.contactIds() != null ? request.contactIds() : new java.util.HashSet<>())
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

    /**
     * Build a CampaignResponse with aggregate message counts (MESG-06).
     * Counts are computed from outbound_messages grouped by status.
     */
    @Transactional(readOnly = true)
    public CampaignResponse toCampaignResponseWithCounts(Campaign campaign) {
        List<Object[]> statusCounts = outboundMessageRepository.countByStatusForCampaign(campaign.getId());
        int total = 0, sent = 0, delivered = 0, failed = 0;
        for (Object[] row : statusCounts) {
            MessageStatus status = (MessageStatus) row[0];
            int count = ((Number) row[1]).intValue();
            total += count;
            switch (status) {
                case SENT -> sent += count;
                case DELIVERED -> delivered += count;
                case FAILED -> failed += count;
                default -> {} // PENDING — not counted in terminal aggregates
            }
        }
        return CampaignResponse.from(campaign, total, sent, delivered, failed);
    }

    /**
     * Return per-message views for a campaign (MESG-07), IDOR-safe.
     * Returns empty list if campaignId is not found or belongs to another user.
     */
    @Transactional(readOnly = true)
    public List<MessageView> getMessages(UUID campaignId, UUID userId) {
        // IDOR check: ensure campaign belongs to the requesting user
        boolean owned = campaignRepository.findByIdAndUserId(campaignId, userId).isPresent();
        if (!owned) {
            return List.of();
        }
        return outboundMessageRepository.findByCampaignId(campaignId).stream()
                .map(MessageView::from)
                .toList();
    }

    /**
     * Cancel a SCHEDULED (or DRAFT) campaign (MESG-05).
     *
     * <p>IDOR-guarded: looks up by (id, userId) so a user cannot cancel another user's campaign.
     * Idempotent: if already CANCELLED, returns silently.
     * T-04-18: only SCHEDULED/DRAFT campaigns can be cancelled; QUEUED/SENDING are rejected.
     *
     * @param campaignId campaign to cancel
     * @param userId     owner from JWT subject
     * @throws IllegalStateException if campaign not found, not owned, or in non-cancellable state
     */
    @Transactional
    public Campaign cancel(UUID campaignId, UUID userId) {
        Campaign campaign = campaignRepository.findByIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new IllegalStateException("Campaign not found or not owned by user"));

        if (campaign.getStatus() == CampaignStatus.CANCELLED) {
            return campaign; // idempotent
        }

        if (campaign.getStatus() != CampaignStatus.SCHEDULED && campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot cancel campaign in state " + campaign.getStatus() + " — only SCHEDULED or DRAFT allowed");
        }

        campaign.setStatus(CampaignStatus.CANCELLED);
        campaign = campaignRepository.save(campaign);
        log.info("Campaign cancelled: id={} userId={}", campaignId, userId);
        return campaign;
    }

    /**
     * Dispatch an immediate send campaign (MESG-03, MESG-08, MESG-09).
     *
     * <p>Steps:
     * <ol>
     *   <li>Expand recipients from target groups via ContactRecipientClient (suppression already applied).</li>
     *   <li>Reserve credits synchronously from wallet (D-03). On 409 → InsufficientCreditsException,
     *       campaign stays in DRAFT, no QUEUED transition (T-04-10 / MESG-03).</li>
     *   <li>Zip recipients to lotIds using per-lot allocation (D-13 / D-14, Pitfall 4).</li>
     *   <li>Persist one OutboundMessage(PENDING) per recipient with its lotId.</li>
     *   <li>Transition campaign to QUEUED and record dispatchedAt.</li>
     *   <li>Publish one SendMessagePayload per recipient to messaging.send (attemptCount=0).</li>
     * </ol>
     *
     * @param campaign the campaign to dispatch (must be DRAFT or SCHEDULED)
     * @return dispatch result carrying recipient count and credits reserved
     * @throws InsufficientCreditsException if wallet returns 409 (propagated to controller → 402/409)
     */
    @Transactional
    public CampaignDispatchResponse executeSend(Campaign campaign) {
        UUID userId = campaign.getUserId();

        // Step 1: expand recipients (contact-service already applies suppression filter — D-14, MESG-09)
        // D-12: flat-contact path (contactIds non-empty) OR legacy group path (groupIds non-empty)
        List<String> recipients;
        if (campaign.getContactIds() != null && !campaign.getContactIds().isEmpty()) {
            recipients = contactRecipientClient.getRecipientsByContactIds(campaign.getContactIds(), userId);
        } else {
            recipients = contactRecipientClient.getRecipientsForGroups(campaign.getGroupIds(), userId);
        }
        int recipientCount = recipients.size();
        log.info("Campaign {} expanded to {} recipients after suppression filter", campaign.getId(), recipientCount);

        if (recipientCount == 0) {
            log.warn("Campaign {} has 0 recipients after suppression filter — aborting dispatch", campaign.getId());
            throw new IllegalStateException("No recipients after suppression filter");
        }

        // Step 2: reserve credits synchronously (D-03). Throws InsufficientCreditsException on 409.
        ReservationResult reservation = walletReservationClient.reserve(userId, recipientCount, campaign.getId());
        log.info("Reserved {} credits for campaign {} across {} lots",
                reservation.reservedCount(), campaign.getId(), reservation.allocations().size());

        // Step 3: zip recipients to lotIds using per-lot allocation (D-13)
        // Fill recipients from allocations in order — allocation[i].count recipients get allocation[i].lotId
        List<OutboundMessage> messages = new ArrayList<>(recipientCount);
        int recipientIdx = 0;
        for (LotAllocation allocation : reservation.allocations()) {
            for (int i = 0; i < allocation.count() && recipientIdx < recipientCount; i++, recipientIdx++) {
                String phone = recipients.get(recipientIdx);
                OutboundMessage message = OutboundMessage.builder()
                        .id(UUID.randomUUID())
                        .campaignId(campaign.getId())
                        .userId(userId)
                        .phoneE164(phone)
                        .lotId(allocation.lotId())
                        .status(MessageStatus.PENDING)
                        .operator(carrierResolver.resolve(phone))
                        .build();
                messages.add(message);
            }
        }

        // Step 4: persist outbound messages
        outboundMessageRepository.saveAll(messages);
        log.info("Persisted {} outbound messages for campaign {}", messages.size(), campaign.getId());

        // Step 5: transition campaign to QUEUED
        campaign.setStatus(CampaignStatus.QUEUED);
        campaign.setDispatchedAt(Instant.now());
        campaignRepository.save(campaign);

        // Step 6: publish one SendMessagePayload per recipient (attemptCount=0)
        for (OutboundMessage msg : messages) {
            SendMessagePayload payload = new SendMessagePayload(
                    msg.getId(),
                    campaign.getId(),
                    userId,
                    msg.getPhoneE164(),
                    campaign.getBody(),
                    campaign.getSenderId(),
                    msg.getLotId(),
                    0
            );
            rabbitTemplate.convertAndSend(SEND_QUEUE, payload);
        }
        log.info("Published {} AMQP messages to {} for campaign {}", messages.size(), SEND_QUEUE, campaign.getId());

        return new CampaignDispatchResponse(campaign.getId(), recipientCount, reservation.reservedCount());
    }
}
