package com.smsreseller.contact;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only Spring configuration for contact-service integration tests.
 *
 * <p>Provides shared test beans that are not part of the production Spring context.
 */
@TestConfiguration
public class ContactTestConfiguration {

    /**
     * JWT test helper that mints RSA-signed tokens using the test keypair.
     * Used in ContactCrudIT and other ITs to authenticate API calls.
     */
    @Bean
    public JwtTestHelper jwtTestHelper() {
        return new JwtTestHelper();
    }
}
