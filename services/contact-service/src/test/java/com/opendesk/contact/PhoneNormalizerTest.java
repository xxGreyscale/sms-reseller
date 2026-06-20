package com.opendesk.contact;

// Requirement: CONT-07
// Implementing plan: 04-03

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Unit tests for Tanzania E.164 phone number normalization.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-03 (CSV Import + E.164 Normalization).
 *
 * <p>Note: This is a plain JUnit test (NOT extending AbstractContactIntegrationTest)
 * because PhoneNormalizer is a pure utility class with no Spring context dependency.
 */
class PhoneNormalizerTest {

    /**
     * CONT-07: System normalizes TZ numbers to E.164.
     * 07xx → +255xx; 06xx → +255xx; already-E.164 (+255xx) unchanged; invalid rejected.
     */
    @Test
    void tanzanianMobileNormalizesToE164() {
        abort("Wave 0 placeholder — implement in 04-03");
    }
}
