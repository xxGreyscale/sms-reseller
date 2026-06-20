package com.opendesk.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for payment-service.
 *
 * <p>Declares the {@code payment.events} topic exchange for outbound events
 * (payment-confirmed, late-success, etc.). Routing key prefix: {@code payment.}.
 *
 * <p>Does NOT redeclare {@code identity.events} — identity-service owns that exchange.
 * The {@code @RabbitListener} binding in any future consumer creates the queue binding passively.
 */
@Configuration
public class RabbitMqConfig {

    /** Outbound exchange for payment-service events (payment-confirmed, etc.). */
    public static final String EXCHANGE = "payment.events";

    /** Routing key prefix for all payment events. */
    public static final String ROUTING_KEY_PREFIX = "payment.";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
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
