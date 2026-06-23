package com.smsreseller.messaging;

// Requirement: MESG-02
// Implementing plan: 04-04

import com.smsreseller.messaging.sms.SmsEncoder;
import com.smsreseller.messaging.sms.SmsEncoding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SMS character counting and encoding detection.
 *
 * <p>Pure JUnit test — no Spring context needed. SmsEncoder is a stateless utility.
 * Tests MESG-02: GSM-7 vs UCS-2 detection and correct part count boundaries.
 */
class SmsEncoderTest {

    private final SmsEncoder encoder = new SmsEncoder();

    // ── GSM-7 detection ───────────────────────────────────────────────────────

    @Test
    void gsm7AlphanumericIsDetectedAsGsm7() {
        assertThat(encoder.detect("Hello World 123")).isEqualTo(SmsEncoding.GSM7);
    }

    @Test
    void nonGsm7CharTriggersUcs2() {
        // 'ñ' is NOT in the GSM-7 extended set — Swahili/other Unicode triggers UCS-2
        // Using 'ā' (ā = U+0101) which is outside GSM-7 basic+extended set
        assertThat(encoder.detect("Habari ā")).isEqualTo(SmsEncoding.UCS2);
    }

    @Test
    void extendedGsm7CharIsStillGsm7() {
        // '€' is in the GSM-7 extended set — encoding is GSM7 but counts as 2
        assertThat(encoder.detect("Price: €100")).isEqualTo(SmsEncoding.GSM7);
    }

    // ── charCount: extended chars count as 2 ──────────────────────────────────

    @Test
    void euroSignCountsAsTwo() {
        // "€" alone: 1 char in string, but GSM-7 extended → charCount = 2
        assertThat(encoder.charCount("€", SmsEncoding.GSM7)).isEqualTo(2);
    }

    @Test
    void regularGsm7CharCountsAsOne() {
        assertThat(encoder.charCount("A", SmsEncoding.GSM7)).isEqualTo(1);
    }

    @Test
    void ucs2CharCountEqualsStringLength() {
        // For UCS-2, each Java char counts as 1 regardless of content
        assertThat(encoder.charCount("Habari ā", SmsEncoding.UCS2)).isEqualTo(8);
    }

    // ── partCount: GSM-7 boundaries ──────────────────────────────────────────

    @Test
    void gsm7_160CharsFitsOnePart() {
        // 160 chars exactly = 1 part (single SMS limit)
        assertThat(encoder.partCount(160, SmsEncoding.GSM7)).isEqualTo(1);
    }

    @Test
    void gsm7_161CharsRequiresTwoParts() {
        // 161 chars = 2 parts (multipart; 153 chars/part)
        assertThat(encoder.partCount(161, SmsEncoding.GSM7)).isEqualTo(2);
    }

    @Test
    void gsm7_306CharsRequiresTwoParts() {
        // 153 * 2 = 306 → exactly 2 parts
        assertThat(encoder.partCount(306, SmsEncoding.GSM7)).isEqualTo(2);
    }

    @Test
    void gsm7_307CharsRequiresThreeParts() {
        // 307 = ceil(307/153) = 3 parts
        assertThat(encoder.partCount(307, SmsEncoding.GSM7)).isEqualTo(3);
    }

    // ── partCount: UCS-2 boundaries ──────────────────────────────────────────

    @Test
    void ucs2_70CharsFitsOnePart() {
        // 70 chars exactly = 1 part (single SMS limit for UCS-2)
        assertThat(encoder.partCount(70, SmsEncoding.UCS2)).isEqualTo(1);
    }

    @Test
    void ucs2_71CharsRequiresTwoParts() {
        // 71 chars = 2 parts (multipart; 67 chars/part)
        assertThat(encoder.partCount(71, SmsEncoding.UCS2)).isEqualTo(2);
    }

    @Test
    void ucs2_134CharsFitsInTwoParts() {
        // 67 * 2 = 134 → exactly 2 parts
        assertThat(encoder.partCount(134, SmsEncoding.UCS2)).isEqualTo(2);
    }

    @Test
    void ucs2_135CharsRequiresThreeParts() {
        assertThat(encoder.partCount(135, SmsEncoding.UCS2)).isEqualTo(3);
    }

    // ── Extended GSM-7 chars double-counted in part logic ─────────────────────

    @Test
    void messageWith159RegularPlusOneEuroIsTwoParts() {
        // 159 regular chars + '€' (extended = 2) = charCount 161 → 2 parts GSM-7
        String msg = "A".repeat(159) + "€";
        SmsEncoding enc = encoder.detect(msg);
        int count = encoder.charCount(msg, enc);
        int parts = encoder.partCount(count, enc);

        assertThat(enc).isEqualTo(SmsEncoding.GSM7);
        assertThat(count).isEqualTo(161);
        assertThat(parts).isEqualTo(2);
    }

    // ── StubSmsProvider outcome routing ──────────────────────────────────────
    // These lightweight checks confirm stub magic-suffix logic without a Spring context.

    @Test
    void stubProviderHardFailSuffix() {
        com.smsreseller.messaging.sms.StubSmsProvider stub = new com.smsreseller.messaging.sms.StubSmsProvider();
        com.smsreseller.messaging.sms.SmsResult result = stub.send("+2550000000001", "test", "SENDER");
        assertThat(result.outcome()).isEqualTo(com.smsreseller.messaging.sms.SmsResult.Outcome.HARD_FAIL);
    }

    @Test
    void stubProviderTransientFailSuffix() {
        com.smsreseller.messaging.sms.StubSmsProvider stub = new com.smsreseller.messaging.sms.StubSmsProvider();
        com.smsreseller.messaging.sms.SmsResult result = stub.send("+2550000000002", "test", "SENDER");
        assertThat(result.outcome()).isEqualTo(com.smsreseller.messaging.sms.SmsResult.Outcome.TRANSIENT_FAIL);
    }

    @Test
    void stubProviderAcceptedByDefault() {
        com.smsreseller.messaging.sms.StubSmsProvider stub = new com.smsreseller.messaging.sms.StubSmsProvider();
        com.smsreseller.messaging.sms.SmsResult result = stub.send("+255712345678", "test", "SENDER");
        assertThat(result.outcome()).isEqualTo(com.smsreseller.messaging.sms.SmsResult.Outcome.ACCEPTED);
    }
}
