package com.smsreseller.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Messaging Service — manages SMS campaigns, send pipeline, DLX retry, and sender-ID requests.
 *
 * <p>Responsibilities: campaign lifecycle (DRAFT → QUEUED → DISPATCHING → DONE),
 * per-recipient AMQP fan-out to quorum queue with DLX TTL-ladder retry,
 * credit reservation (sync REST to wallet-service), delivery tracking,
 * scheduled campaign dispatch, and sender-ID request state machine.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class MessagingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingServiceApplication.class, args);
    }
}
