package com.smsreseller.payment.application.port;

import com.smsreseller.payment.domain.payment.Payment;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: repository contract for {@link Payment}.
 *
 * <p>Defined in the application layer (port) so that use-case services depend on the port,
 * not the infrastructure JPA implementation. This satisfies the inward dependency rule.
 */
public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByExternalId(String externalId);

    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndStatus(UUID userId, PaymentStatus status);

    List<Payment> findByStatusInAndCreatedAtBefore(List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);

    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);

    void deleteAll();
}
