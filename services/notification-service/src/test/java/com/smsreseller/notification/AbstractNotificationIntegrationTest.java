package com.smsreseller.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for notification-service integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>PostgreSQL 16 via Testcontainers {@code @ServiceConnection} — no manual JDBC config needed</li>
 *   <li>RabbitMQ 3 via {@code RabbitMQContainer} with {@code @DynamicPropertySource} — wires
 *       all AMQP connection properties for the 4-exchange consumer tests</li>
 * </ul>
 *
 * <p>No Redis container — notification-service has no OTP/session dependency.
 *
 * <p>Containers are started ONCE for the entire test run via a static initializer block,
 * NOT via {@code @Testcontainers}/@Container annotations. This is the correct pattern for
 * shared containers across multiple {@code @SpringBootTest} classes.
 *
 * <p>Profiles "stub" and "test" are activated:
 * <ul>
 *   <li>{@code stub} — enables {@code StubPushChannel} and any stub beans</li>
 *   <li>{@code test} — activates {@code application-test.yml} config overrides</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
public abstract class AbstractNotificationIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("notification_test")
                    .withUsername("test")
                    .withPassword("test");

    @SuppressWarnings("resource")
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3-management");

    static {
        // Start containers once for the entire test run. JVM shutdown hook (Ryuk) cleans up.
        POSTGRES.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
