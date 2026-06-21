package com.opendesk.messaging.campaign;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Campaign aggregate — represents a bulk SMS send targeting one or more contact groups.
 *
 * <p>Lifecycle: DRAFT → SCHEDULED | QUEUED → SENDING → COMPLETED | CANCELLED
 * Recipients are expanded at dispatch time (04-05) from the referenced groupIds.
 * Each recipient becomes one {@code OutboundMessage} row with its own credit lot correlation.
 */
@Entity
@Table(name = "campaigns")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Owner — from JWT sub; never from request body (IDOR guard). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name")
    private String name;

    /** SMS body text. Length validated against SmsEncoder limits before dispatch. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Alphanumeric sender ID (max 11 chars per GSM spec, validated by sender_id_requests). */
    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    /** Null for immediately-dispatched campaigns. Set for scheduled campaigns (MESG-04). */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Set when the dispatcher begins fan-out (status transitions to QUEUED). */
    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    /**
     * Contact group IDs to expand into recipients at dispatch time.
     * Stored in {@code campaign_groups} join table. Group membership is resolved
     * at dispatch against the contact-service (no FK — cross-service reference).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_groups", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "group_id")
    @Builder.Default
    private Set<UUID> groupIds = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
