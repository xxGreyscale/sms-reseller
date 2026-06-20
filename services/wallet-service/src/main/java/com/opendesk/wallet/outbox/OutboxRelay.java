package com.opendesk.wallet.outbox;

import com.opendesk.wallet.config.RabbitMqConfig;
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
 * Transactional outbox relay for wallet-service.
 *
 * <p>Polls unsent {@link OutboxEntry} records every 5 seconds and publishes them to the
 * {@code wallet.events} TopicExchange. Each delivery is marked {@code sent=true} after
 * successful publishing. The {@code event_id UNIQUE} DB constraint prevents duplicate rows
 * on concurrent relay retries.
 *
 * <p>Copied verbatim from identity-service — only the package and exchange constant change.
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
