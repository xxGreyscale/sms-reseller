package com.opendesk.payment;

// Requirement: PYMT-03 (timeout) + PYMT-04 (late SUCCESS from Azampay after EXPIRED transition, D-04)
// ReconciliationJob polls stale PENDING/EXPIRED payments and drives the idempotent late-success path.

import com.opendesk.payment.outbox.OutboxRepository;
import com.opendesk.payment.payment.Payment;
import com.opendesk.payment.payment.PaymentRepository;
import com.opendesk.payment.payment.PaymentStatus;
import com.opendesk.payment.reconciliation.ReconciliationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the ReconciliationJob (D-04, PYMT-04):
 * - An EXPIRED payment that Azampay (stub) reports as success is flipped to SUCCESS
 *   and emits exactly one PaymentConfirmed outbox event (idempotent, T-03-18).
 * - A still-pending payment that stub returns as pending is unchanged.
 *
 * <p>TDD RED: fails before ReconciliationJob is created.
 */
class ReconciliationIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private ReconciliationJob reconciliationJob;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanData() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    /**
     * An EXPIRED payment whose externalId resolves to SUCCESS via the stub gateway
     * is flipped to SUCCESS and emits exactly one PaymentConfirmed outbox event (D-04, T-03-18).
     */
    @Test
    void reconciliationJobFlipsExpiredPaymentToSuccessAndEmitsPaymentConfirmed() {
        UUID userId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        // externalId with default outcome (SUCCESS) — not ending in 0001 or 0002
        String externalId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.EXPIRED) // timed out but Azampay will say SUCCESS
                .externalId(externalId)
                .provider("MPESA")
                .build();
        paymentRepository.save(payment);

        // Trigger reconciliation with a cutoff in the future (so the payment is "old enough")
        reconciliationJob.reconcile(Instant.now().plus(1, ChronoUnit.HOURS));

        // Payment flipped to SUCCESS
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Exactly one PaymentConfirmed outbox event emitted
        long confirmedCount = outboxRepository.findBySentFalse().stream()
                .filter(e -> "PaymentConfirmed".equals(e.getEventType()))
                .count();
        assertThat(confirmedCount).isEqualTo(1);
    }

    /**
     * Running reconciliation twice for the same EXPIRED→SUCCESS payment must emit only
     * one PaymentConfirmed outbox event (idempotent, T-03-18).
     */
    @Test
    void reconciliationJobIsIdempotentForAlreadySuccessPayment() {
        UUID userId = UUID.randomUUID();
        String externalId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .bundleId(UUID.randomUUID())
                .amountTzs(14500L)
                .smsCount(1000)
                .status(PaymentStatus.EXPIRED)
                .externalId(externalId)
                .provider("TIGOPESA")
                .build();
        paymentRepository.save(payment);

        // First reconciliation run — flips to SUCCESS
        reconciliationJob.reconcile(Instant.now().plus(1, ChronoUnit.HOURS));

        // Second reconciliation run — payment is now SUCCESS; should be skipped (already not PENDING/EXPIRED)
        reconciliationJob.reconcile(Instant.now().plus(1, ChronoUnit.HOURS));

        // Still only one PaymentConfirmed outbox event
        long confirmedCount = outboxRepository.findBySentFalse().stream()
                .filter(e -> "PaymentConfirmed".equals(e.getEventType()))
                .count();
        assertThat(confirmedCount).isEqualTo(1);
    }

    /**
     * A PENDING payment that the stub reports as still PENDING (externalId ends in 0002 = TIMEOUT)
     * remains PENDING after reconciliation and no outbox event is emitted.
     */
    @Test
    void reconciliationJobLeavesPendingStubPaymentUnchanged() {
        UUID userId = UUID.randomUUID();
        // externalId ending in 0002 → StubPaymentGateway returns PENDING (TIMEOUT suffix)
        String externalId = UUID.randomUUID() + "0002";

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .bundleId(UUID.randomUUID())
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(externalId)
                .provider("AIRTELMONEY")
                .build();
        paymentRepository.save(payment);

        reconciliationJob.reconcile(Instant.now().plus(1, ChronoUnit.HOURS));

        Payment unchanged = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.PENDING);

        long confirmedCount = outboxRepository.findBySentFalse().stream()
                .filter(e -> "PaymentConfirmed".equals(e.getEventType()))
                .count();
        assertThat(confirmedCount).isZero();
    }
}
