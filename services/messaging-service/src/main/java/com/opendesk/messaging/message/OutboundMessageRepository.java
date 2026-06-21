package com.opendesk.messaging.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link OutboundMessage} entities.
 */
public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {

    /** All messages for a campaign (MESG-07 per-message delivery status). */
    List<OutboundMessage> findByCampaignId(UUID campaignId);

    /** IDOR-safe lookup: find message by ID scoped to a specific user. */
    Optional<OutboundMessage> findByIdAndUserId(UUID id, UUID userId);

    /** Find by provider external ID — used by DeliveryReceiptService for DLR matching (D-12). */
    Optional<OutboundMessage> findByExternalId(String externalId);

    /**
     * Aggregate counts by status for a campaign (MESG-06 campaign status summary).
     * Returns Object[] tuples of [status, count].
     */
    @Query("SELECT m.status, COUNT(m) FROM OutboundMessage m WHERE m.campaignId = :campaignId GROUP BY m.status")
    List<Object[]> countByStatusForCampaign(@Param("campaignId") UUID campaignId);
}
