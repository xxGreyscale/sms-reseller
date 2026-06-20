---
phase: 4
slug: contacts-messaging
status: executing
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-21
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + RabbitMQ via `@ServiceConnection` / `@DynamicPropertySource`), Spring Boot Test |
| **Config file** | `services/contact-service/.../AbstractContactIntegrationTest.java` + `services/messaging-service/.../AbstractMessagingIntegrationTest.java` (Wave 0 installed) |
| **Quick run command** | `./gradlew :services:contact-service:test :services:messaging-service:test` |
| **Full suite command** | `./gradlew :services:contact-service:test :services:messaging-service:test :services:wallet-service:test` |
| **Estimated runtime** | ~90–180 seconds (Testcontainers + RabbitMQ DLX TTL ladders shortened in test config) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (unit tests for touched module)
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

> One row per Phase 4 requirement ID. Wave 0 placeholders use `Assumptions.abort(...)` — they report as SKIPPED/ABORTED, not failed.
> Shortened DLX TTL ladder in `application-test.yml` (2s/4s/6s) keeps DlxRetryIT within 180s budget.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| contact-crud-1 | 04-02 | 1 | CONT-01 | T-04-SC (input validation on contact creation) | Phone normalizes to E.164; userId from JWT only | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.ContactCrudIT.addContactPersistsAndIsRetrievable"` | ✅ | ❌ red (W0) |
| contact-crud-2 | 04-02 | 1 | CONT-02 | — | IDOR guard: only owner can edit | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.ContactCrudIT.editContactUpdatesFields"` | ✅ | ❌ red (W0) |
| contact-crud-3 | 04-02 | 1 | CONT-03 | — | IDOR guard: only owner can delete; cascade to groups | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.ContactCrudIT.deleteContactRemovesFromGroups"` | ✅ | ❌ red (W0) |
| contact-group-1 | 04-02 | 1 | CONT-04 | — | Group membership scoped to userId | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.ContactGroupIT.groupMembershipIsPersisted"` | ✅ | ❌ red (W0) |
| csv-import-1 | 04-03 | 1 | CONT-05 | T-04-SC (file upload validation) | Multipart CSV; streaming parse; no path traversal | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.CsvImportIT.csvUploadInsertsContacts"` | ✅ | ❌ red (W0) |
| csv-import-2 | 04-03 | 1 | CONT-06 | — | INSERT ON CONFLICT DO NOTHING deduplication | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.CsvImportIT.duplicatePhoneSkippedAndCounted"` | ✅ | ❌ red (W0) |
| phone-norm-1 | 04-03 | 1 | CONT-07 | — | libphonenumber validates TZ numbering plan | Unit | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.PhoneNormalizerTest.tanzanianMobileNormalizesToE164"` | ✅ | ❌ red (W0) |
| suppression-1 | 04-02 | 1 | CONT-08 | — | Suppression list scoped per userId; excluded at dispatch | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.SuppressionIT.suppressedNumberIsExcluded"` | ✅ | ❌ red (W0) |
| csv-import-3 | 04-03 | 1 | CONT-09 | — | Import summary counts: imported/duplicates/invalid | IT | `./gradlew :services:contact-service:test --tests "com.opendesk.contact.CsvImportIT.importSummaryCountsAreCorrect"` | ✅ | ❌ red (W0) |
| campaign-1 | 04-04 | 1 | MESG-01 | — | Campaign scoped to userId; group IDs validated | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.CampaignIT.createCampaignTargetingGroups"` | ✅ | ❌ red (W0) |
| sms-encoder-1 | 04-04 | 1 | MESG-02 | — | GSM-7 charset; extended chars count as 2; UCS-2 detection | Unit | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.SmsEncoderTest.partCountAndEncodingDetection"` | ✅ | ❌ red (W0) |
| campaign-2 | 04-04 | 1 | MESG-03 | — | Insufficient credits → 402/409; no AMQP published | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.CampaignIT.insufficientCreditsBlocksQueuedTransition"` | ✅ | ❌ red (W0) |
| sched-campaign-1 | 04-05 | 2 | MESG-04 | — | Scheduler only dispatches SCHEDULED campaigns at/after scheduledAt | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.ScheduledCampaignIT.pollerDispatchesAtScheduledTime"` | ✅ | ❌ red (W0) |
| sched-campaign-2 | 04-05 | 2 | MESG-05 | — | CANCELLED campaigns skipped by dispatcher | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.ScheduledCampaignIT.cancelledCampaignNotDispatched"` | ✅ | ❌ red (W0) |
| campaign-status-1 | 04-05 | 2 | MESG-06 | — | Aggregate counts derived from outbound_messages; no cross-user data | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.CampaignStatusIT.aggregateStatusCountsAreCorrect"` | ✅ | ❌ red (W0) |
| delivery-1 | 04-06 | 2 | MESG-07 | — | Per-message status: PENDING → SENT → DELIVERED; scoped to campaign | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.DeliveryTrackingIT.perMessageStatusTransitions"` | ✅ | ❌ red (W0) |
| campaign-3 | 04-04 | 1 | MESG-08 | — | Dispatch response: campaignId + recipientCount + creditsReserved | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.CampaignIT.dispatchResponseIncludesCreditsAndCount"` | ✅ | ❌ red (W0) |
| pipeline-1 | 04-06 | 2 | MESG-09 | — | Suppressed numbers excluded before AMQP publish; no orphan messages | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.SendPipelineIT.suppressedNumberNotPublished"` | ✅ | ❌ red (W0) |
| dlx-retry-1 | 04-06 | 2 | MESG-10 | — | default-requeue-rejected=false; delivery-limit=3; dead queue + refund event | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.DlxRetryIT.permanentFailureEmitsRefundDueEvent"` | ✅ | ❌ red (W0) |
| sender-id-1 | 04-08 | 3 | SNDR-02 | — | SenderIdRequest status=REQUESTED; scoped to userId | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.SenderIdIT.userCanSubmitRequest"` | ✅ | ❌ red (W0) |
| sender-id-2 | 04-08 | 3 | SNDR-03 | — | ROLE_ADMIN guard on /api/v1/internal/**; status→APPROVED | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.SenderIdIT.adminApproveTransitionsToApproved"` | ✅ | ❌ red (W0) |
| sender-id-3 | 04-08 | 3 | SNDR-04 | — | SenderIdDecided outbox entry on messaging.events exchange | IT | `./gradlew :services:messaging-service:test --tests "com.opendesk.messaging.SenderIdIT.senderIdDecidedEventPublished"` | ✅ | ❌ red (W0) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `contact-service` + `messaging-service` Gradle modules + `AbstractIntegrationTest` bases (PG16 + RabbitMQ Testcontainers), mirroring Phase 3
- [x] Placeholder failing IT per requirement ID (CONT-01..09, MESG-01..10, SNDR-02/03/04) so the validation map is non-empty
- [x] Shortened DLX TTL ladder in messaging-service test config (2s/4s/6s via application-test.yml) for DlxRetryIT timing

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real upstream SMS send + real delivery receipt | MESG-07, MESG-10 | Requires a contracted upstream SMS provider (Phase 0 dependency); stub simulates DLRs + hard/transient fails in CI | When provider is signed: switch to `@Profile("prod")`, send a small campaign, confirm real delivery receipts update per-message status and permanent failures refund credits |

*All other phase behaviors have automated verification via the stub provider + Testcontainers.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 180s (shortened DLX TTL ladder in test config)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** Wave 0 complete — ready for Wave 1 implementation plans
