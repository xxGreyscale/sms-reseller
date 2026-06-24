package com.smsreseller.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only Spring configuration for payment-service integration tests.
 *
 * <p>Registers {@link JwtTestHelper} as a Spring bean so integration tests can
 * {@code @Autowired} it for generating test JWTs without boilerplate.
 */
@TestConfiguration
public class PaymentTestConfiguration {

    @Bean
    public JwtTestHelper jwtTestHelper() {
        return new JwtTestHelper();
    }
}
