package com.smsreseller.contact.csv;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.stereotype.Component;

/**
 * Normalizes raw phone numbers to E.164 format using libphonenumber (CONT-07).
 *
 * <p>Tanzania region code "TZ" is used for parsing, which handles:
 * <ul>
 *   <li>07xx local format → +255 7xx (e.g. 0712345678 → +255712345678)</li>
 *   <li>06xx local format → +255 6xx (e.g. 0612345678 → +255612345678)</li>
 *   <li>+255xxx already in E.164 format — passes through unchanged</li>
 * </ul>
 *
 * <p>Invalid numbers (wrong length, non-TZ format) throw {@link NumberParseException}.
 * Callers (CsvImportService) must catch and count as invalid rows — never abort the import.
 *
 * <p>Thread-safe: {@link PhoneNumberUtil#getInstance()} returns a singleton; the instance
 * is stateless and safe for concurrent use across virtual threads.
 */
@Component
public class PhoneNormalizer {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    private static final String TZ_REGION = "TZ";

    /**
     * Normalizes {@code raw} to E.164 format.
     *
     * @param raw the raw phone string (e.g. "0712345678", "+255712345678")
     * @return E.164 string (e.g. "+255712345678")
     * @throws NumberParseException if the number cannot be parsed or is invalid for Tanzania
     */
    public String normalizeToE164(String raw) throws NumberParseException {
        var parsed = UTIL.parse(raw.strip(), TZ_REGION);
        if (!UTIL.isValidNumber(parsed)) {
            throw new NumberParseException(
                    NumberParseException.ErrorType.NOT_A_NUMBER,
                    "Invalid TZ number: " + raw);
        }
        return UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
    }
}
