package com.smsreseller.payment.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link OutboxEntry}.
 *
 * <p>Copied verbatim from identity-service OutboxRepository (03-PATTERNS.md lines 107-121).
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    /** Used in tests only — returns all unsent entries without pagination. */
    List<OutboxEntry> findBySentFalse();
}
