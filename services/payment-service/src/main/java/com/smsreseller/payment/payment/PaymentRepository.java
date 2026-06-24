package com.smsreseller.payment.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Payment} — query seams for Plan 05 flow + Plan 05/06 reconciliation.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Looks up a payment by its Azampay idempotency key (= payment UUID string).
     * Used by callback processor and reconciliation job (Plan 05).
     */
    Optional<Payment> findByExternalId(String externalId);

    /**
     * Returns paginated payment history for a user, newest first (PYMT-history).
     */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Guards single-pending enforcement at the application layer (secondary check).
     * Primary enforcement is the DB partial unique index {@code uq_payments_user_pending} (D-13).
     */
    boolean existsByUserIdAndStatus(UUID userId, PaymentStatus status);

    /**
     * Returns stale PENDING or EXPIRED payments older than {@code cutoff} for reconciliation.
     * Used by {@code ReconciliationJob} (Plan 05) to poll Azampay for late confirmations.
     *
     * @param statuses  list of statuses to include (typically [PENDING, EXPIRED])
     * @param cutoff    created_at must be before this instant (e.g. now minus 5 minutes)
     * @param pageable  bounded by {@code PageRequest.of(0, maxPerRun)}
     */
    List<Payment> findByStatusInAndCreatedAtBefore(
            List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);

    /**
     * Owner-scoped single-payment lookup — compound (id, userId) WHERE clause.
     *
     * <p>Used by {@link PaymentService#findByIdAndUser(UUID, UUID)} to implement
     * GET /api/v1/payments/{id} (D-11, MOBL-05). The compound key ensures that
     * a payment belonging to another user is indistinguishable from a missing payment
     * (IDOR guard — T-06-02-02).
     *
     * @param id     payment UUID (path parameter — attacker-controlled)
     * @param userId user UUID from JWT subject (trusted)
     * @return the payment if it exists AND belongs to the caller; empty otherwise
     */
    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
}
