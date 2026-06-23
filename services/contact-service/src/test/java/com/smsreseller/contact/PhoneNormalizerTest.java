package com.smsreseller.contact;

// Requirement: CONT-07
// Implementing plan: 04-03

import com.smsreseller.contact.csv.PhoneNormalizer;
import com.google.i18n.phonenumbers.NumberParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Tanzania E.164 phone number normalization (CONT-07).
 *
 * <p>Plain JUnit test — no Spring context needed (PhoneNormalizer is a stateless utility).
 */
class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    /**
     * CONT-07: System normalizes TZ numbers to E.164.
     * 07xx → +255xx; 06xx → +255xx; already-E.164 (+255xx) unchanged; invalid rejected.
     */
    @Test
    void tanzanianMobileNormalizesToE164() throws NumberParseException {
        // 07xx local format → +255 7xx (Vodacom/Tigo/Airtel mobile)
        assertThat(normalizer.normalizeToE164("0712345678")).isEqualTo("+255712345678");

        // 06xx local format → +255 6xx (Halotel mobile)
        assertThat(normalizer.normalizeToE164("0612345678")).isEqualTo("+255612345678");

        // Already E.164 — must pass through unchanged
        assertThat(normalizer.normalizeToE164("+255712345678")).isEqualTo("+255712345678");

        // Invalid number (too short) → must throw
        assertThatThrownBy(() -> normalizer.normalizeToE164("12"))
                .isInstanceOf(NumberParseException.class);
    }
}
