package com.smsreseller.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for messaging-service.
 *
 * <p>Topology:
 * <pre>
 * messaging.events (TopicExchange)  — outbox relay publishes events here
 * messaging.send   (quorum queue)   — primary work queue for send pipeline (04-05)
 *   DLX → messaging.retry.dlx (DirectExchange)
 *     → messaging.retry.1m  (classic, TTL configurable for tests)
 *       → messaging.send (on TTL expiry)
 *     → messaging.retry.5m  (classic, TTL configurable)
 *       → messaging.send
 *     → messaging.retry.15m (classic, TTL configurable)
 *       → messaging.send
 *     → messaging.dead (quorum queue — poison/exhausted messages)
 * </pre>
 *
 * <p>T-04-06 (DoS mitigation): quorum send queue + deliveryLimit(3) + default-requeue-rejected=false
 * (set in application.yml) prevent poison message infinite loops (Pitfall 1).
 * T-04-07 (Tampering): .quorum() explicit on send/dead; retry queues classic-with-TTL (OQ#4).
 *
 * <p>TTL values are externalized so tests can use shortened ladders (2s/4s/6s via application-test.yml)
 * without modifying production config.
 */
@Configuration
public class RabbitMqConfig {

    // ── Exchange and queue name constants (consumed by 04-05/06 plans) ──────

    public static final String EXCHANGE            = "messaging.events";
    public static final String ROUTING_KEY_PREFIX  = "messaging.";

    public static final String SEND_QUEUE          = "messaging.send";
    public static final String DLX                 = "messaging.retry.dlx";
    public static final String RETRY_1_QUEUE       = "messaging.retry.1m";
    public static final String RETRY_2_QUEUE       = "messaging.retry.5m";
    public static final String RETRY_3_QUEUE       = "messaging.retry.15m";
    public static final String DEAD_QUEUE          = "messaging.dead";

    // ── TTL ladder (externalized so tests override via application-test.yml) ─

    @Value("${app.messaging.retry.ttl-1:60000}")
    private int ttl1Ms;

    @Value("${app.messaging.retry.ttl-2:300000}")
    private int ttl2Ms;

    @Value("${app.messaging.retry.ttl-3:900000}")
    private int ttl3Ms;

    // ── Exchanges ────────────────────────────────────────────────────────────

    /** Topic exchange for all messaging-service domain events (outbox, MessageAccepted, etc.). */
    @Bean
    public TopicExchange messagingEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Direct exchange — DLX for the send queue.
     * Routes nacked messages to the appropriate retry queue by routing key.
     */
    @Bean
    public DirectExchange retryDlx() {
        return new DirectExchange(DLX, true, false);
    }

    // ── Primary work queue (quorum) ──────────────────────────────────────────

    /**
     * Primary quorum send queue.
     *
     * <p>Quorum queue provides broker-level durability and x-delivery-limit support.
     * deliveryLimit(3): after 3 delivery attempts, broker auto-dead-letters to DLX.
     * deadLetterRoutingKey → messaging.retry.1m: first failure routes to 1-minute retry.
     *
     * <p>T-04-06/07: .quorum() explicit — prevents classic/quorum mismatch (Pitfall 2).
     */
    @Bean
    public Queue sendQueue() {
        return QueueBuilder.durable(SEND_QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RETRY_1_QUEUE)
                .deliveryLimit(3)
                .build();
    }

    // ── TTL retry ladder (classic queues — TTL supported universally, OQ#4) ─

    /** Retry queue 1 (~1 minute TTL). On expiry, dead-letters back to messaging.send. */
    @Bean
    public Queue retry1Queue() {
        return QueueBuilder.durable(RETRY_1_QUEUE)
                .ttl(ttl1Ms)
                .deadLetterExchange("")          // default exchange
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    /** Retry queue 2 (~5 minute TTL). On expiry, dead-letters back to messaging.send. */
    @Bean
    public Queue retry2Queue() {
        return QueueBuilder.durable(RETRY_2_QUEUE)
                .ttl(ttl2Ms)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    /** Retry queue 3 (~15 minute TTL). On expiry, dead-letters back to messaging.send. */
    @Bean
    public Queue retry3Queue() {
        return QueueBuilder.durable(RETRY_3_QUEUE)
                .ttl(ttl3Ms)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    // ── Dead (poison) queue (quorum) ─────────────────────────────────────────

    /**
     * Quorum dead queue — catches messages that exhausted all retries or received a hard-fail.
     * DeadLetterConsumer (04-06) listens here to emit MessageRefundDue events.
     *
     * <p>T-04-07: .quorum() explicit on dead queue — same durability requirement as send queue.
     */
    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).quorum().build();
    }

    // ── DLX bindings ─────────────────────────────────────────────────────────

    @Bean
    public Binding retry1Binding(Queue retry1Queue, DirectExchange retryDlx) {
        return BindingBuilder.bind(retry1Queue).to(retryDlx).with(RETRY_1_QUEUE);
    }

    @Bean
    public Binding retry2Binding(Queue retry2Queue, DirectExchange retryDlx) {
        return BindingBuilder.bind(retry2Queue).to(retryDlx).with(RETRY_2_QUEUE);
    }

    @Bean
    public Binding retry3Binding(Queue retry3Queue, DirectExchange retryDlx) {
        return BindingBuilder.bind(retry3Queue).to(retryDlx).with(RETRY_3_QUEUE);
    }

    @Bean
    public Binding deadBinding(Queue deadQueue, DirectExchange retryDlx) {
        return BindingBuilder.bind(deadQueue).to(retryDlx).with(DEAD_QUEUE);
    }

    // ── Message converter + template ─────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
