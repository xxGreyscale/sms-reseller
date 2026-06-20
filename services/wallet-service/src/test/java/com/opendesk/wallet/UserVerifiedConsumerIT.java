package com.opendesk.wallet;

// Cross-service integration: UserVerified AMQP event → 50 bonus credits granted (D-03)
// Requirement: WLET-01 (balance), WLET-06 (30-day bonus expiry)
// Covered by: 03-04 (UserVerifiedConsumer + idempotency via processed_events table)

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test: verifies that a UserVerified event published to identity.events
 * grants a 50-credit BONUS lot for the user — exactly once (idempotent re-delivery).
 *
 * <p>T-03-08: duplicate delivery MUST NOT double-grant bonus (processed_events guard).
 */
class UserVerifiedConsumerIT extends AbstractWalletIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    CreditLotRepository creditLotRepository;

    @Autowired
    BalanceService balanceService;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanData() {
        // CreditTransaction has FK to CreditLot — delete transactions first
        creditLotRepository.findAll(); // eagerly loaded — clean via cascade not configured
        // Use Spring's JPA context to clean between tests
        creditLotRepository.deleteAll();
    }

    @Test
    void userVerifiedEventGrantsFiftyCreditBonusLotIdempotently() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        // Publish UserVerified event to identity.events exchange with routing key identity.UserVerified
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "userId", userId.toString(),
                "freeCredits", 50
        ));
        var message = MessageBuilder.withBody(payload.getBytes())
                .andProperties(new MessageProperties())
                .build();
        message.getMessageProperties().setContentType("application/json");

        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        // Wait for consumer to process the message
        await().atMost(10, SECONDS).untilAsserted(() -> {
            int balance = balanceService.getBalance(userId);
            assertThat(balance).isEqualTo(50);
        });

        // Verify a BONUS lot was created for the user
        var lots = creditLotRepository.findAll().stream()
                .filter(l -> l.getUserId().equals(userId))
                .toList();
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).getLotType()).isEqualTo(LotType.BONUS);
        assertThat(lots.get(0).getGranted()).isEqualTo(50);

        // Verify expires_at is approximately now + 30 days (D-03)
        Instant expectedExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        assertThat(lots.get(0).getExpiresAt())
                .isAfter(expectedExpiry.minus(5, ChronoUnit.MINUTES))
                .isBefore(expectedExpiry.plus(5, ChronoUnit.MINUTES));

        // IDEMPOTENCY: deliver the same eventId again — must NOT grant a second lot
        rabbitTemplate.send("identity.events", "identity.UserVerified", message);

        // Wait a bit to ensure the consumer processes the duplicate
        await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() -> {
            long lotCount = creditLotRepository.findAll().stream()
                    .filter(l -> l.getUserId().equals(userId))
                    .count();
            assertThat(lotCount).isEqualTo(1);
        });

        // Balance still 50, not 100
        assertThat(balanceService.getBalance(userId)).isEqualTo(50);
    }
}
