package com.smsreseller.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service — manages Azampay STK-push payments, bundle catalog, callbacks, and reconciliation.
 *
 * <p>Responsibilities: SMS bundle catalog (read-only), payment initiation via Azampay (stub in dev/test,
 * prod gateway in prod), callback processing with idempotency, 2-minute timeout enforcement,
 * reconciliation job for stale payments, and the transactional-outbox relay for credit-grant events.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
