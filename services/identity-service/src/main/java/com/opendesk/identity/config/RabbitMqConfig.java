package com.opendesk.identity.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the identity-service.
 *
 * <p>Declares the {@code identity.events} topic exchange used by the transactional outbox
 * relay ({@link com.opendesk.identity.outbox.OutboxRelay}) to publish domain events.
 *
 * <p>Topic exchange pattern (CLAUDE.md): routing key format is {@code identity.<EventType>},
 * e.g. {@code identity.UserVerified}. Phase 3 wallet service binds a queue to
 * {@code identity.UserVerified} to consume credit-grant events.
 */
@Configuration
public class RabbitMqConfig {

    /** Exchange name used by all identity-service outbox events. */
    public static final String IDENTITY_EXCHANGE = "identity.events";

    /** Routing key prefix for identity events. */
    public static final String ROUTING_KEY_PREFIX = "identity.";

    @Bean
    public TopicExchange identityEventsExchange() {
        // durable=true, autoDelete=false — survives broker restarts
        return new TopicExchange(IDENTITY_EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
