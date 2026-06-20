package com.opendesk.messaging;

// Requirement: MESG-02
// Implementing plan: 04-04

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Unit tests for SMS character counting and encoding detection.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-04 (Campaign Create + Send Pipeline).
 *
 * <p>Note: This is a plain JUnit test (NOT extending AbstractMessagingIntegrationTest)
 * because SmsEncoder is a pure utility class with no Spring context dependency.
 */
class SmsEncoderTest {

    /**
     * MESG-02: Campaign composer shows real-time character counter with SMS part count and UCS-2 warning.
     * Assertions:
     * - GSM-7: 160 chars → 1 part; 161 chars → 2 parts; extended char (€) counts as 2
     * - UCS-2: any non-GSM-7 char (e.g. Swahili-specific unicode) triggers UCS-2; 70 chars = 1 part, 71 = 2 parts
     * - detect() correctly identifies GSM7 vs UCS2 encoding
     */
    @Test
    void partCountAndEncodingDetection() {
        abort("Wave 0 placeholder — implement in 04-04");
    }
}
