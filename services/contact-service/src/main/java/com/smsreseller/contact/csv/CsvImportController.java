package com.smsreseller.contact.csv;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller for CSV contact import (CONT-05).
 *
 * <p>IDOR guard (T-04-04): userId is derived exclusively from {@code auth.getToken().getSubject()}.
 * Contacts are inserted under that user only — never from request parameters.
 *
 * <p>Accepts multipart/form-data with a single {@code file} part (CSV, UTF-8).
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;

    /**
     * POST /api/v1/contacts/import
     *
     * <p>Streams the uploaded CSV, normalizes phones to E.164, deduplicates, and returns
     * an import summary with imported / duplicates / invalid counts.
     *
     * @param auth JWT authentication — userId from subject
     * @param file the uploaded CSV file (header row: name,phone)
     * @return {@link ImportSummaryResponse} — always 200 OK; invalid rows do not abort the import
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryResponse importContacts(
            JwtAuthenticationToken auth,
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard (T-04-04)
        return csvImportService.importCsv(userId, file.getInputStream());
    }
}
