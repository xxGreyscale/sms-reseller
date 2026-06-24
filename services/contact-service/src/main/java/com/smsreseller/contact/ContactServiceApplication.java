package com.smsreseller.contact;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Contact Service — manages contacts, groups, suppression lists, and CSV imports.
 *
 * <p>Responsibilities: contact CRUD (with E.164 normalization via libphonenumber),
 * contact group management, suppression list enforcement, CSV bulk import,
 * and outbox relay for downstream contact events.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class ContactServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContactServiceApplication.class, args);
    }
}
