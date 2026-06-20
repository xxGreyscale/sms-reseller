package com.opendesk.contact;

// Requirements: CONT-05, CONT-06, CONT-09
// Implementing plan: 04-03

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for CSV contact import.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-03 (CSV Import + E.164 Normalization).
 */
class CsvImportIT extends AbstractContactIntegrationTest {

    /**
     * CONT-05: User can import contacts from a CSV file.
     * POST /api/v1/contacts/import with multipart CSV → 200 with import summary; contacts retrievable.
     */
    @Test
    void csvUploadInsertsContacts() {
        abort("Wave 0 placeholder — implement in 04-03");
    }

    /**
     * CONT-06: System deduplicates on import.
     * CSV with duplicate phone number → duplicate count = 1, contact not inserted twice.
     */
    @Test
    void duplicatePhoneSkippedAndCounted() {
        abort("Wave 0 placeholder — implement in 04-03");
    }

    /**
     * CONT-09: System shows import summary (X imported, Y duplicates, Z invalid).
     * Mixed CSV (valid + duplicate + invalid rows) → correct counts in ImportSummaryResponse.
     */
    @Test
    void importSummaryCountsAreCorrect() {
        abort("Wave 0 placeholder — implement in 04-03");
    }
}
