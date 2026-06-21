package com.opendesk.wallet;

// Idempotent MessagingEventConsumer: CONSUME / RELEASE / REFUND (MESG-10, wallet side)
// Requirement: wallet applies ledger mutations from messaging AMQP events exactly once
// Threat T-04-08: duplicate events MUST NOT double-mutate the ledger (processed_events guard)

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotService;
import com.opendesk.wallet.lot.LotType;
import com.opendesk.wallet.reservation.ReservationService;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import com.opendesk.wallet.transaction.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test: MessagingEventConsumer processes MessageAccepted / MessageReleased /
 * MessageRefundDue events from messaging.events exchange idempotently.
 *
 * <p>T-04-08: duplicate events MUST NOT double-mutate the ledger.
 * T-04-09: concurrent operations are protected by @Transactional (pessimistic lock inherited
 *          from reservation, consumer runs in its own @Transactional).
 */
class MessagingEventConsumerIT extends AbstractWalletIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    LotService lotService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    CreditLotRepository creditLotRepository;

    @Autowired
    CreditTransactionRepository creditTransactionRepository;

    @Autowired
    BalanceService balanceService;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        creditTransactionRepository.deleteAll();
        creditLotRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // MessageAccepted → consumeFromLot (CONSUME)
    // -----------------------------------------------------------------------

    @Test
    void messageAcceptedConsumesLot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // Seed: grant 5 credits, reserve 2
        var lot = lotService.grantBonus(userId, 5, Instant.now().plus(30, ChronoUnit.DAYS));
        reservationService.reserve(userId, 2, campaignId);

        // Verify initial state
        var lotBefore = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotBefore.getReserved()).isEqualTo(2);
        assertThat(lotBefore.getConsumed()).isEqualTo(0);

        // Publish MessageAccepted to messaging.events
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "messageId", UUID.randomUUID().toString(),
                "userId", userId.toString(),
                "lotId", lot.getId().toString()
        ));
        sendToMessagingEvents("messaging.MessageAccepted", payload);

        // Wait for consumer to apply CONSUME
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var lotAfter = creditLotRepository.findById(lot.getId()).orElseThrow();
            assertThat(lotAfter.getReserved()).isEqualTo(1);
            assertThat(lotAfter.getConsumed()).isEqualTo(1);
        });

        // Verify CONSUME CreditTransaction was written
        var consumeTxns = creditTransactionRepository.findAll().stream()
                .filter(t -> t.getLotId().equals(lot.getId()) && t.getTxnType() == TxnType.CONSUME)
                .toList();
        assertThat(consumeTxns).hasSize(1);

        // IDEMPOTENCY: replay the same event — must NOT consume again
        sendToMessagingEvents("messaging.MessageAccepted", payload);

        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() -> {
            var lotAfterReplay = creditLotRepository.findById(lot.getId()).orElseThrow();
            assertThat(lotAfterReplay.getReserved()).isEqualTo(1); // unchanged
            assertThat(lotAfterReplay.getConsumed()).isEqualTo(1); // still 1, not 2
        });
    }

    // -----------------------------------------------------------------------
    // MessageReleased → releaseFromLot (RELEASE)
    // -----------------------------------------------------------------------

    @Test
    void messageReleasedReleasesLot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        // Seed: grant 5 credits, reserve 2
        var lot = lotService.grantBonus(userId, 5, Instant.now().plus(30, ChronoUnit.DAYS));
        reservationService.reserve(userId, 2, campaignId);

        var lotBefore = creditLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotBefore.getReserved()).isEqualTo(2);
        assertThat(lotBefore.getConsumed()).isEqualTo(0);

        // Publish MessageReleased
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "messageId", UUID.randomUUID().toString(),
                "userId", userId.toString(),
                "lotId", lot.getId().toString()
        ));
        sendToMessagingEvents("messaging.MessageReleased", payload);

        // Wait for consumer to apply RELEASE
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var lotAfter = creditLotRepository.findById(lot.getId()).orElseThrow();
            assertThat(lotAfter.getReserved()).isEqualTo(1);
            assertThat(lotAfter.getConsumed()).isEqualTo(0); // consumed unchanged
        });

        // Verify RELEASE CreditTransaction was written
        var releaseTxns = creditTransactionRepository.findAll().stream()
                .filter(t -> t.getLotId().equals(lot.getId()) && t.getTxnType() == TxnType.RELEASE)
                .toList();
        assertThat(releaseTxns).hasSize(1);

        // IDEMPOTENCY: replay the same event — must NOT release again
        sendToMessagingEvents("messaging.MessageReleased", payload);

        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() -> {
            var lotAfterReplay = creditLotRepository.findById(lot.getId()).orElseThrow();
            assertThat(lotAfterReplay.getReserved()).isEqualTo(1); // unchanged
        });
    }

    // -----------------------------------------------------------------------
    // MessageRefundDue → creditBack (REFUND lot)
    // -----------------------------------------------------------------------

    @Test
    void messageRefundDueCreditsBack() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        // No credits needed initially for a refund — creditBack creates a new lot
        int creditsToRefund = 3;
        int balanceBefore = balanceService.getBalance(userId);

        // Publish MessageRefundDue
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "messageId", messageId.toString(),
                "userId", userId.toString(),
                "lotId", UUID.randomUUID().toString(), // originating lot (not used for refund path)
                "creditsToRefund", creditsToRefund
        ));
        sendToMessagingEvents("messaging.MessageRefundDue", payload);

        // Wait for consumer to create a REFUND lot via creditBack
        await().atMost(10, SECONDS).untilAsserted(() -> {
            int balanceAfter = balanceService.getBalance(userId);
            assertThat(balanceAfter).isEqualTo(balanceBefore + creditsToRefund);
        });

        // Verify a REFUND lot was created
        var refundLots = creditLotRepository.findAll().stream()
                .filter(l -> l.getUserId().equals(userId) && l.getLotType() == LotType.REFUND)
                .toList();
        assertThat(refundLots).hasSize(1);
        assertThat(refundLots.get(0).getGranted()).isEqualTo(creditsToRefund);

        // IDEMPOTENCY: replay same event — must NOT create a second refund lot
        sendToMessagingEvents("messaging.MessageRefundDue", payload);

        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() -> {
            long refundLotCount = creditLotRepository.findAll().stream()
                    .filter(l -> l.getUserId().equals(userId) && l.getLotType() == LotType.REFUND)
                    .count();
            assertThat(refundLotCount).isEqualTo(1); // still 1, not 2
        });

        // Balance unchanged after replay
        assertThat(balanceService.getBalance(userId)).isEqualTo(balanceBefore + creditsToRefund);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void sendToMessagingEvents(String routingKey, String payload) {
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties())
                .build();
        message.getMessageProperties().setContentType("application/json");
        rabbitTemplate.send("messaging.events", routingKey, message);
    }
}
