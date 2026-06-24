package com.smsreseller.identity.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link OutboxEntry} entities.
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /**
     * Find all unsent outbox rows, bounded by {@code pageable}.
     *
     * <p>Used by {@link OutboxRelay} to fetch a bounded batch of events to publish.
     * Pass {@code PageRequest.of(0, N)} to limit relay batch size.
     */
    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Find all unsent outbox rows (unbounded — used in tests only).
     *
     * <p>Production callers should use {@link #findBySentFalseOrderByCreatedAtAsc(Pageable)}.
     */
    List<OutboxEntry> findBySentFalse();
}
