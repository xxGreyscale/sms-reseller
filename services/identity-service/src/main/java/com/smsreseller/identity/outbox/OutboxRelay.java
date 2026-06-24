package com.smsreseller.identity.outbox;

import com.smsreseller.identity.config.RabbitMqConfig;
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
 * Transactional outbox relay — publishes unsent domain events to RabbitMQ (Pattern 4).
 *
 * <p>Runs on a fixed-delay schedule, fetching a bounded batch of unsent {@link OutboxEntry}
 * rows and publishing each to the {@code identity.events} topic exchange. After successful
 * publish, the row is marked {@code sent=true} and {@code sent_at} is set.
 *
 * <p>Delivery guarantee: at-least-once. Consumers MUST deduplicate by {@code event_id}
 * (Pitfall 5 from 02-RESEARCH.md). If the relay crashes after publish but before marking
 * sent, the event will be re-published on the next run.
 *
 * <p>Each row is marked sent in its own {@code @Transactional} call so a publish failure
 * on one row does not block subsequent rows in the batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    /** Maximum rows per relay run — keeps relay bounded under high load. */
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Relay loop — runs every 5 seconds after the previous run completes.
     *
     * <p>Fixed-delay (not fixed-rate) so a slow RabbitMQ does not cause relay runs to
     * stack up. In production, relay lag manifests as credit-grant latency (acceptable).
     */
    @Scheduled(fixedDelay = 5_000)
    public void relay() {
        List<OutboxEntry> batch =
                outboxRepository.findBySentFalseOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) {
            return;
        }

        log.debug("OutboxRelay: relaying {} unsent event(s)", batch.size());

        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                markSent(entry);
            } catch (Exception ex) {
                // Log and continue — this row will be retried on the next relay run.
                // At-least-once guarantee: a re-publish is safe; consumers deduplicate by event_id.
                log.warn("OutboxRelay: failed to publish outbox entry id={} eventId={} — will retry",
                        entry.getId(), entry.getEventId(), ex);
            }
        }
    }

    private void publish(OutboxEntry entry) {
        String routingKey = RabbitMqConfig.ROUTING_KEY_PREFIX + entry.getEventType();
        rabbitTemplate.convertAndSend(RabbitMqConfig.IDENTITY_EXCHANGE, routingKey, entry.getPayload());
        log.debug("OutboxRelay: published eventId={} eventType={} routingKey={}",
                entry.getEventId(), entry.getEventType(), routingKey);
    }

    @Transactional
    public void markSent(OutboxEntry entry) {
        entry.setSent(true);
        entry.setSentAt(Instant.now());
        outboxRepository.save(entry);
    }
}
