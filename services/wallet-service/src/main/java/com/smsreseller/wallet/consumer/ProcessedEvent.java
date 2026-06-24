package com.smsreseller.wallet.consumer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for the {@code processed_events} table.
 *
 * <p>Rows in this table serve as idempotency guards for inbound AMQP consumers.
 * The table uses {@code event_id} as the primary key — inserting with
 * {@code ON CONFLICT DO NOTHING} is the atomic guard (T-03-08).
 *
 * <p>This entity is only ever inserted, never updated or deleted.
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 128)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
