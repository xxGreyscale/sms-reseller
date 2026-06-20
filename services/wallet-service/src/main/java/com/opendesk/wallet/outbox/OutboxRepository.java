package com.opendesk.wallet.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for wallet-service transactional outbox entries.
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    /** Used by OutboxRelay for bounded-batch relay. */
    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    /** Used in tests to assert outbox state. */
    List<OutboxEntry> findBySentFalse();
}
