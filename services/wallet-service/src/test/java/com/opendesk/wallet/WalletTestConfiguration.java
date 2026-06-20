package com.opendesk.wallet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only Spring configuration for wallet-service integration tests.
 *
 * <p>Provides shared test beans that are not part of the production Spring context.
 * {@code @TestConfiguration} registers beans only when the test context is loaded.
 */
@TestConfiguration
public class WalletTestConfiguration {

    /**
     * JWT test helper that mints RSA-signed tokens using the test keypair.
     * Used in BalanceIT and TransactionHistoryIT to authenticate API calls.
     */
    @Bean
    public JwtTestHelper jwtTestHelper() {
        return new JwtTestHelper();
    }
}
