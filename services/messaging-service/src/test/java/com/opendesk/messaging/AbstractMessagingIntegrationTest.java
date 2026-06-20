package com.opendesk.messaging;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for messaging-service integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>PostgreSQL 16 via Testcontainers {@code @ServiceConnection} — no manual JDBC config needed</li>
 *   <li>RabbitMQ 3 via {@code RabbitMQContainer} with {@code @DynamicPropertySource} — wires
 *       all AMQP connection properties for the quorum queue + DLX TTL-ladder tests</li>
 * </ul>
 *
 * <p>No Redis container — messaging-service has no Redis dependency in Phase 4.
 * Rate-limiting (if added) would require Redis; defer to that plan.
 *
 * <p>Containers are started ONCE for the entire test run via a static initializer block,
 * NOT via {@code @Testcontainers}/@Container annotations. This is the correct pattern for
 * shared containers across multiple {@code @SpringBootTest} classes.
 *
 * <p>Profiles "stub" and "test" are activated:
 * <ul>
 *   <li>{@code stub} — enables {@code StubSmsProvider} and any other stub @Profile("stub") beans</li>
 *   <li>{@code test} — activates {@code application-test.yml} with shortened DLX TTL ladder
 *       (2s/4s/6s) so DlxRetryIT completes well within the 180s feedback budget</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
@Import(MessagingTestConfiguration.class)
public abstract class AbstractMessagingIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("messaging_test")
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
