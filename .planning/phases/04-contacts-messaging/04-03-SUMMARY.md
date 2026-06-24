---
phase: 04-contacts-messaging
plan: "03"
subsystem: contact-service
tags: [csv-import, libphonenumber, e164, dedup, commons-csv, tdd]
dependency_graph:
  requires: [04-02]
  provides: [CONT-05, CONT-06, CONT-07, CONT-09]
  affects: [04-05]
tech_stack:
  added: []
  patterns:
    - "PhoneNormalizer: PhoneNumberUtil.getInstance() singleton + parse(raw, 'TZ') + isValidNumber + format E164"
    - "CsvImportService: CSVFormat.DEFAULT with setHeader/setSkipHeaderRecord, intra-file dedup via HashSet<String>"
    - "insertIfAbsent: @Modifying native INSERT ... ON CONFLICT (user_id, phone_e164) DO NOTHING; 1=inserted 0=duplicate"
    - "IDOR guard: userId from JwtAuthenticationToken.getToken().getSubject() in CsvImportController"
key_files:
  created:
    - services/contact-service/src/main/java/com/smsreseller/contact/csv/PhoneNormalizer.java
    - services/contact-service/src/main/java/com/smsreseller/contact/csv/ImportSummaryResponse.java
    - services/contact-service/src/main/java/com/smsreseller/contact/csv/CsvImportService.java
    - services/contact-service/src/main/java/com/smsreseller/contact/csv/CsvImportController.java
  modified:
    - services/contact-service/src/main/java/com/smsreseller/contact/contact/Contact.java
    - services/contact-service/src/test/java/com/smsreseller/contact/PhoneNormalizerTest.java
    - services/contact-service/src/test/java/com/smsreseller/contact/CsvImportIT.java
decisions:
  - "Contact entity @UniqueConstraint added for Hibernate DDL (create-drop in tests) to support ON CONFLICT clause — Flyway migration already had uq_contact_user_phone but JPA entity did not declare it, causing 42P10 in test runs"
  - "Intra-file dedup via HashSet<String> in CsvImportService (per D-09): tracks E.164 seen in this import; avoids double-increment of both imported and duplicate for same phone appearing twice in one CSV"
  - "Invalid TZ phone numbers in test fixtures: 072x prefix is not a valid TZ mobile prefix per libphonenumber 9.0.32 — replaced with 071x, 074x, 075x which are verified valid"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-21"
  tasks_completed: 1
  files_changed: 7
---

# Phase 4 Plan 03: CSV Import — E.164 Normalization, Dedup, Import Summary

CSV contact import via POST /api/v1/contacts/import: streams multipart CSV with commons-csv, normalizes each phone to E.164 via libphonenumber (TZ region), deduplicates within the file and against the existing account, and returns an import summary with exact imported/duplicates/invalid counts.

## What Was Built

### CSV Import Pipeline (CONT-05, CONT-06, CONT-07, CONT-09)

- `PhoneNormalizer`: Spring `@Component` wrapping `PhoneNumberUtil.getInstance()` (singleton). `normalizeToE164(raw)` parses with region `"TZ"`, validates with `isValidNumber`, formats to `E164`. Throws `NumberParseException` on invalid input. Thread-safe under virtual threads.
- `ImportSummaryResponse`: `record(int imported, int duplicates, int invalid)` — the DTO returned by the import endpoint.
- `CsvImportService`: Streams CSV with `CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)`. Per row: normalize phone → intra-file dedup check (HashSet) → `insertIfAbsent` (native INSERT ON CONFLICT DO NOTHING). Increments one of `imported/duplicates/invalid`. Invalid rows logged (row number + raw phone) and never abort the import (T-04-CSV).
- `CsvImportController`: `POST /api/v1/contacts/import` with `@RequestParam MultipartFile file`. userId from `auth.getToken().getSubject()` exclusively (T-04-04 IDOR guard). Returns `ImportSummaryResponse` 200 OK.
- `ContactRepository.insertIfAbsent`: Already present from 04-02 — native `@Modifying` INSERT ... ON CONFLICT (user_id, phone_e164) DO NOTHING returning rows affected.

