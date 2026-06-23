package com.smsreseller.wallet.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the idempotency guard table {@code processed_events}.
 *
 * <p>The core operation is {@link #tryInsert(String)}: atomically insert a new event_id
 * row, returning {@code true} if the insert succeeded (first delivery) or {@code false}
 * if the row already exists (duplicate delivery — ON CONFLICT DO NOTHING skips the row).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Atomically inserts an event_id into {@code processed_events}.
     *
     * <p>Uses PostgreSQL {@code INSERT ... ON CONFLICT DO NOTHING}. Returns the number
     * of rows actually inserted (1 = first delivery, 0 = duplicate).
     *
     * @param eventId the event identifier to guard (max 128 chars)
     * @return 1 if this is the first time the event was processed, 0 on duplicate
     */
    @Modifying
    @Query(
        value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId);

    /**
     * Convenience wrapper: returns {@code true} if the event was inserted (first delivery).
     *
     * @param eventId the event identifier to guard
     * @return true if the event is new (should be processed); false if it was already seen
     */
    default boolean tryInsert(String eventId) {
        return insertIfAbsent(eventId) == 1;
    }
}
