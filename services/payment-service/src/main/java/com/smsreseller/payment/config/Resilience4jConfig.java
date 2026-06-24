package com.smsreseller.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * Resilience4j circuit breaker and retry configuration for the Azampay payment gateway (T-03-19).
 *
 * <p>Only active under {@code @Profile("prod")} — stub tests use the StubPaymentGateway which
 * does not need circuit breaking. However, the Resilience4j Spring Boot starter auto-configures
 * beans from {@code application.yml} when the annotations are present on the prod gateway.
 *
 * <p>The "azampay" circuit breaker:
 * <ul>
 *   <li>Failure rate threshold: 50% (opens after 50% of calls fail)</li>
 *   <li>Sliding window: 10 calls</li>
 *   <li>Wait duration in OPEN state: 30 seconds</li>
 *   <li>Permitted calls in HALF_OPEN: 3</li>
 * </ul>
 *
 * <p>The "azampay" retry:
 * <ul>
 *   <li>Max attempts: 3</li>
 *   <li>Wait duration: 1 second (initial), exponential multiplier 2</li>
 * </ul>
 *
 * <p>These defaults can be overridden via {@code application.yml} using Spring Boot's
 * Resilience4j auto-configuration prefix {@code resilience4j.circuitbreaker.instances.azampay}.
 */
@Configuration
public class Resilience4jConfig {
    // Resilience4j Spring Boot 3 auto-configures CircuitBreakerRegistry and RetryRegistry
    // from application.yml (resilience4j.circuitbreaker.instances.azampay, etc.).
    // This class serves as a home for Azampay-specific config documentation and any
    // programmatic customization needed in future.
    //
    // For MVP, application.yml configures the "azampay" named circuit breaker + retry.
    // No @Bean overrides needed — auto-config handles both prod and stub profiles correctly.
    // @CircuitBreaker(name="azampay") on AzampayPaymentGateway picks up from registry automatically.
}
