package com.smsreseller.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for admin-service.
 *
 * <p>Admin-service is a PASSIVE consumer only — it does not declare any exchanges.
 * The {@code @RabbitListener} @Exchange annotations on {@link com.smsreseller.admin.consumer.DomainEventAuditConsumer}
 * use {@code ignoreDeclarationExceptions="true"} to tolerate any topology divergence
 * with the owning service (T-05-20, RESEARCH.md Pitfall 1).
 *
 * <p>Jackson2JsonMessageConverter is wired to both the template and the listener container
 * factory so that event payloads are deserialized automatically from JSON.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
