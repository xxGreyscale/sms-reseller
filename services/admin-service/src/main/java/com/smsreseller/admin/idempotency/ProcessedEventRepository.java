package com.smsreseller.admin.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Idempotency gate for admin-service AMQP consumers (T-05-18).
 *
 * <p>Uses {@code INSERT ... ON CONFLICT DO NOTHING} to atomically claim an eventId.
 * Returns {@code true} if the event is new (first delivery), {@code false} on replay.
 *
 * <p>Pattern: verbatim copy of wallet-service ProcessedEventRepository, package changed.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(
        value = "INSERT INTO admin.processed_events (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId);

    /**
     * Atomically claims the given eventId.
     *
     * @param eventId unique event identifier (from the payload)
     * @return {@code true} if this is the first delivery; {@code false} if already processed
     */
    default boolean tryInsert(String eventId) {
        return insertIfAbsent(eventId) == 1;
    }
}
