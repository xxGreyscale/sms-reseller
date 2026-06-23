package com.smsreseller.messaging.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CarrierResolver — TZ MNO derivation from E.164 number.
 *
 * <p>These tests run WITHOUT a Spring context (plain unit tests) — CarrierResolver is a stateless
 * utility with no Spring dependencies.
 *
 * <p>TZ MNO prefix ranges (as of 2024):
 * <ul>
 *   <li>Vodacom/M-Pesa: +25574x, +25575x, +25576x</li>
 *   <li>Airtel: +25578x, +25579x, +25568x, +25569x</li>
 *   <li>Tigo/Miamungu: +25571x, +25565x, +25567x</li>
 *   <li>Halotel: +25562x</li>
 *   <li>TTCL/Simu (fixed): +25551x, +25552x, +25557x</li>
 * </ul>
 */
class CarrierResolverTest {

    private final CarrierResolver resolver = new CarrierResolver();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "+255740000000, Vodacom",
        "+255750000000, Vodacom",
        "+255760000000, Vodacom",
        "+255710000000, Tigo",
        "+255650000000, Tigo",
        "+255670000000, Tigo",
        "+255780000000, Airtel",
        "+255790000000, Airtel",
        "+255620000000, Halotel",
    })
    void validTzNumberResolvesToCarrier(String number, String expectedCarrier) {
        String result = resolver.resolve(number);
        assertThat(result).isEqualTo(expectedCarrier);
    }

    @Test
    void malformedNumberReturnsUnknown() {
        assertThat(resolver.resolve("not-a-number")).isEqualTo("UNKNOWN");
    }

    @Test
    void nullReturnsUnknown() {
        assertThat(resolver.resolve(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void emptyStringReturnsUnknown() {
        assertThat(resolver.resolve("")).isEqualTo("UNKNOWN");
    }

    @Test
    void nonTzNumberReturnsUnknown() {
        // US number — not a TZ MNO
        assertThat(resolver.resolve("+12125551234")).isEqualTo("UNKNOWN");
    }

    @Test
    void resolveNeverThrowsOnAnyInput() {
        // Threat model T-05-04: CarrierResolver must not throw — DLR/analytics must not break
        String[] badInputs = {null, "", "   ", "++255", "abc", "12345678901234567890"};
        for (String input : badInputs) {
            assertThat(resolver.resolve(input)).isEqualTo("UNKNOWN");
        }
    }
}
