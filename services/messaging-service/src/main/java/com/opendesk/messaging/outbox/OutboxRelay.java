package com.opendesk.messaging.outbox;

import com.opendesk.messaging.config.RabbitMqConfig;
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
 * Transactional outbox relay for messaging-service events.
 *
 * <p>Polls unsent {@link OutboxEntry} rows every 5 seconds and publishes each to
 * the {@code messaging.events} topic exchange. Mirrors identity-service OutboxRelay
 * pattern exactly (Pattern 4). At-least-once delivery — consumers deduplicate by {@code event_id}.
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

        if (batch.isEmpty()) {
            return;
        }

        log.debug("OutboxRelay: relaying {} unsent event(s)", batch.size());

        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                markSent(entry);
            } catch (Exception ex) {
                log.warn("OutboxRelay: failed to publish entry id={} eventId={} — will retry",
                        entry.getId(), entry.getEventId(), ex);
            }
        }
    }

    private void publish(OutboxEntry entry) {
        String routingKey = RabbitMqConfig.ROUTING_KEY_PREFIX + entry.getEventType();
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, entry.getPayload());
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
