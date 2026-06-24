package com.smsreseller.payment;

// Requirements: PYMT-04 + PYMT-06 + D-04 (late EXPIRED→SUCCESS)
//   PYMT-04 — Successful Azampay callback transitions PENDING → SUCCESS and emits PaymentConfirmed outbox
//   PYMT-06 — Duplicate callback (same externalId) is idempotent — exactly ONE PaymentConfirmed outbox row
//   D-04    — A success callback for an EXPIRED payment also succeeds (late-success credit idempotent)
// Covered by: 03-05 (CallbackController + CallbackProcessor + outbox idempotent guard)

import com.smsreseller.payment.domain.bundle.SmsBundle;
import com.smsreseller.payment.domain.payment.Payment;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import com.smsreseller.payment.infrastructure.persistence.BundleRepository;
import com.smsreseller.payment.infrastructure.persistence.OutboxRepository;
import com.smsreseller.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackProcessingIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BundleRepository bundleRepository;

    private UUID userId;
    private UUID bundleId;
    private int smsCount;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        userId = UUID.randomUUID();

        SmsBundle bundle = bundleRepository.findAll().stream()
                .filter(SmsBundle::isPurchasable)
                .findFirst()
                .orElseThrow();
        bundleId = bundle.getId();
        smsCount = bundle.getSmsCount();
    }

    private Payment createPayment(PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        Payment p = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(smsCount)
                .status(status)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        return paymentRepository.save(p);
    }

    private ResponseEntity<Map> postCallback(String externalId, String transactionStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "msisdn", "255712345678",
                "amount", "3200",
                "message", "Payment successful",
                "utilityRef", externalId,
                "operator", "MPESA",
                "reference", "REF-" + UUID.randomUUID(),
                "transactionStatus", transactionStatus,
                "submerchantAcc", ""
        );

        return restTemplate.postForEntity(
                "/api/v1/payments/callback",
                new HttpEntity<>(payload, headers),
                Map.class
        );
    }

    @Test
    void successCallbackTransitionsPendingToSuccessAndEmitsOutboxEvent() {
        // Given: PENDING payment
        Payment payment = createPayment(PaymentStatus.PENDING);

        // When: success callback
        ResponseEntity<Map> response = postCallback(payment.getExternalId(), "success");

        // Then: 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Payment status → SUCCESS
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Exactly one PaymentConfirmed outbox row written
        var outboxEntries = outboxRepository.findBySentFalse();
        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).getEventType()).isEqualTo("PaymentConfirmed");
        assertThat(outboxEntries.get(0).getPayload()).contains(userId.toString());
        assertThat(outboxEntries.get(0).getPayload()).contains(payment.getId().toString());
    }

    @Test
    void duplicateSuccessCallbackIsIdempotentSingleOutboxRow() {
        // Given: PENDING payment
        Payment payment = createPayment(PaymentStatus.PENDING);

        // When: success callback delivered twice
        postCallback(payment.getExternalId(), "success");
        postCallback(payment.getExternalId(), "success");

        // Then: payment is SUCCESS
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // CRITICAL: exactly ONE PaymentConfirmed outbox row — no double-credit (PYMT-06, T-03-11)
        var outboxEntries = outboxRepository.findAll().stream()
                .filter(e -> "PaymentConfirmed".equals(e.getEventType()))
                .toList();
        assertThat(outboxEntries).hasSize(1);
    }

    @Test
    void lateSuccessCallbackOnExpiredPaymentStillSucceeds() {
        // Given: EXPIRED payment (2-minute sweep already ran — D-04, Pitfall 5)
        Payment payment = createPayment(PaymentStatus.EXPIRED);

        // When: late success callback
        ResponseEntity<Map> response = postCallback(payment.getExternalId(), "success");

        // Then: 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // CRITICAL: EXPIRED → SUCCESS (D-04 — money left customer account; honor it)
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // And exactly one PaymentConfirmed outbox row
        var outboxEntries = outboxRepository.findBySentFalse();
        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).getEventType()).isEqualTo("PaymentConfirmed");
    }

    @Test
    void failCallbackTransitionsPendingToFailedNoOutboxRow() {
        // Given: PENDING payment
        Payment payment = createPayment(PaymentStatus.PENDING);

        // When: fail callback
        ResponseEntity<Map> response = postCallback(payment.getExternalId(), "fail");

        // Then: 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Payment status → FAILED
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // No outbox event for failed payment
        assertThat(outboxRepository.findBySentFalse()).isEmpty();
    }

    @Test
    void callbackForUnknownExternalIdIsIgnoredOrRejected() {
        // When: callback for a utilityRef that doesn't exist in our DB
        ResponseEntity<Map> response = postCallback(UUID.randomUUID().toString(), "success");

        // Then: 404 or 200 (either is acceptable — spoofed callback can't grant credits
        // because no matching payment row exists)
        assertThat(response.getStatusCode().value()).isIn(200, 404);

        // No outbox entry written
        assertThat(outboxRepository.findBySentFalse()).isEmpty();
    }
}
