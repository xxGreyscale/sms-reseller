package com.smsreseller.payment.application;

import com.smsreseller.payment.application.port.BundleRepositoryPort;
import com.smsreseller.payment.application.port.PaymentGateway;
import com.smsreseller.payment.application.port.PaymentRepositoryPort;
import com.smsreseller.payment.application.port.StkPushRequest;
import com.smsreseller.payment.domain.bundle.SmsBundle;
import com.smsreseller.payment.domain.payment.BundleNotPurchasableException;
import com.smsreseller.payment.domain.payment.Payment;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import com.smsreseller.payment.domain.payment.PendingPaymentExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Core payment flow service.
 *
 * <p>Implements:
 * <ul>
 *   <li>PYMT-02: Purchase initiation via {@link #initiate(UUID, com.smsreseller.payment.presentation.PurchaseRequest)}</li>
 *   <li>D-05/D-13: Single-pending enforcement — application-layer check + DB unique index backstop</li>
 *   <li>PYMT-05: Payment history via {@link #history(UUID, Pageable)}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepositoryPort paymentRepository;
    private final BundleRepositoryPort bundleRepository;
    private final PaymentGateway paymentGateway;

    /**
     * Initiates an Azampay STK push purchase.
     *
     * <p>Steps:
     * <ol>
     *   <li>Application-layer check: reject if user already has a PENDING payment (D-05)</li>
     *   <li>Load bundle — reject if not purchasable</li>
     *   <li>Create Payment entity (status=PENDING, externalId=payment UUID)</li>
     *   <li>Save — DB partial unique index {@code uq_payments_user_pending} backstops any race</li>
     *   <li>Call gateway.initiateStkPush (stub in dev, Azampay in prod)</li>
     * </ol>
     *
     * @throws PendingPaymentExistsException if a PENDING payment already exists for this user
     * @throws BundleNotPurchasableException if the bundle is not purchasable via Azampay
     * @throws jakarta.persistence.EntityNotFoundException if the bundle does not exist
     */
    @Transactional
    public Payment initiate(UUID userId, UUID bundleId, String msisdn, String provider) {
        // Application-layer check (primary guard)
        if (paymentRepository.existsByUserIdAndStatus(userId, PaymentStatus.PENDING)) {
            throw new PendingPaymentExistsException(userId.toString());
        }

        // Load and validate bundle
        SmsBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleId));

        if (!bundle.isPurchasable()) {
            throw new BundleNotPurchasableException(bundle.getId().toString());
        }

        // Create payment record
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .bundleId(bundle.getId())
                .amountTzs(bundle.getPriceTzs())
                .smsCount(bundle.getSmsCount())
                .status(PaymentStatus.PENDING)
                .externalId(paymentId.toString())  // Azampay idempotency key = our payment UUID
                .provider(provider)
                .build();

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            // DB partial unique index backstop: concurrent insert race caught here
            log.warn("PaymentService: concurrent PENDING payment detected for userId={}", userId);
            throw new PendingPaymentExistsException(userId.toString());
        }

        // Initiate STK push (stub returns immediately; Azampay is async)
        StkPushRequest stkPushRequest = new StkPushRequest(
                paymentId,
                msisdn,
                bundle.getPriceTzs(),
                provider
        );
        paymentGateway.initiateStkPush(stkPushRequest);

        log.info("PaymentService: initiated STK push paymentId={} userId={} bundleId={} amountTzs={}",
                paymentId, userId, bundle.getId(), bundle.getPriceTzs());
        return payment;
    }

    /**
     * Returns paginated payment history for a user, newest-first (PYMT-05).
     * Only returns payments for the authenticated user — userId is extracted from JWT (IDOR protection).
     */
    public Page<Payment> history(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Returns a single payment scoped to the authenticated user (D-11, MOBL-05).
     *
     * <p>Delegates to the compound {@code findByIdAndUserId} repository method so that
     * payments belonging to other users are indistinguishable from missing payments
     * (IDOR guard — T-06-02-01, T-06-02-02). The caller must return 404 on empty.
     *
     * @param id     payment UUID (from path — attacker-controlled)
     * @param userId user UUID from JWT subject (trusted)
     * @return the payment if it exists and belongs to the caller; empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<Payment> findByIdAndUser(UUID id, UUID userId) {
        return paymentRepository.findByIdAndUserId(id, userId);
    }
}
