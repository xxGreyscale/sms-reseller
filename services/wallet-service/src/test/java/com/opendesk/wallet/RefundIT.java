package com.opendesk.wallet;

import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.consumer.ProcessedEventRepository;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotType;
import com.opendesk.wallet.refund.RefundRequest;
import com.opendesk.wallet.refund.RefundService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for:
 * 1. PaymentConfirmed consumer — grants exactly one PURCHASED lot (idempotent) [PYMT-04, T-03-16]
 * 2. RefundService — idempotent credit-back (REFUND lot), rejects non-positive amounts [PYMT-08, D-07, T-03-17]
 *
 * <p>TDD RED: these tests fail before implementation because RefundService,
 * PaymentConfirmedConsumer, and the wallet outbox infrastructure do not yet exist.
 */
class RefundIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private CreditLotRepository lotRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private RefundService refundService;

    @Autowired
    JwtTestHelper jwtTestHelper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        processedEventRepository.deleteAll();
        lotRepository.deleteAll();
    }

    // ── PaymentConfirmed consumer tests ─────────────────────────────────────────

    /**
     * A PaymentConfirmed AMQP event grants exactly one PURCHASED lot with the correct credit count
     * and 12-month expiry (D-03). [PYMT-04]
     */
    @Test
    void paymentConfirmedGrantsPurchasedLot() {
        UUID paymentId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        int smsCount = 200;

        // Publish to the payment.events exchange with key payment.PaymentConfirmed
        String payload = String.format(
                "{\"eventId\":\"%s\",\"userId\":\"%s\",\"paymentId\":\"%s\",\"smsCount\":%d}",
                eventId, userId, paymentId, smsCount
        );
        rabbitTemplate.convertAndSend("payment.events", "payment.PaymentConfirmed", payload);

        // Wait for consumer to process
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> balanceService.getBalance(userId) >= smsCount);

        // Verify exactly one PURCHASED lot
        var lots = lotRepository.findAll().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .filter(l -> LotType.PURCHASED.equals(l.getLotType()))
                .toList();
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).getGranted()).isEqualTo(smsCount);
        assertThat(lots.get(0).getPaymentId()).isEqualTo(paymentId);

        // Verify 12-month expiry (approximately 365 days from now, D-03)
        var expiresAt = lots.get(0).getExpiresAt();
        var now = java.time.Instant.now();
        assertThat(expiresAt).isAfter(now.plus(364, ChronoUnit.DAYS));
        assertThat(expiresAt).isBefore(now.plus(366, ChronoUnit.DAYS));
    }

    /**
     * Re-delivery of the same eventId (duplicate) must not grant a second lot.
     * processed_events guard ensures exactly-once credit [T-03-16].
     */
    @Test
    void paymentConfirmedIsIdempotentOnRedelivery() {
        UUID paymentId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        int smsCount = 100;

        String payload = String.format(
                "{\"eventId\":\"%s\",\"userId\":\"%s\",\"paymentId\":\"%s\",\"smsCount\":%d}",
                eventId, userId, paymentId, smsCount
        );

        // First delivery
        rabbitTemplate.convertAndSend("payment.events", "payment.PaymentConfirmed", payload);
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> balanceService.getBalance(userId) >= smsCount);

        // Second delivery (same eventId — duplicate re-delivery)
        rabbitTemplate.convertAndSend("payment.events", "payment.PaymentConfirmed", payload);

        // Wait a moment then assert still only one lot
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long lotCount = lotRepository.findAll().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .filter(l -> LotType.PURCHASED.equals(l.getLotType()))
                .count();
        assertThat(lotCount).isEqualTo(1);
        assertThat(balanceService.getBalance(userId)).isEqualTo(smsCount);
    }

    // ── RefundService tests ──────────────────────────────────────────────────────

    /**
     * RefundService.refund() creates a REFUND lot and credits the wallet (D-07, PYMT-08).
     */
    @Test
    void refundServiceCreatesRefundLotAndCreditsWallet() {
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        int credits = 50;

        refundService.refund(userId, credits, referenceId, idempotencyKey);

        assertThat(balanceService.getBalance(userId)).isEqualTo(credits);

        var lots = lotRepository.findAll().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .filter(l -> LotType.REFUND.equals(l.getLotType()))
                .toList();
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).getGranted()).isEqualTo(credits);
    }

    /**
     * Calling refund twice with the same idempotencyKey must only credit once [T-03-17].
     */
    @Test
    void refundIsIdempotentForSameIdempotencyKey() {
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        int credits = 75;

        refundService.refund(userId, credits, referenceId, idempotencyKey);
        refundService.refund(userId, credits, referenceId, idempotencyKey); // duplicate call

        assertThat(balanceService.getBalance(userId)).isEqualTo(credits);

        long lotCount = lotRepository.findAll().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .filter(l -> LotType.REFUND.equals(l.getLotType()))
                .count();
        assertThat(lotCount).isEqualTo(1);
    }

    /**
     * RefundService must reject credits <= 0 [T-03-17, Security Domain].
     */
    @Test
    void refundRejectsNonPositiveCredits() {
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        assertThatThrownBy(() -> refundService.refund(userId, 0, referenceId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> refundService.refund(userId, -10, referenceId, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(balanceService.getBalance(userId)).isZero();
    }

    /**
     * POST /api/v1/wallet/refunds is reachable and drives the idempotent refund path.
     */
    @Test
    void refundEndpointIsReachableAndIdempotent() {
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        int credits = 30;

        String jwt = jwtTestHelper.createToken(userId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);

        RefundRequest request = new RefundRequest(userId, credits, referenceId, idempotencyKey);
        HttpEntity<RefundRequest> entity = new HttpEntity<>(request, headers);

        // First call — should succeed (201 or 200)
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/wallet/refunds", entity, Void.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(balanceService.getBalance(userId)).isEqualTo(credits);

        // Second call with same idempotencyKey — idempotent, balance unchanged
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/wallet/refunds", entity, Void.class);
        assertThat(balanceService.getBalance(userId)).isEqualTo(credits);
    }
}
