package com.smsreseller.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wallet Service — manages SMS credit lots, balance derivation, reservations, and outbox events.
 *
 * <p>Responsibilities: credit lot lifecycle (PURCHASED | BONUS | REFUND), FIFO reservation
 * (expiry-soonest-first with pessimistic locking), low-credit alerts, expiry sweep job,
 * and the transactional-outbox relay for downstream events.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
