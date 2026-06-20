package com.opendesk.messaging;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only Spring configuration for messaging-service integration tests.
 *
 * <p>Provides shared test beans that are not part of the production Spring context.
 */
@TestConfiguration
public class MessagingTestConfiguration {

    /**
     * JWT test helper that mints RSA-signed tokens using the test keypair.
     * Used in CampaignIT, SenderIdIT, and other ITs to authenticate API calls.
     * Supports both user tokens and admin tokens (for sender-ID approval).
     */
    @Bean
    public JwtTestHelper jwtTestHelper() {
        return new JwtTestHelper();
    }
}
