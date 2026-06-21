package com.opendesk.messaging.sms;

import org.springframework.stereotype.Component;

/**
 * Pure utility for GSM-7 / UCS-2 encoding detection and SMS part counting.
 *
 * <p>MESG-02: Provides the server-side character counter logic. The Flutter/web client
 * implements the same algorithm client-side for real-time display; this class validates
 * message length before dispatch.
 *
 * <p>GSM-7 spec:
 * <ul>
 *   <li>Basic set (64 chars): standard ASCII subset + some accented Latin</li>
 *   <li>Extended set (10 chars): {, }, [, ], ~, \, |, €, ^, @ — each costs 2 GSM-7 units</li>
 *   <li>Single SMS: 160 chars. Multipart: 153 chars/part (7 chars overhead for UDH).</li>
 * </ul>
 *
 * <p>UCS-2 spec:
 * <ul>
 *   <li>Any character not in the GSM-7 basic+extended set triggers UCS-2.</li>
 *   <li>Single SMS: 70 chars. Multipart: 67 chars/part.</li>
 * </ul>
 *
 * <p>[ASSUMED] Standard SMS encoding rules, confirmed via industry sources (GSMA, 3GPP TS 23.038).
 */
@Component
public class SmsEncoder {

    // GSM-7 basic character set (63 visible chars + space + control chars)
    // Source: 3GPP TS 23.038 §6.2.1 — GSM 7-bit default alphabet
    private static final String GSM7_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ !" +
            "\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿" +
            "abcdefghijklmnopqrstuvwxyzäöñüà";

    // GSM-7 extended character set — each costs 2 GSM-7 units (ESC + char)
    // Source: 3GPP TS 23.038 §6.2.1.1 — GSM 7-bit default alphabet extension table
    private static final String GSM7_EXTENDED = "{}[]~\\|€^";

    /**
     * Detect the SMS encoding required for the given text.
     *
     * @return GSM7 if all characters are in the GSM-7 basic+extended set; UCS2 otherwise.
     */
    public SmsEncoding detect(String text) {
        for (char c : text.toCharArray()) {
            if (GSM7_BASIC.indexOf(c) < 0 && GSM7_EXTENDED.indexOf(c) < 0) {
                return SmsEncoding.UCS2;
            }
        }
        return SmsEncoding.GSM7;
    }

    /**
     * Count the number of GSM-7 or UCS-2 "units" in the text.
     *
     * <p>For GSM7: basic chars = 1 unit; extended chars = 2 units (ESC prefix).
     * For UCS2: each Java char = 1 unit.
     *
     * @param text     the message text
     * @param encoding the encoding to use (must match detect() output)
     * @return character unit count
     */
    public int charCount(String text, SmsEncoding encoding) {
        if (encoding == SmsEncoding.UCS2) {
            return text.length();
        }
        // GSM-7: accumulate 2 for extended chars, 1 for basic chars
        return (int) text.chars()
                .mapToLong(c -> GSM7_EXTENDED.indexOf(c) >= 0 ? 2L : 1L)
                .sum();
    }

    /**
     * Calculate the number of SMS parts required.
     *
     * <p>Rules:
     * <ul>
     *   <li>GSM7: single part = up to 160 units; multipart = up to 153 units/part</li>
     *   <li>UCS2: single part = up to 70 units; multipart = up to 67 units/part</li>
     * </ul>
     *
     * @param charCount the unit count from {@link #charCount}
     * @param encoding  the encoding
     * @return number of SMS parts (always &gt;= 1)
     */
    public int partCount(int charCount, SmsEncoding encoding) {
        int single = encoding == SmsEncoding.GSM7 ? 160 : 70;
        int multi  = encoding == SmsEncoding.GSM7 ? 153 : 67;
        if (charCount <= single) {
            return 1;
        }
        return (int) Math.ceil((double) charCount / multi);
    }
}
