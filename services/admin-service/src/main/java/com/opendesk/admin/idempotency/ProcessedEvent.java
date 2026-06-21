package com.opendesk.admin.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency guard entity for admin-service AMQP consumers.
 *
 * <p>Each successfully processed domain event has its {@code eventId} recorded here.
 * A duplicate delivery is detected by {@link ProcessedEventRepository#tryInsert} —
 * if the insert is a no-op (ON CONFLICT DO NOTHING), the consumer returns early.
 *
 * <p>Pattern: verbatim copy of wallet-service ProcessedEvent, package changed.
 */
@Entity
@Table(schema = "admin", name = "processed_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
