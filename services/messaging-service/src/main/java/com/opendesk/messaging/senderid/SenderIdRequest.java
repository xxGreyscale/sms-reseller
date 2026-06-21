package com.opendesk.messaging.senderid;

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
 * Sender ID request — user submits a custom alphanumeric sender ID for admin approval.
 *
 * <p>State machine: REQUESTED → APPROVED | REJECTED
 * On decision, an outbox event {@code SenderIdDecided} is published to {@code messaging.events}
 * for Phase 5 notification-service consumption.
 */
@Entity
@Table(name = "sender_id_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderIdRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Requesting user — from JWT sub; never from request body. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Requested sender name (max 11 chars per GSM spec). */
    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SenderIdStatus status = SenderIdStatus.REQUESTED;

    /** Populated when admin rejects; explains reason for rejection. */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /** Set when admin approves or rejects. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
