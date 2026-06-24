package com.smsreseller.contact.csv;

import com.google.i18n.phonenumbers.NumberParseException;
import com.smsreseller.contact.contact.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Service for streaming CSV contact import (CONT-05, CONT-06, CONT-09).
 *
 * <p>For each row:
 * <ol>
 *   <li>Normalize phone to E.164 via {@link PhoneNormalizer} — invalid → increment {@code invalid}, skip row</li>
 *   <li>Check intra-file dedup set (tracks E.164 seen in this import) — already seen → increment {@code duplicates}, skip row</li>
 *   <li>Call {@link ContactRepository#insertIfAbsent} — 0 rows affected (DB dedup) → increment {@code duplicates}</li>
 *   <li>1 row affected → increment {@code imported}</li>
 * </ol>
 *
 * <p>Invalid rows are logged (row number + raw phone) and counted; they never abort the import (T-04-CSV).
 *
 * <p>CSV cell values are treated as opaque strings: phone goes through libphonenumber validation;
 * name is stored as-is without interpretation. No formula evaluation (T-04-CSV mitigation).
 *
 * <p>Virtual threads ({@code spring.threads.virtual.enabled=true}) make synchronous streaming
 * cheap at MVP scale (Pitfall 5, D-09).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final PhoneNormalizer phoneNormalizer;
    private final ContactRepository contactRepository;

    /**
     * Streams and imports contacts from a CSV {@link InputStream}.
     *
     * <p>Expected CSV columns: {@code name}, {@code phone} (header row required).
     *
     * @param userId owner of the imported contacts
     * @param csv    input stream of the CSV file (UTF-8)
     * @return {@link ImportSummaryResponse} with imported / duplicates / invalid counts
     */
    @Transactional
    public ImportSummaryResponse importCsv(UUID userId, InputStream csv) throws IOException {
        int imported = 0;
        int duplicates = 0;
        int invalid = 0;

        // Track E.164 numbers already processed in this file to count intra-file duplicates
        Set<String> seenInFile = new HashSet<>();

        var format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (var reader = new InputStreamReader(csv, StandardCharsets.UTF_8);
             var parser = format.parse(reader)) {

            long rowNumber = 1; // 1-based (header is row 0)
            for (var record : parser) {
                rowNumber++;
                String rawPhone = record.get("phone");
                String name = record.get("name");

                // Step 1: Normalize phone to E.164
                String e164;
                try {
                    e164 = phoneNormalizer.normalizeToE164(rawPhone);
                } catch (NumberParseException ex) {
                    log.debug("CSV import: row {} — invalid phone '{}': {}", rowNumber, rawPhone, ex.getMessage());
                    invalid++;
                    continue;
                }

                // Step 2: Intra-file dedup check
                if (!seenInFile.add(e164)) {
                    duplicates++;
                    continue;
                }

                // Step 3: Upsert — ON CONFLICT (user_id, phone_e164) DO NOTHING
                int rowsAffected = contactRepository.insertIfAbsent(UUID.randomUUID(), userId, name, e164);
                if (rowsAffected == 1) {
                    imported++;
                } else {
                    // DB already has this phone under userId (cross-import dedup)
                    duplicates++;
                }
            }
        }

        return new ImportSummaryResponse(imported, duplicates, invalid);
    }
}
