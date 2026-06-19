package com.opendesk.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for identity-service.
 *
 * <p>The NIDA verification call is @Async (IDEN-02 + D-01) — user is returned PENDING
 * immediately while NIDA verification runs on a dedicated bounded executor.
 *
 * <p>WHY a dedicated bounded executor instead of the default virtual-thread executor:
 * With {@code spring.threads.virtual.enabled=true}, Spring Boot's default async executor
 * is {@code SimpleAsyncTaskExecutor} — which is UNBOUNDED. A slow or hung NIDA endpoint
 * (which is real government infrastructure, see CLAUDE.md NIDA guidance) would spawn
 * unlimited in-flight virtual threads, exhausting resources (Pitfall 2 / T-02-DoS mitigated).
 *
 * <p>A fixed {@link ThreadPoolTaskExecutor} with a bounded queue caps concurrency and
 * fails fast when the queue is full, letting Resilience4j circuit breaker (Plan 02-03)
 * see the rejection and open the circuit (D-04 NidaVerificationService fallback).
 *
 * <p>The bean name {@code "nidaExecutor"} is referenced by {@code @Async("nidaExecutor")}
 * in {@code NidaVerificationService} implementations (02-03).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Core pool size: concurrency cap for NIDA calls in steady state. */
    private static final int NIDA_CORE_POOL_SIZE = 4;

    /** Max pool size: burst capacity when queue is saturated. */
    private static final int NIDA_MAX_POOL_SIZE = 8;

    /**
     * Bounded queue capacity — prevents unlimited task accumulation during NIDA outages.
     * Tasks beyond this cap are rejected (triggering Resilience4j fallback in 02-03).
     * NOT Integer.MAX_VALUE (Pitfall 2, T-02-DoS mitigated).
     */
    private static final int NIDA_QUEUE_CAPACITY = 50;

    /**
     * Bounded, dedicated thread pool for NIDA verification @Async calls.
     *
     * <p>corePoolSize=4, maxPoolSize=8, queueCapacity=50 keeps NIDA concurrency
     * predictable and bounded regardless of the default virtual-thread executor setting.
     * Named threads (nida-*) appear clearly in thread dumps for diagnostics.
     */
    @Bean("nidaExecutor")
    public Executor nidaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(NIDA_CORE_POOL_SIZE);
        executor.setMaxPoolSize(NIDA_MAX_POOL_SIZE);
        executor.setQueueCapacity(NIDA_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("nida-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
