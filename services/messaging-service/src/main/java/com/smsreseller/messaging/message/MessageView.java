package com.smsreseller.messaging.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view DTO for a single outbound message (MESG-07 per-message status).
 *
 * <p>Returned by GET /api/v1/campaigns/{id}/messages and the POST /api/v1/messaging/dlr stub.
 */
public record MessageView(
        UUID id,
        UUID campaignId,
        String phoneE164,
        String status,
        String externalId,
        Instant createdAt,
        Instant updatedAt
) {
    public static MessageView from(OutboundMessage message) {
        return new MessageView(
                message.getId(),
                message.getCampaignId(),
                message.getPhoneE164(),
                message.getStatus().name(),
                message.getExternalId(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
