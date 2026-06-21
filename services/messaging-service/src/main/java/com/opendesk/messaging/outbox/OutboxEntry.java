package com.opendesk.messaging.outbox;

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
 * Transactional outbox row for domain events published to the {@code messaging.events} exchange.
 *
 * <p>Mirrors identity-service OutboxEntry verbatim (Pattern 4 — transactional outbox).
 * Events are written atomically alongside the triggering domain state change.
 * {@link OutboxRelay} polls and publishes at-least-once. Consumers deduplicate by {@link #eventId}.
 *
 * <p>Currently used for:
 * <ul>
 *   <li>{@code SenderIdDecided} — routing key {@code messaging.SenderIdDecided}</li>
 * </ul>
 * Plan 04-05/06 will add MessageAccepted / MessageReleased / MessageRefundDue events.
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

    /** Globally unique event identifier — consumers use for at-least-once deduplication. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    /** Aggregate type (e.g. "SenderIdRequest", "OutboundMessage"). */
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    /** Aggregate root ID (string form of UUID). */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    /** Event type name (e.g. "SenderIdDecided"). Used to build routing key. */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** JSON-serialized event payload. */
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
