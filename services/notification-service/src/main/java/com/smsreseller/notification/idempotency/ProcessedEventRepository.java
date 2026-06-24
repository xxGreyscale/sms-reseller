package com.smsreseller.notification.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Idempotency gate for all notification-service AMQP consumers.
 *
 * <p>T-05-14: {@code tryInsert} uses {@code ON CONFLICT DO NOTHING} — atomically safe
 * under concurrent replay. Returns {@code true} only on first insert (new event).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(
        value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId);

    default boolean tryInsert(String eventId) {
        return insertIfAbsent(eventId) == 1;
    }
}
