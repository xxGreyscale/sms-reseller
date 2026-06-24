package com.smsreseller.contact;

// Requirements: CONT-05, CONT-06, CONT-09
// Implementing plan: 04-03

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CSV contact import (CONT-05, CONT-06, CONT-09).
 *
 * <p>Tests POST /api/v1/contacts/import with multipart CSV files.
 *
 * <p>Phone numbers use valid TZ prefixes (071x/062x/074x/075x) confirmed via libphonenumber.
 * The 072x prefix is NOT a valid TZ mobile prefix and is intentionally avoided.
 */
class CsvImportIT extends AbstractContactIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtTestHelper jwt;

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> importCsv(String token, String csvContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource csvResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() { return "contacts.csv"; }
        };
        body.add("file", csvResource);

        var resp = rest.exchange(
                "/api/v1/contacts/import",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    // ── CONT-05 ───────────────────────────────────────────────────────────────

    /**
     * CONT-05: Upload CSV with 3 valid rows → 3 contacts persisted, import summary shows 3 imported.
     */
    @Test
    void csvUploadInsertsContacts() {
        String userId = UUID.randomUUID().toString();
        String token = jwt.createToken(userId);

        // 071x prefix is valid TZ Vodacom/Airtel mobile
        String csv = "name,phone\n" +
                     "Alice,0711000001\n" +
                     "Bob,0711000002\n" +
                     "Carol,0711000003\n";

        Map<String, Object> summary = importCsv(token, csv);

        assertThat(summary.get("imported")).isEqualTo(3);
        assertThat(summary.get("duplicates")).isEqualTo(0);
        assertThat(summary.get("invalid")).isEqualTo(0);

        // Verify contacts are retrievable via GET
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        var listResp = rest.exchange("/api/v1/contacts", HttpMethod.GET,
                new HttpEntity<>(getHeaders), Map.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var content = (java.util.List<?>) listResp.getBody().get("content");
        assertThat(content).hasSize(3);
    }

    // ── CONT-06 ───────────────────────────────────────────────────────────────

    /**
     * CONT-06: CSV with duplicate phone number (once in file, once already in DB)
     * → duplicate skipped; duplicate count reflects both skips; contact not inserted twice.
     */
    @Test
    void duplicatePhoneSkippedAndCounted() {
        String userId = UUID.randomUUID().toString();
        String token = jwt.createToken(userId);

        // First import: insert one contact (074x is a valid TZ prefix)
        String firstCsv = "name,phone\nAlice,0741111111\n";
        importCsv(token, firstCsv);

        // Second import:
        //   - 0741111111 already in DB → counted as duplicate (skips DB insert, still added to seenInFile)
        //   - 0741111111 again in file → intra-file duplicate
        //   - 0742111111 is new → imported
        String secondCsv = "name,phone\n" +
                           "Alice-dup1,0741111111\n" +  // already in DB → duplicate
                           "Alice-dup2,0741111111\n" +  // intra-file duplicate
                           "Bob,0742111111\n";          // new → imported

        Map<String, Object> summary = importCsv(token, secondCsv);

        assertThat(summary.get("imported")).isEqualTo(1);
        assertThat(summary.get("duplicates")).isEqualTo(2);
        assertThat(summary.get("invalid")).isEqualTo(0);
    }

    // ── CONT-09 ───────────────────────────────────────────────────────────────

    /**
     * CONT-09: Mixed CSV (valid + duplicate + invalid rows) → correct counts; invalid rows
     * do NOT abort the import.
     */
    @Test
    void importSummaryCountsAreCorrect() {
        String userId = UUID.randomUUID().toString();
        String token = jwt.createToken(userId);

        // Pre-seed one contact that will appear as a duplicate (075x is a valid TZ prefix)
        String seedCsv = "name,phone\nSeed,0752111111\n";
        importCsv(token, seedCsv);

        // Mixed file:
        //   ValidA (0753111111) — new → imported
        //   ValidB (0754111111) — new → imported
        //   Dup    (0752111111) — already in DB → duplicate
        //   Bad    ("12")       — invalid number → invalid (does not abort import)
        String mixedCsv = "name,phone\n" +
                          "ValidA,0753111111\n" +
                          "ValidB,0754111111\n" +
                          "Dup,0752111111\n" +
                          "Bad,12\n";

        Map<String, Object> summary = importCsv(token, mixedCsv);

        assertThat(summary.get("imported")).isEqualTo(2);
        assertThat(summary.get("duplicates")).isEqualTo(1);
        assertThat(summary.get("invalid")).isEqualTo(1);
    }
}
