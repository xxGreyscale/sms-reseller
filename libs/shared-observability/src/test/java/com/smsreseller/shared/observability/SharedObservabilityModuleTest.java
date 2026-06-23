package com.smsreseller.shared.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 0 placeholder test for the shared-observability module.
 *
 * <p>This test exists to verify that the module compiles and that the OTel,
 * Prometheus, and Sentry dependencies resolve at test compile time, giving
 * {@code ./gradlew test} a green baseline before any real observability
 * autoconfiguration is added in Phase 2+.
 */
class SharedObservabilityModuleTest {

    @Test
    void moduleCompiles() {
        // Wave 0 placeholder — proves the module compiles and JUnit 5 resolves
        // with all OTel/Prometheus/Sentry deps on the classpath
        assertTrue(true);
    }
}
