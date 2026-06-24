package com.smsreseller.messaging.message;

// Wave 0 RED placeholder — made GREEN by plan 05-02 (upstream gap fix: emit CampaignCompleted outbox event)
// Requirement: NOTF-05 prerequisite — CampaignCompleted event emitted by messaging-service

import com.smsreseller.messaging.AbstractMessagingIntegrationTest;
import com.smsreseller.messaging.JwtTestHelper;
import com.smsreseller.messaging.campaign.Campaign;
import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignStatus;
import com.smsreseller.messaging.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED→GREEN: verifies that when all messages in a campaign reach terminal state,
 * DeliveryReceiptService emits a CampaignCompleted outbox row to messaging.events.
 *
 * <p>This tests the upstream gap fix (D-12): Phase 4 set campaign to COMPLETED in DB
 * but did NOT emit an outbox event. Plan 05-02 adds the outbox emit.
 */
class CampaignCompletedIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private DeliveryReceiptService deliveryReceiptService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void campaignCompletionEmitsCampaignCompletedOutboxEvent() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // Create campaign
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .userId(userId)
                .senderId("TEST")
                .name("Test Campaign").body("Hello TZ")
                .status(CampaignStatus.SENDING)
                .build();
        campaignRepository.save(campaign);

        // Create two outbound messages — both start SENT, then one DLR each
        String externalId1 = "ext-" + UUID.randomUUID();
        String externalId2 = "ext-" + UUID.randomUUID();
        OutboundMessage msg1 = OutboundMessage.builder()
                .id(UUID.randomUUID())
                .campaignId(campaignId)
                .userId(userId)
                .phoneE164("+255740000001")
                .lotId(UUID.randomUUID())
                .status(MessageStatus.SENT)
                .externalId(externalId1)
                .build();
        OutboundMessage msg2 = OutboundMessage.builder()
                .id(UUID.randomUUID())
                .campaignId(campaignId)
                .userId(userId)
                .phoneE164("+255710000002")
                .lotId(UUID.randomUUID())
                .status(MessageStatus.SENT)
                .externalId(externalId2)
                .build();
        outboundMessageRepository.saveAll(List.of(msg1, msg2));

        // Deliver first — campaign still SENDING (not all terminal)
        deliveryReceiptService.handleDeliveryReceipt(externalId1, "DELIVERED");
        assertThat(campaignRepository.findById(campaignId).get().getStatus())
                .isEqualTo(CampaignStatus.SENDING);
        // Filter by this campaign's ID to avoid cross-test pollution (shared DB context)
        long outboxCountAfterFirst = outboxRepository.findByEventType("CampaignCompleted").stream()
                .filter(e -> e.getAggregateId().equals(campaignId.toString())).count();
        assertThat(outboxCountAfterFirst).isZero();

        // Deliver second — campaign becomes COMPLETED; outbox row must be written
        deliveryReceiptService.handleDeliveryReceipt(externalId2, "DELIVERED");
        assertThat(campaignRepository.findById(campaignId).get().getStatus())
                .isEqualTo(CampaignStatus.COMPLETED);

        var completedOutbox = outboxRepository.findByEventType("CampaignCompleted").stream()
                .filter(e -> e.getAggregateId().equals(campaignId.toString())).toList();
        assertThat(completedOutbox).hasSize(1);
        var outboxEntry = completedOutbox.get(0);
        assertThat(outboxEntry.getAggregateType()).isEqualTo("Campaign");
        assertThat(outboxEntry.getAggregateId()).isEqualTo(campaignId.toString());
        assertThat(outboxEntry.getPayload()).contains("campaignId");
        assertThat(outboxEntry.getPayload()).contains("deliveredCount");
        assertThat(outboxEntry.getPayload()).contains("failedCount");
        assertThat(outboxEntry.getPayload()).contains("totalCount");
    }

    @Test
    void duplicateCompletionDoesNotEmitSecondOutboxEvent() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .userId(userId)
                .senderId("TEST")
                .name("Dupe Campaign").body("Dupe guard")
                .status(CampaignStatus.SENDING)
                .build();
        campaignRepository.save(campaign);

        String externalId = "ext-dupe-" + UUID.randomUUID();
        OutboundMessage msg = OutboundMessage.builder()
                .id(UUID.randomUUID())
                .campaignId(campaignId)
                .userId(userId)
                .phoneE164("+255740000099")
                .lotId(UUID.randomUUID())
                .status(MessageStatus.SENT)
                .externalId(externalId)
                .build();
        outboundMessageRepository.save(msg);

        // First DLR — campaign transitions COMPLETED + outbox emitted
        deliveryReceiptService.handleDeliveryReceipt(externalId, "DELIVERED");
        assertThat(outboxRepository.findByEventType("CampaignCompleted")
                .stream().filter(e -> e.getAggregateId().equals(campaignId.toString())).count())
                .isEqualTo(1);

        // Second DLR for same externalId — handleDeliveryReceipt no-ops (message not SENT)
        // The campaign is already COMPLETED — no second outbox row
        deliveryReceiptService.handleDeliveryReceipt(externalId, "DELIVERED");
        assertThat(outboxRepository.findByEventType("CampaignCompleted")
                .stream().filter(e -> e.getAggregateId().equals(campaignId.toString())).count())
                .isEqualTo(1);
    }
}
