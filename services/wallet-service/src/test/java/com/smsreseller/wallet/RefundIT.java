package com.smsreseller.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.wallet.balance.BalanceService;
import com.smsreseller.wallet.consumer.ProcessedEventRepository;
import com.smsreseller.wallet.lot.CreditLotRepository;
import com.smsreseller.wallet.lot.LotType;
import com.smsreseller.wallet.refund.RefundRequest;
import com.smsreseller.wallet.refund.RefundService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static java.util.concurrent.TimeUnit.SECONDS;

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
    private ObjectMapper objectMapper;

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
    void paymentConfirmedGrantsPurchasedLot() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        int smsCount = 200;

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "userId", userId.toString(),
                "paymentId", paymentId.toString(),
                "smsCount", smsCount
        ));

        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties())
                .build();
        message.getMessageProperties().setContentType("application/json");

        // Publish to the payment.events exchange with key payment.PaymentConfirmed
        rabbitTemplate.send("payment.events", "payment.PaymentConfirmed", message);

        // Wait for consumer to process
        Awaitility.await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(balanceService.getBalance(userId)).isEqualTo(smsCount));

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
    void paymentConfirmedIsIdempotentOnRedelivery() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        int smsCount = 100;

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "userId", userId.toString(),
                "paymentId", paymentId.toString(),
                "smsCount", smsCount
        ));

        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties())
                .build();
        message.getMessageProperties().setContentType("application/json");

        // First delivery
        rabbitTemplate.send("payment.events", "payment.PaymentConfirmed", message);
        Awaitility.await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(balanceService.getBalance(userId)).isEqualTo(smsCount));

        // Second delivery (same eventId — duplicate re-delivery)
        rabbitTemplate.send("payment.events", "payment.PaymentConfirmed", message);

        // Wait then assert still only one lot
        Awaitility.await()
                .atMost(5, SECONDS)
                .pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    long lotCount = lotRepository.findAll().stream()
                            .filter(l -> userId.equals(l.getUserId()))
                            .filter(l -> LotType.PURCHASED.equals(l.getLotType()))
                            .count();
                    assertThat(lotCount).isEqualTo(1);
                    assertThat(balanceService.getBalance(userId)).isEqualTo(smsCount);
                });
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

        // First call — should succeed (200)
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
