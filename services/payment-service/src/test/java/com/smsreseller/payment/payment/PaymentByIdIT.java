package com.smsreseller.payment.payment;

// Requirement: MOBL-05 — GET /api/v1/payments/{id} owner-scoped status for STK countdown polling (D-11)
// Plan: 06-02 — RED test written before implementation (TDD)

import com.smsreseller.payment.AbstractPaymentIntegrationTest;
import com.smsreseller.payment.JwtTestHelper;
import com.smsreseller.payment.domain.bundle.SmsBundle;
import com.smsreseller.payment.domain.payment.Payment;
import com.smsreseller.payment.domain.payment.PaymentStatus;
import com.smsreseller.payment.infrastructure.persistence.BundleRepository;
import com.smsreseller.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GET /api/v1/payments/{id} — owner-scoped single-payment status.
 *
 * <p>Covers:
 * <ul>
 *   <li>Test 1: owner gets 200 with correct status field (PENDING/CONFIRMED/EXPIRED)</li>
 *   <li>Test 2: cross-user request returns 404 (IDOR guard — existence must not leak)</li>
 *   <li>Test 3: random unknown UUID returns 404</li>
 * </ul>
 */
class PaymentByIdIT extends AbstractPaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    private UUID userA;
    private UUID userB;
    private String tokenA;
    private String tokenB;
    private UUID bundleId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        tokenA = jwtTestHelper.generateToken(userA.toString());
        tokenB = jwtTestHelper.generateToken(userB.toString());

        bundleId = bundleRepository.findAll().stream()
                .filter(SmsBundle::isPurchasable)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void getById_owner_returns200WithStatusField() {
        // Given: a PENDING payment owned by userA
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userA)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(payment);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);

        // When: userA requests their own payment by id
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/payments/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        // Then: 200 with status == "PENDING"
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
    }

    @Test
    void getById_crossUser_returns404_notLeakingExistence() {
        // Given: a payment owned by userA
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userA)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(paymentId.toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(payment);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenB);

        // When: userB requests userA's payment (IDOR attempt)
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        // Then: 404 — payment existence must not leak (not 403 or 200)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_unknownId_returns404() {
        // Given: no payment with this id
        UUID randomId = UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);

        // When: request for a non-existent payment id
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/" + randomId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        // Then: 404
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
