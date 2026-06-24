package com.smsreseller.payment.infrastructure.persistence;

import com.smsreseller.payment.application.port.OutboxRepositoryPort;
import com.smsreseller.payment.domain.outbox.OutboxEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of the {@link OutboxRepositoryPort} output port.
 */
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID>, OutboxRepositoryPort {

    @Override
    OutboxEntry save(OutboxEntry entry);

    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    List<OutboxEntry> findBySentFalse();
}
