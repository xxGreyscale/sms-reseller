package com.smsreseller.contact;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for contact-service integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>PostgreSQL 16 via Testcontainers {@code @ServiceConnection} — no manual JDBC config needed</li>
 * </ul>
 *
 * <p>No RabbitMQ container — contact-service has no AMQP consumers in Phase 4.
 * The outbox relay publishes events but these are not exercised in contact-service ITs;
 * they are tested in messaging-service ITs where the consumer is active.
 *
 * <p>Containers are started ONCE for the entire test run via a static initializer block,
 * NOT via {@code @Testcontainers}/@Container annotations. This is the correct pattern for
 * shared containers across multiple {@code @SpringBootTest} classes.
 *
 * <p>Profiles "stub" and "test" are activated:
 * <ul>
 *   <li>{@code stub} — enables stub beans for any @Profile("stub") components</li>
 *   <li>{@code test} — activates {@code application-test.yml} config overrides (Flyway off, ddl-auto=create-drop)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
@Import(ContactTestConfiguration.class)
public abstract class AbstractContactIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("contact_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        // Start container once for the entire test run. JVM shutdown hook (Ryuk) cleans up.
        POSTGRES.start();
    }
}
