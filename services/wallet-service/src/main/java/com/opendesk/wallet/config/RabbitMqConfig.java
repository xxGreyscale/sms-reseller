package com.opendesk.wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the wallet-service.
 *
 * <p>Declares the wallet-service's OWN outbound exchange ({@code wallet.events}).
 * Does NOT redeclare {@code identity.events} — the {@code @QueueBinding} on
 * {@link com.opendesk.wallet.consumer.UserVerifiedConsumer} binds passively to the
 * pre-existing identity exchange declared by the identity-service.
 *
 * <p>Configures a {@link Jackson2JsonMessageConverter} for JSON payload
 * serialization/deserialization across all AMQP messages.
 */
@Configuration
public class RabbitMqConfig {

    /**
     * Wallet-service outbound events exchange.
     *
     * <p>Not used in Phase 3 but declared here so it exists before wallet-service
     * starts publishing balance-related events in later phases.
     */
    @Bean
    public TopicExchange walletEventsExchange() {
        return new TopicExchange("wallet.events", true, false);
    }

    /**
     * Jackson-based JSON message converter for all AMQP messages.
     *
     * <p>Enables automatic deserialization of JSON payloads (e.g. UserVerifiedEvent)
     * in {@code @RabbitListener} methods.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate configured with the JSON converter for outbound publishing.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
