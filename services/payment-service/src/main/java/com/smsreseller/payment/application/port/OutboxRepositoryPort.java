package com.smsreseller.payment.application.port;

import com.smsreseller.payment.domain.outbox.OutboxEntry;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Output port: repository contract for {@link OutboxEntry}.
 *
 * <p>Defined in the application layer (port) so that use-case services depend on the port,
 * not the infrastructure JPA implementation. Satisfies the inward dependency rule (ARCH-01).
 */
public interface OutboxRepositoryPort {

    OutboxEntry save(OutboxEntry entry);

    void deleteAll();

    List<OutboxEntry> findAll();

    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    List<OutboxEntry> findBySentFalse();
}
