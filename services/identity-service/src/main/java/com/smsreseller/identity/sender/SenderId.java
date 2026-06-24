package com.smsreseller.identity.sender;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-internal numeric sender ID assigned to a user on NIDA verification (SNDR-01).
 *
 * <p>The {@link #senderId} is a 6-digit zero-padded numeric shortcode unique per user.
 * This is a placeholder until TCRA provisions real alphanumeric sender IDs (Phase 4, SNDR-02).
 *
 * <p>Uniqueness is enforced both at the DB level (unique constraint on sender_id) and in
 * {@link SenderIdService} which retries on collision during generation.
 */
@Entity
@Table(name = "sender_ids")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderId {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to users.id — one sender ID per user. */
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    /**
     * 6-digit zero-padded numeric shortcode, e.g. "042137".
     * Placeholder until TCRA provisioning (SNDR-02, Phase 4).
     */
    @Column(name = "sender_id", nullable = false, unique = true, updatable = false)
    private String senderId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
