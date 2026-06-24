package com.smsreseller.messaging.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-recipient SMS message row.
 *
 * <p>Created by the send pipeline (04-05) when a campaign is dispatched.
 * One row per recipient — enables per-message retry, delivery tracking, and credit refund.
 *
 * <p>The {@link #lotId} carries the credit reservation correlation (D-13) from the wallet
 * reservation result. It travels with the AMQP payload so wallet's CONSUME/RELEASE/REFUND
 * events reference the correct credit lot without a secondary lookup.
 */
@Entity
@Table(name = "outbound_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    /** Owner — matches Campaign.userId; included for direct IDOR queries (MESG-07). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "phone_e164", nullable = false, updatable = false)
    private String phoneE164;

    /**
     * Credit lot ID reserved for this specific message (D-01 / D-13).
     * Set at dispatch time from wallet's ReservationResult.lotIds.
     * Carried in every AMQP event payload (MessageAccepted / MessageRefundDue / MessageReleased).
     */
    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    /** Provider reference returned on ACCEPTED. Used for DLR matching (D-12). */
    @Column(name = "external_id")
    private String externalId;

    /**
     * TZ mobile network operator derived from phoneE164 at dispatch time (D-13).
     * Values: "Vodacom", "Tigo", "Airtel", "Halotel", "UNKNOWN", or null for legacy rows.
     * Required for ANLX-03 GROUP BY operator analytics.
     */
    @Column(name = "operator")
    private String operator;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
