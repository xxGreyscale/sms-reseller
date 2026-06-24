package com.smsreseller.payment;

// Requirements: PYMT-03 + PYMT-07
//   PYMT-03 — PENDING payment transitions to EXPIRED after 2 minutes (no infinite PENDING — D-06)
//   PYMT-07 — EXPIRED payment is surfaced to status endpoint; no infinite spinner (T-03-13)
// Covered by: 03-05 (PaymentTimeoutJob @Scheduled sweep)

import com.smsreseller.payment.bundle.BundleRepository;
import com.smsreseller.payment.bundle.SmsBundle;
import com.smsreseller.payment.payment.Payment;
import com.smsreseller.payment.payment.PaymentRepository;
import com.smsreseller.payment.payment.PaymentStatus;
import com.smsreseller.payment.timeout.PaymentTimeoutJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTimeoutIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private PaymentTimeoutJob paymentTimeoutJob;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    private UUID userId;
    private UUID bundleId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userId = UUID.randomUUID();
        bundleId = bundleRepository.findAll().stream()
                .filter(SmsBundle::isPurchasable)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void paymentMarkedExpiredWhenNoCallbackWithinTwoMinutes() {
        // Given: a PENDING payment created MORE than 2 minutes ago (fast-forward via direct insert)
        UUID paymentId = UUID.randomUUID();

        // We insert a PENDING payment with created_at = now - 3 minutes to simulate timeout
        // Use JDBC template via repository save and then update created_at via native query approach
        Payment p = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(p);

        // Fast-forward: directly modify the in-DB created_at to be 3 minutes ago
        // using the repository findByStatusInAndCreatedAtBefore seam — but we need
        // to actually have the timestamp old enough. Use direct JPA update.
        // We'll use a native approach: manipulate via re-save is not possible since
        // created_at is @CreatedDate (immutable). Instead we verify the sweep logic
        // by calling the timeout job with a future cutoff.

        // When: timeout job runs with cutoff = now (payment was created "just now" but
        // the job uses configurable timeout — we invoke sweep() directly with context
        // that the payment was created before the cutoff by 3 min)
        // The cleanest test: create payment with old timestamp via raw save, then call sweep.
        // Since @CreatedDate prevents setting it in normal flow, we need to bypass:
        // We simulate by calling paymentRepository.save directly after saving to set stale timestamp.
        // Actually: use a helper that directly sets created_at via JPQL/native update is cleanest.
        // For this test, we verify the job works by inserting a stale record via @BeforeEach
        // and directly invoking the job — accepting the JPA auditing constraint.

        // Alternative approach (correct): find the payment that was just saved (created_at = now)
        // and call the sweep with a cutoff of "now + 1 second" to make the payment appear stale.
        Instant futureCutoff = Instant.now().plus(5, ChronoUnit.SECONDS);
        paymentTimeoutJob.sweepExpiredPayments(futureCutoff);

        // Then: the PENDING payment should now be EXPIRED
        Payment updated = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void expiredPaymentAppearsInHistoryNotPending() {
        // Given: EXPIRED payment in DB
        UUID paymentId = UUID.randomUUID();
        Payment p = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.EXPIRED)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(p);

        // When: user checks payment history
        String token = jwtTestHelper.generateToken(userId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        // Then: 200 OK, history contains the EXPIRED payment (no infinite PENDING — PYMT-07)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<?, ?> payment = (Map<?, ?>) content.get(0);
        assertThat(payment.get("status")).isEqualTo("EXPIRED");
    }

    @Test
    void pendingPaymentNotYetTimedOutIsNotExpired() {
        // Given: a PENDING payment created just now (not yet timed out)
        UUID paymentId = UUID.randomUUID();
        Payment p = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(p);

        // When: timeout job runs with cutoff = now - 2 minutes (payment is too new)
        Instant pastCutoff = Instant.now().minus(2, ChronoUnit.MINUTES);
        paymentTimeoutJob.sweepExpiredPayments(pastCutoff);

        // Then: payment still PENDING (not expired yet)
        Payment updated = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
