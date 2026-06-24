package com.smsreseller.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for notification-service (consumer-only).
 *
 * <p>notification-service does NOT publish to any exchange — it is a pure consumer.
 * No exchange beans are declared here; all exchanges are owned by upstream services.
 * The {@link Jackson2JsonMessageConverter} is required for {@code @RabbitListener}
 * methods to automatically deserialize JSON payloads into event records.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
