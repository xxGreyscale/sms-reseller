package com.opendesk.identity;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for identity-service integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>PostgreSQL 16 via Testcontainers {@code @ServiceConnection} — no manual JDBC config needed</li>
 *   <li>Redis 7 via {@code GenericContainer} with {@code @DynamicPropertySource} — wires
 *       {@code spring.data.redis.host} and {@code spring.data.redis.port} automatically</li>
 * </ul>
 *
 * <p>Concrete ITs extend this class and add their own {@code @Test} methods. Each
 * container is shared at the class level to reduce startup overhead across test methods.
 *
 * <p>Profiles "stub" and "test" are activated:
 * <ul>
 *   <li>{@code stub} — enables {@code StubNidaVerificationService} and stub email sender</li>
 *   <li>{@code test} — activates {@code application-test.yml} config overrides</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles({"stub", "test"})
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("identity_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
