package com.smsreseller.messaging.message;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Derives the TZ mobile network operator (MNO) from an E.164 phone number.
 *
 * <p>Uses libphonenumber to parse and validate the number, then maps the national
 * subscriber number prefix to a known TZ MNO label. The label is stored on
 * {@link OutboundMessage} at dispatch time (D-13 store-at-write decision) so that
 * ANLX-03 can GROUP BY operator without re-derivation at read time.
 *
 * <p>Never throws — if the number is null, malformed, or unknown, returns {@code "UNKNOWN"}.
 * This ensures DLR processing and analytics are never broken by bad phone data (T-05-04).
 *
 * <p>TZ MNO prefix mapping (E.164 +255 + 2-digit sub-prefix):
 * <ul>
 *   <li>Vodacom/M-Pesa: 74x, 75x, 76x</li>
 *   <li>Tigo/Miamungu: 71x, 65x, 67x</li>
 *   <li>Airtel: 78x, 79x, 68x, 69x</li>
 *   <li>Halotel: 62x</li>
 * </ul>
 */
@Component
@Slf4j
public class CarrierResolver {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private static final String TZ_REGION = "TZ";
    private static final String UNKNOWN = "UNKNOWN";

    /**
     * Resolve the TZ MNO from an E.164 phone number.
     *
     * @param phoneE164 E.164 phone number (e.g. "+255740000000")
     * @return MNO label ("Vodacom", "Tigo", "Airtel", "Halotel") or "UNKNOWN"
     */
    public String resolve(String phoneE164) {
        if (phoneE164 == null || phoneE164.isBlank()) {
            return UNKNOWN;
        }
        try {
            Phonenumber.PhoneNumber number = PHONE_UTIL.parse(phoneE164, TZ_REGION);
            if (!PHONE_UTIL.isValidNumber(number)) {
                return UNKNOWN;
            }

            // Only TZ numbers have meaningful MNO prefix mapping here
            if (!"TZ".equals(PHONE_UTIL.getRegionCodeForNumber(number))) {
                return UNKNOWN;
            }

            // National significant number starts after country code 255
            // E.g. +255740000000 → national number "740000000" → first 2 digits "74"
            String national = String.valueOf(number.getNationalNumber());
            if (national.length() < 2) {
                return UNKNOWN;
            }
            String prefix2 = national.substring(0, 2);

            return switch (prefix2) {
                case "74", "75", "76" -> "Vodacom";
                case "71", "65", "67" -> "Tigo";
                case "78", "79", "68", "69" -> "Airtel";
                case "62" -> "Halotel";
                default -> UNKNOWN;
            };
        } catch (NumberParseException e) {
            log.debug("CarrierResolver: could not parse phone='{}' — returning UNKNOWN", phoneE164);
            return UNKNOWN;
        } catch (Exception e) {
            log.warn("CarrierResolver: unexpected error resolving phone='{}': {}", phoneE164, e.getMessage());
            return UNKNOWN;
        }
    }
}
