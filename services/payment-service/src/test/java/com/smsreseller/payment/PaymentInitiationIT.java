package com.smsreseller.payment;

// Requirements: PYMT-02, D-05/D-13 (single-pending), PYMT-03 (countdown contract)
// Covered by: 03-05 (PaymentService + PaymentController + StubPaymentGateway)

import com.smsreseller.payment.domain.bundle.SmsBundle;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import com.smsreseller.payment.infrastructure.persistence.BundleRepository;
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

class PaymentInitiationIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    private UUID userId;
    private String token;
    private UUID starterId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userId = UUID.randomUUID();
        token = jwtTestHelper.generateToken(userId.toString());

        // find the Starter bundle (purchasable, price 3200)
        starterId = bundleRepository.findAll().stream()
                .filter(SmsBundle::isPurchasable)
                .filter(b -> b.getSmsCount() == 200)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Starter bundle not found in seed data"))
                .getId();
    }

    @Test
    void initiatePaymentCreatesPendingRecordAndTriggersGateway() {
        // Given: a valid purchase request for Starter bundle
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "bundleId", starterId.toString(),
                "msisdn", "255712345678",
                "provider", "MPESA"
        );

        // When: POST /api/v1/payments
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // Then: 200 or 201, payment created with PENDING status
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
        assertThat(response.getBody().get("paymentId")).isNotNull();
        assertThat(response.getBody().get("timeoutSeconds")).isEqualTo(120);

        // Verify payment in DB
        var payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(payments.getTotalElements()).isEqualTo(1);
        var payment = payments.getContent().get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmountTzs()).isEqualTo(3200L);
        assertThat(payment.getSmsCount()).isEqualTo(200);
        assertThat(payment.getExternalId()).isEqualTo(payment.getId().toString());
    }

    @Test
    void secondConcurrentPaymentInitiationRejectedForSameUser() {
        // Given: first payment is PENDING
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "bundleId", starterId.toString(),
                "msisdn", "255712345678",
                "provider", "MPESA"
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // First payment succeeds
        ResponseEntity<Map> first = restTemplate.postForEntity("/api/v1/payments", entity, Map.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();

        // When: second payment for same user while PENDING exists
        ResponseEntity<Map> second = restTemplate.postForEntity("/api/v1/payments", entity, Map.class);

        // Then: 409 Conflict
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void purchasingTasterBundleIsRejected() {
        // Given: Taster bundle (not purchasable, price 0)
        UUID tasterId = bundleRepository.findAll().stream()
                .filter(b -> !b.isPurchasable())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Taster bundle not found"))
                .getId();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "bundleId", tasterId.toString(),
                "msisdn", "255712345678",
                "provider", "MPESA"
        );

        // When: attempt to purchase non-purchasable bundle
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(body, headers),
                Map.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
