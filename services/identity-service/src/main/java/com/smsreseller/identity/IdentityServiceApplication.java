package com.smsreseller.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Identity Service — the sole JWT issuer for the sms-reseller platform.
 *
 * <p>Responsibilities: user registration (NIDA KYC), login/logout, JWT issuance (RSA-signed),
 * refresh-token rotation, password reset, and the transactional-outbox event for 50-credit grant.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