### Tests

- `PhoneNormalizerTest.tanzanianMobileNormalizesToE164` (CONT-07): GREEN — 0712345678→+255712345678, 0612345678→+255612345678, +255712345678 pass-through, "12" throws.
- `CsvImportIT.csvUploadInsertsContacts` (CONT-05): GREEN — 3-row CSV, imported=3, contacts retrievable via GET.
- `CsvImportIT.duplicatePhoneSkippedAndCounted` (CONT-06): GREEN — pre-seeded phone + two occurrences in file → imported=1, duplicates=2.
- `CsvImportIT.importSummaryCountsAreCorrect` (CONT-09): GREEN — mixed file → imported=2, duplicates=1, invalid=1.
- Full contact-service test suite GREEN (all prior ITs unaffected).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Contact entity missing @UniqueConstraint for Hibernate DDL**
- **Found during:** GREEN implementation (CsvImportIT first run)
- **Issue:** `insertIfAbsent` uses `ON CONFLICT (user_id, phone_e164) DO NOTHING`. In tests, Flyway is disabled (`ddl-auto=create-drop`). Hibernate generates the `contacts` table from the JPA entity, which lacked a `@UniqueConstraint` — so the unique index `uq_contact_user_phone` was never created. PostgreSQL error: `42P10 there is no unique or exclusion constraint matching the ON CONFLICT specification`.
- **Fix:** Added `@UniqueConstraint(name = "uq_contact_user_phone", columnNames = {"user_id", "phone_e164"})` to `@Table` on the `Contact` entity. No behavior change in production (Flyway creates it from V1 migration); test environment now creates it correctly.
- **Files modified:** `Contact.java`
- **Commit:** be7ffc1

**2. [Rule 1 - Bug] Test phone numbers used 072x prefix which is invalid in Tanzania**
- **Found during:** GREEN test run debugging
- **Issue:** `0722111111` is not a valid TZ mobile number per libphonenumber 9.0.32 (072x is not an assigned prefix). The seed import in `duplicatePhoneSkippedAndCounted` and `importSummaryCountsAreCorrect` silently counted the seed as `invalid`, causing the second import to show 0 duplicates.
- **Fix:** Replaced all 072x test numbers with verified valid TZ prefixes: 071x (Vodacom), 074x (Tigo), 075x (Airtel). Prefix validity confirmed via `PhoneNumberUtil.isValidNumber` before committing.
- **Files modified:** `CsvImportIT.java`
- **Commit:** be7ffc1

## Known Stubs

None. All data flows are wired end-to-end: CSV → PhoneNormalizer → ContactRepository → ImportSummaryResponse.

## Threat Flags

None beyond the plan's threat model:
- T-04-CSV: CSV cell values are opaque strings; phone validated by libphonenumber; name stored as-is. No formula evaluation.
- T-04-04: userId from JWT subject only; contacts inserted under that user.

## TDD Gate Compliance

- RED gate (7a75197): `test(04-03): RED — failing PhoneNormalizerTest and CsvImportIT` — compilation error (PhoneNormalizer class absent).
- GREEN gate (be7ffc1): `feat(04-03): CSV import — E.164 normalization, dedup, import summary` — all 4 tests GREEN, full suite GREEN.

## Self-Check: PASSED

- PhoneNormalizer.java: FOUND at services/contact-service/src/main/java/com/smsreseller/contact/csv/PhoneNormalizer.java
- ImportSummaryResponse.java: FOUND at services/contact-service/src/main/java/com/smsreseller/contact/csv/ImportSummaryResponse.java
- CsvImportService.java: FOUND at services/contact-service/src/main/java/com/smsreseller/contact/csv/CsvImportService.java
- CsvImportController.java: FOUND at services/contact-service/src/main/java/com/smsreseller/contact/csv/CsvImportController.java
- RED commit 7a75197: VERIFIED
- GREEN commit be7ffc1: VERIFIED
- Full contact-service test suite: BUILD SUCCESSFUL
