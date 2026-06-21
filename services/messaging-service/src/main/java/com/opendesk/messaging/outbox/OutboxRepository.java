package com.opendesk.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link OutboxEntry} entities.
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /** Bounded batch of unsent rows ordered by creation time — used by {@link OutboxRelay}. */
    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    /** All unsent rows — for tests only (unbounded). */
    List<OutboxEntry> findBySentFalse();
}
