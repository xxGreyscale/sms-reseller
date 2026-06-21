package com.opendesk.contact;

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

        String csv = "name,phone\n" +
                     "Alice,0712000001\n" +
                     "Bob,0712000002\n" +
                     "Carol,0712000003\n";

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

        // First import: insert one contact
        String firstCsv = "name,phone\nAlice,0722111111\n";
        importCsv(token, firstCsv);

        // Second import: same phone again (already in DB) + that phone repeated in file
        String secondCsv = "name,phone\n" +
                           "Alice-dup1,0722111111\n" +  // already in DB → duplicate
                           "Alice-dup2,0722111111\n" +  // intra-file duplicate → duplicate
                           "Bob,0733222222\n";          // new → imported

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

        // Pre-seed one contact that will appear as a duplicate
        String seedCsv = "name,phone\nSeed,0744333333\n";
        importCsv(token, seedCsv);

        // Mixed file: 2 new valid, 1 duplicate (already in DB), 1 invalid
        String mixedCsv = "name,phone\n" +
                          "ValidA,0755444444\n" +  // new → imported
                          "ValidB,0766555555\n" +  // new → imported
                          "Dup,0744333333\n" +     // already in DB → duplicate
                          "Bad,12\n";              // invalid number → invalid

        Map<String, Object> summary = importCsv(token, mixedCsv);

        assertThat(summary.get("imported")).isEqualTo(2);
        assertThat(summary.get("duplicates")).isEqualTo(1);
        assertThat(summary.get("invalid")).isEqualTo(1);
    }
}
