package com.opendesk.payment;

// Requirement: PYMT-05 — Payment history returns JWT-scoped payment records (no IDOR)
// Covered by: 03-05 (PaymentController GET /api/v1/payments)

import com.opendesk.payment.bundle.BundleRepository;
import com.opendesk.payment.bundle.SmsBundle;
import com.opendesk.payment.payment.Payment;
import com.opendesk.payment.payment.PaymentRepository;
import com.opendesk.payment.payment.PaymentStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentHistoryIT extends AbstractPaymentIntegrationTest {

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
    void paymentHistoryReturnsPaginatedRecordsForAuthenticatedUser() {
        // Given: two payments for userA, one for userB
        Payment p1 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(UUID.randomUUID().toString())
                .provider("MPESA")
                .build();
        Payment p2 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .bundleId(bundleId)
                .amountTzs(14500L)
                .smsCount(1000)
                .status(PaymentStatus.SUCCESS)
                .externalId(UUID.randomUUID().toString())
                .provider("TIGOPESA")
                .build();
        Payment p3 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userB)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.PENDING)
                .externalId(UUID.randomUUID().toString())
                .provider("MPESA")
                .build();
        paymentRepository.saveAll(List.of(p1, p2, p3));

        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);

        // When: userA requests payment history
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                Map.class
        );

        // Then: 200, only userA's payments (2), not userB's (IDOR protection)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        Object content = response.getBody().get("content");
        assertThat(content).isNotNull();
        List<?> payments = (List<?>) content;
        assertThat(payments).hasSize(2);
    }

    @Test
    void paymentHistoryDoesNotExposeOtherUsersPayments() {
        // Given: one payment for userB only
        Payment p = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userB)
                .bundleId(bundleId)
                .amountTzs(3200L)
                .smsCount(200)
                .status(PaymentStatus.SUCCESS)
                .externalId(UUID.randomUUID().toString())
                .provider("MPESA")
                .build();
        paymentRepository.save(p);

        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);

        // When: userA requests payment history
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                Map.class
        );

        // Then: 200, empty content — userB's payment is NOT returned (IDOR protection)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> payments = (List<?>) response.getBody().get("content");
        assertThat(payments).isEmpty();
    }

    @Test
    void paymentHistoryRequiresAuthentication() {
        // When: unauthenticated request
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/payments", String.class);
        // Then: 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
