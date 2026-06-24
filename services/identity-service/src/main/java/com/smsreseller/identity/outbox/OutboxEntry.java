package com.smsreseller.identity.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row for domain events that must be published to RabbitMQ (Pattern 4).
 *
 * <p>Events are written atomically alongside the triggering domain state change in the same
 * Postgres transaction. The {@link OutboxRelay} scheduler polls unsent rows and publishes them
 * to the RabbitMQ topic exchange at-least-once. Consumers MUST deduplicate by {@link #eventId}
 * (Pitfall 5 from 02-RESEARCH.md).
 *
 * <p>Currently used for:
 * <ul>
 *   <li>{@code UserVerified} — grants 50 free credits via Phase 3 wallet service (IDEN-03)</li>
 * </ul>
 */
@Entity
@Table(name = "outbox")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Globally unique event identifier.
     * Consumers use this to deduplicate at-least-once deliveries.
     */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    /** Aggregate type (e.g. "User"). */
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    /** Aggregate root ID (string form of the UUID). */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    /** Event type name (e.g. "UserVerified"). */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /**
     * JSON-serialized event payload.
     * For {@code UserVerified}: {@code {"eventId":"...","userId":"...","freeCredits":50}}
     */
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    /** Whether this row has been published to RabbitMQ. */
    @Column(name = "sent", nullable = false)
    @Builder.Default
    private boolean sent = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Timestamp when the relay published this row. Null until sent. */
    @Column(name = "sent_at")
    private Instant sentAt;
}
