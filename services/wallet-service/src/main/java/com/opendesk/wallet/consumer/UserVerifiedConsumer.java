package com.opendesk.wallet.consumer;

import com.opendesk.wallet.lot.LotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Inbound AMQP consumer: grants a 50-credit BONUS lot when a user is NIDA-verified.
 *
 * <p>Binds to the {@code identity.events} TopicExchange (declared by identity-service)
 * with routing key {@code identity.UserVerified}. The {@code @QueueBinding} creates the
 * queue and binding passively — it does NOT redeclare the exchange.
 *
 * <p><b>Idempotency (T-03-08):</b> {@code processed_events} table guard via
 * {@code INSERT ... ON CONFLICT DO NOTHING}. If the event_id was already inserted,
 * {@code tryInsert} returns false and the method returns early — no double-grant.
 *
 * <p><b>Atomicity (T-03-10):</b> {@code @Transactional} ensures that the
 * {@code processed_events} INSERT and the {@code credit_lots} GRANT commit together
 * or both roll back (no lost bonus on consumer crash).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserVerifiedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final LotService lotService;

    /**
     * Handles a UserVerified event by granting a 30-day BONUS lot (D-03).
     *
     * @param event the deserialized UserVerified payload from identity-service
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
            exchange = @Exchange(name = "identity.events", type = "topic", durable = "true"),
            key = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(UserVerifiedEvent event) {
        log.debug("Received UserVerified event: eventId={}, userId={}, freeCredits={}",
                event.eventId(), event.userId(), event.freeCredits());

        // Idempotency guard: return immediately if this event was already processed (T-03-08)
        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate UserVerified event ignored: eventId={}", event.eventId());
            return;
        }

        // Grant BONUS lot with 30-day expiry (D-03)
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        lotService.grantBonus(event.userId(), event.freeCredits(), expiresAt);

        log.info("Granted {} bonus credits to userId={} (eventId={})",
                event.freeCredits(), event.userId(), event.eventId());
    }
}
