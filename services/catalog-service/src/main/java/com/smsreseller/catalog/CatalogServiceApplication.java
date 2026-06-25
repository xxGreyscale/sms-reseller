package com.smsreseller.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Catalog Service — placeholder application.
 *
 * <p>This service is scaffolded but not yet feature-complete. It currently boots as a
 * minimal Spring Boot resource server exposing only actuator endpoints, so the full
 * local dev stack comes up green. Domain logic (SMS bundle catalog) will be added in a
 * later phase.
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
