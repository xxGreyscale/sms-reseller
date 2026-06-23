package com.smsreseller.messaging.message;

import com.smsreseller.messaging.campaign.Campaign;
import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignStatus;
import com.smsreseller.messaging.outbox.OutboxEntry;
import com.smsreseller.messaging.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles delivery receipt (DLR) callbacks — transitions {@link OutboundMessage} status
 * from {@link MessageStatus#SENT} to {@link MessageStatus#DELIVERED} or {@link MessageStatus#FAILED}.
 *
 * <p>There are two call sites:
 * <ol>
 *   <li>{@link com.smsreseller.messaging.sms.StubSmsProvider} — {@code @Scheduled} DLR sweep
 *       (100ms delay in test profile; 10s in dev). Wired via
 *       {@link com.smsreseller.messaging.sms.StubSmsProvider#setDeliveryReceiptHandler}.</li>
 *   <li>POST {@code /api/v1/messaging/dlr} webhook (future real provider) — delegates to
 *       {@link #handleDeliveryReceipt(String, String)} via the DLR controller.</li>
 * </ol>
 *
 * <p>After updating per-message status, this service checks whether all messages in the
 * campaign have reached a terminal state (DELIVERED or FAILED). If so, the campaign is
 * advanced to {@link CampaignStatus#COMPLETED} and a {@code CampaignCompleted} outbox event
 * is emitted atomically in the same transaction (D-12 gap fix — NOTF-05 prerequisite).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryReceiptService {

    private final OutboundMessageRepository outboundMessageRepository;
    private final CampaignRepository campaignRepository;
    private final OutboxRepository outboxRepository;

    /**
     * Process a delivery receipt for a message identified by its provider external ID.
     *
     * @param externalId the provider reference (from {@code SmsResult.externalId()})
     * @param dlrStatus  "DELIVERED" or "FAILED" (matches the stub's output; real DLR adapts to this)
     */
    @Transactional
    public void handleDeliveryReceipt(String externalId, String dlrStatus) {
        OutboundMessage message = outboundMessageRepository.findByExternalId(externalId).orElse(null);
        if (message == null) {
            log.warn("DeliveryReceiptService: no OutboundMessage found for externalId={} — discarding DLR", externalId);
            return;
        }

        if (message.getStatus() != MessageStatus.SENT) {
            log.debug("DeliveryReceiptService: messageId={} status={} — ignoring DLR (not SENT)",
                    message.getId(), message.getStatus());
            return;
        }

        MessageStatus newStatus = "DELIVERED".equalsIgnoreCase(dlrStatus)
                ? MessageStatus.DELIVERED
                : MessageStatus.FAILED;

        message.setStatus(newStatus);
        outboundMessageRepository.save(message);
        log.info("DeliveryReceiptService: messageId={} externalId={} → {}", message.getId(), externalId, newStatus);

        // Check if the entire campaign has reached terminal state
        checkCampaignCompletion(message.getCampaignId());
    }

    /**
     * If all messages in the campaign are in a terminal state (DELIVERED or FAILED),
     * advance the campaign to COMPLETED and emit a CampaignCompleted outbox event
     * in the same transaction (D-12 gap fix).
     *
     * <p>The emit is guarded by the {@code status != COMPLETED} check so that a late
     * duplicate DLR never produces a second CampaignCompleted row (T-05-03).
     */
    private void checkCampaignCompletion(UUID campaignId) {
        List<OutboundMessage> messages = outboundMessageRepository.findByCampaignId(campaignId);
        if (messages.isEmpty()) {
            return;
        }

        boolean allTerminal = messages.stream().allMatch(m ->
                m.getStatus() == MessageStatus.DELIVERED || m.getStatus() == MessageStatus.FAILED);

        if (allTerminal) {
            campaignRepository.findById(campaignId).ifPresent(campaign -> {
                if (campaign.getStatus() != CampaignStatus.COMPLETED) {
                    campaign.setStatus(CampaignStatus.COMPLETED);
                    campaignRepository.save(campaign);
                    log.info("Campaign {} set to COMPLETED — all {} messages terminal", campaignId, messages.size());

                    // D-12 gap fix: emit CampaignCompleted outbox event in same transaction
                    emitCampaignCompleted(campaign, messages);
                }
            });
        }
    }

    /**
     * Builds and persists the CampaignCompleted outbox row.
     * OutboxRelay @Scheduled polls and publishes to messaging.events/messaging.CampaignCompleted.
     */
    private void emitCampaignCompleted(Campaign campaign, List<OutboundMessage> messages) {
        long delivered = messages.stream().filter(m -> m.getStatus() == MessageStatus.DELIVERED).count();
        long failed = messages.stream().filter(m -> m.getStatus() == MessageStatus.FAILED).count();

        UUID eventId = UUID.randomUUID();
        String payload = buildCampaignCompletedPayload(
                eventId, campaign.getId(), campaign.getUserId(),
                messages.size(), (int) delivered, (int) failed);

        OutboxEntry outbox = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .aggregateType("Campaign")
                .aggregateId(campaign.getId().toString())
                .eventType("CampaignCompleted")
                .payload(payload)
                .build();

        outboxRepository.save(outbox);
        log.info("CampaignCompleted outbox event emitted: campaignId={} delivered={} failed={}",
                campaign.getId(), delivered, failed);
    }

    private String buildCampaignCompletedPayload(
            UUID eventId, UUID campaignId, UUID userId,
            int totalCount, int deliveredCount, int failedCount) {
        // Simple JSON construction — avoids Jackson ObjectMapper dependency in this service
        return String.format(
                "{\"eventId\":\"%s\",\"campaignId\":\"%s\",\"userId\":\"%s\","
                + "\"totalCount\":%d,\"deliveredCount\":%d,\"failedCount\":%d}",
                eventId, campaignId, userId, totalCount, deliveredCount, failedCount);
    }
}
