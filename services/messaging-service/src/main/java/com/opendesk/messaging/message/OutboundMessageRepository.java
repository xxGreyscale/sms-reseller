package com.opendesk.messaging.message;

import com.opendesk.messaging.analytics.OperatorRateDto;
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

    /**
     * ANLX-01: count total messages for a campaign scoped to a user (IDOR-safe).
     */
    @Query("SELECT COUNT(m) FROM OutboundMessage m WHERE m.campaignId = :campaignId AND m.userId = :userId")
    long countByCampaignIdAndUserId(@Param("campaignId") UUID campaignId, @Param("userId") UUID userId);

    /**
     * ANLX-01: aggregate counts by status for a campaign scoped to userId (IDOR-safe).
     * Returns Object[] tuples of [status, count].
     */
    @Query("""
        SELECT m.status, COUNT(m) FROM OutboundMessage m
        WHERE m.campaignId = :campaignId AND m.userId = :userId
        GROUP BY m.status
    """)
    List<Object[]> countByStatusForCampaignAndUser(
            @Param("campaignId") UUID campaignId,
            @Param("userId") UUID userId);

    /**
     * ANLX-03: operator-level delivery rates grouped by (operator, status) for a user.
     * Returns OperatorRateDto records directly via constructor expression.
     */
    @Query("""
        SELECT new com.opendesk.messaging.analytics.OperatorRateDto(m.operator, CAST(m.status AS string), COUNT(m))
        FROM OutboundMessage m
        WHERE m.userId = :userId
        GROUP BY m.operator, m.status
        ORDER BY m.operator ASC, m.status ASC
    """)
    List<OperatorRateDto> findOperatorRatesByUser(@Param("userId") UUID userId);
}
