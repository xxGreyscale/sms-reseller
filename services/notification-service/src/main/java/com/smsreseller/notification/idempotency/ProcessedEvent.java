package com.smsreseller.notification.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for the {@code processed_events} table in the notification schema.
 *
 * <p>Rows serve as idempotency guards for all inbound AMQP consumers.
 * T-05-14: INSERT ON CONFLICT DO NOTHING atomically prevents duplicate notification creation.
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
