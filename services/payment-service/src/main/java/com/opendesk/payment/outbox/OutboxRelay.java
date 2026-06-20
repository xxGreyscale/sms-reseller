package com.opendesk.payment.outbox;

import com.opendesk.payment.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox relay — publishes unsent outbox entries to {@code payment.events}.
 *
 * <p>Copied verbatim from identity-service OutboxRelay (03-PATTERNS.md lines 124-176).
 * Only the package and exchange constant (via {@link RabbitMqConfig#EXCHANGE}) differ.
 *
 * <p>At-least-once delivery: if publish succeeds but markSent fails (rare), the entry
 * will be retried on the next relay cycle. Consumers must be idempotent (wallet-service
 * uses {@code processed_events} ON CONFLICT DO NOTHING — Plan 06).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5_000)
    public void relay() {
        List<OutboxEntry> batch =
                outboxRepository.findBySentFalseOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;
        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                markSent(entry);
            } catch (Exception ex) {
                log.warn("OutboxRelay: failed to publish id={} — will retry", entry.getId(), ex);
            }
        }
    }

    private void publish(OutboxEntry entry) {
        String routingKey = RabbitMqConfig.ROUTING_KEY_PREFIX + entry.getEventType();
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, entry.getPayload());
    }

    @Transactional
    public void markSent(OutboxEntry entry) {
        entry.setSent(true);
        entry.setSentAt(Instant.now());
        outboxRepository.save(entry);
    }
}
