package com.smsreseller.payment.infrastructure.persistence;

import com.smsreseller.payment.application.port.PaymentRepositoryPort;
import com.smsreseller.payment.domain.payment.Payment;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of the {@link PaymentRepositoryPort} output port.
 *
 * <p>Application services depend on {@link PaymentRepositoryPort} (application layer);
 * Spring injects this JPA implementation at runtime (ARCH-01 inward dependency rule).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepositoryPort {

    @Override
    Payment save(Payment payment);

    @Override
    Optional<Payment> findById(UUID id);

    Optional<Payment> findByExternalId(String externalId);

    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndStatus(UUID userId, PaymentStatus status);

    List<Payment> findByStatusInAndCreatedAtBefore(List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);

    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
}
