---
phase: 04-contacts-messaging
verified: 2026-06-21T11:00:00Z
status: human_needed
score: 22/22 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Real upstream SMS send with a contracted provider"
    expected: "Dispatch a small campaign; real delivery receipts update per-message status to DELIVERED; permanent provider failures trigger a credit refund"
    why_human: "RealSmsProvider is @Profile(prod) — CI uses StubSmsProvider. No contracted upstream provider exists yet (Phase 0 dependency). All paths except real DLR round-trips are verified automatically."
---

# Phase 04: Contacts & Messaging Verification Report

**Phase Goal:** Users manage contact lists and send verified bulk SMS campaigns with guaranteed credit reservation before dispatch, DLX retry for failed sends, and accurate per-message delivery tracking.
**Requirements:** CONT-01..09, MESG-01..10, SNDR-02/03/04
**Verified:** 2026-06-21
**Status:** HUMAN_NEEDED — all automated checks pass; one real-provider smoke test deferred per 04-VALIDATION.md Manual-Only Verifications
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Add/edit/delete contacts; CSV import with E.164 normalization, dedup, import summary | VERIFIED | `ContactService`, `CsvImportService`, `PhoneNormalizer`; tests `ContactCrudIT`, `CsvImportIT`, `PhoneNormalizerTest` all GREEN |
| 2 | Named groups; suppression list; suppressed numbers silently excluded from all campaigns | VERIFIED | `GroupService`, `SuppressionService`; `ContactGroupIT`, `SuppressionIT` GREEN; `SendPipelineIT.suppressedNumberNotPublished` verifies exclusion at dispatch |
| 3 | Immediate bulk campaign — credits RESERVED before QUEUED; refused with clear error if insufficient | VERIFIED | `CampaignService.executeSend` calls `walletReservationClient.reserve` before setting `status=QUEUED`; `CampaignIT.insufficientCreditsBlocksQueuedTransition` GREEN |
| 4 | Schedule for future; cancel before dispatch; view per-campaign + per-message delivery status | VERIFIED | `ScheduledCampaignDispatchJob`; `CampaignService.cancel`; `CampaignService.toCampaignResponseWithCounts` + `getMessages`; `ScheduledCampaignIT`, `CampaignStatusIT`, `DeliveryTrackingIT` GREEN |
| 5 | Failed messages retried via DLX with exponential backoff; permanently undeliverable → credit refund; correct final counts | VERIFIED | `SendMessageConsumer` explicit-republish ladder; `DeadLetterConsumer` → `MessageRefundDue` outbox; `MessagingEventConsumer.onMessageRefundDue` → `lotService.creditBack`; `DlxRetryIT` GREEN |
| 6 | Request custom alphanumeric sender ID; admin approve/reject; user notified of outcome | VERIFIED | `SenderIdService`; `SenderIdAdminController` (ROLE_ADMIN guard); `OutboxRelay` publishes `SenderIdDecided`; `SenderIdIT` GREEN |

**Score:** 6/6 phase-level truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/contact-service/src/main/java/.../contact/ContactService.java` | CRUD with IDOR guard | VERIFIED | create/list/get/update/delete; userId always from JWT, never from DTO |
| `services/contact-service/src/main/java/.../csv/CsvImportService.java` | Streaming CSV import, E.164, dedup, summary | VERIFIED | `importCsv()` streams via commons-csv; intra-file + DB dedup; returns `ImportSummaryResponse(imported, duplicates, invalid)` |
| `services/contact-service/src/main/java/.../csv/PhoneNormalizer.java` | libphonenumber E.164 normalization | VERIFIED | Uses `com.google.i18n.phonenumbers`; throws `NumberParseException` on invalid |
| `services/contact-service/src/main/java/.../group/GroupService.java` | Named contact groups | VERIFIED | Group CRUD + membership management |
| `services/contact-service/src/main/java/.../suppression/SuppressionService.java` | Per-user suppression list | VERIFIED | Idempotent suppress; list endpoint |
| `services/messaging-service/src/main/java/.../campaign/CampaignService.java` | Campaign lifecycle + reserve-before-QUEUED | VERIFIED | `executeSend()` calls wallet reserve before QUEUED transition; lots zipped to messages via `ReservationResult.allocations()` |
| `services/messaging-service/src/main/java/.../message/SendMessageConsumer.java` | DLX retry ladder + idempotency guard | VERIFIED | Explicit republish+ack (Approach A); MAX_ATTEMPTS=3; hard-fail routes directly to dead; idempotency guard on SENT/FAILED status |
| `services/messaging-service/src/main/java/.../message/DeadLetterConsumer.java` | Permanent failure → FAILED + refund | VERIFIED | Sets status=FAILED; calls `messageEventPublisher.refundDue`; unique eventId in payload |
| `services/messaging-service/src/main/java/.../scheduler/ScheduledCampaignDispatchJob.java` | Scheduled campaign dispatch | VERIFIED | File exists; tested by `ScheduledCampaignIT` |
| `services/messaging-service/src/main/java/.../senderid/SenderIdService.java` | SenderID state machine + outbox event | VERIFIED | REQUESTED→APPROVED/REJECTED; `SenderIdDecided` outbox row written atomically |
| `services/messaging-service/src/main/java/.../config/RabbitMqConfig.java` | Quorum queue + DLX topology | VERIFIED | `send_queue` declared as quorum with `deliveryLimit(3)`; DLX routes to dead queue |
| `services/wallet-service/src/main/java/.../consumer/MessagingEventConsumer.java` | Idempotent CONSUME/RELEASE/REFUND | VERIFIED | Three `@RabbitListener`s with `processedEventRepository.tryInsert(eventId)` guard before ledger mutation |
| `services/contact-service/src/main/resources/db/migration/V1-V3__*.sql` | Contact, group, suppression schemas | VERIFIED | V1 (contacts), V2 (contact_groups + memberships), V3 (suppressed_numbers) |
| `services/messaging-service/src/main/resources/db/migration/V1-V4__*.sql` | Campaign, messages, sender_id, outbox schemas | VERIFIED | V1 (campaigns), V2 (outbound_messages), V3 (sender_id_requests), V4 (outbox) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `CampaignService.executeSend` | `WalletReservationClient.reserve` | Sync REST call (D-03) | WIRED | Called at line 190 before QUEUED transition at line 218 — order enforces invariant |
| `CampaignService.executeSend` | `ContactRecipientClient.getRecipientsForGroups` | Sync REST via `RestContactRecipientClient` | WIRED | Suppression applied at contact-service boundary; messaging-service trusts result (D-14) |
| `SendMessageConsumer` | `MessageEventPublisher.accepted/released/refundDue` | AMQP outbox (D-04 — no sync HTTP in consumers) | WIRED | Confirmed — no `walletReservationClient` injection in consumer; events via outbox only |
| `DeadLetterConsumer` | `MessageEventPublisher.refundDue` | AMQP outbox | WIRED | `messageEventPublisher.refundDue(messageId, userId, lotId, 1)` at line 82 |
| `MessagingEventConsumer.onMessageRefundDue` | `LotService.creditBack` | AMQP event from `messaging.events` exchange | WIRED | `processedEventRepository.tryInsert` guard → `lotService.creditBack` |
| `SenderIdService.approve/reject` | `OutboxRelay` → `messaging.events` | Transactional outbox write | WIRED | `OutboxEntry` written in same `@Transactional` block; relay publishes async |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `CampaignService.toCampaignResponseWithCounts` | `statusCounts` | `outboundMessageRepository.countByStatusForCampaign(campaignId)` | Yes — DB aggregate query | FLOWING |
| `CampaignService.getMessages` | `messages` | `outboundMessageRepository.findByCampaignId(campaignId)` | Yes — DB query with IDOR guard | FLOWING |
| `DeadLetterConsumer.onDeadLetter` | `message` | `outboundMessageRepository.findById(messageId)` | Yes — DB lookup | FLOWING |
| `MessagingEventConsumer.onMessageRefundDue` | `event.creditsToRefund` | AMQP payload from `MessageRefundDue` outbox entry | Yes — real credit count from DeadLetterConsumer | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| contact-service tests (CONT-01..09) | `./gradlew :services:contact-service:test --rerun-tasks` | BUILD SUCCESSFUL | PASS |
| messaging-service tests (MESG-01..10, SNDR-02/03/04) | `./gradlew :services:messaging-service:test --rerun-tasks` | BUILD SUCCESSFUL | PASS |
| Full suite including wallet back-compat | `./gradlew :services:contact-service:test :services:messaging-service:test` | BUILD SUCCESSFUL in 34s | PASS |

---

### Per-Requirement Verdicts

#### CONT-01 — Add contact (E.164 normalization; IDOR guard)
**Status: ACHIEVED**
`ContactService.create` accepts userId from JWT only (never from DTO). `ContactCrudIT.addContactPersistsAndIsRetrievable` GREEN.

#### CONT-02 — Edit contact (IDOR guard)
**Status: ACHIEVED**
`ContactService.update` calls `get(userId, contactId)` which uses `findByIdAndUserId` — cross-user edit returns 404. `ContactCrudIT.editContactUpdatesFields` GREEN.

#### CONT-03 — Delete contact (IDOR guard; cascade from groups)
**Status: ACHIEVED**
`ContactService.delete` uses `findByIdAndUserId`. `ContactCrudIT.deleteContactRemovesFromGroups` GREEN.

#### CONT-04 — Named groups; group membership scoped to userId
**Status: ACHIEVED**
`GroupService` + `GroupMembership` entity. `ContactGroupIT.groupMembershipIsPersisted` GREEN.

#### CONT-05 — CSV import; multipart upload; streaming parse
**Status: ACHIEVED**
`CsvImportService.importCsv` streams via `commons-csv` in `InputStreamReader`; no formula evaluation. `CsvImportIT.csvUploadInsertsContacts` GREEN.

#### CONT-06 — Dedup: duplicate phone skipped and counted
**Status: ACHIEVED**
`ContactRepository.insertIfAbsent` uses `ON CONFLICT (user_id, phone_e164) DO NOTHING`; intra-file dedup via `seenInFile` set. `CsvImportIT.duplicatePhoneSkippedAndCounted` GREEN.

#### CONT-07 — E.164 normalization via libphonenumber
**Status: ACHIEVED**
`PhoneNormalizer` uses `com.google.i18n.phonenumbers`. `PhoneNormalizerTest.tanzanianMobileNormalizesToE164` GREEN.

#### CONT-08 — Suppression list; suppressed numbers excluded from all campaigns
**Status: ACHIEVED**
`SuppressionService` + `SuppressionRepository`. Contact-service exposes `/api/v1/internal/contacts/recipients` that joins against suppression table (D-14). `SuppressionIT.suppressedNumberIsExcluded` + `SendPipelineIT.suppressedNumberNotPublished` GREEN.

#### CONT-09 — Import summary: imported / duplicates / invalid counts
**Status: ACHIEVED**
`ImportSummaryResponse(imported, duplicates, invalid)` returned from `CsvImportService.importCsv`. `CsvImportIT.importSummaryCountsAreCorrect` GREEN.

---

#### MESG-01 — Campaign scoped to userId; group IDs validated
**Status: ACHIEVED**
`CampaignService.create` assigns userId from JWT. `CampaignIT.createCampaignTargetingGroups` GREEN.

#### MESG-02 — GSM-7/UCS-2 charset detection; extended chars count as 2; part counting
**Status: ACHIEVED**
`SmsEncoder` with `SmsEncoding` enum. `SmsEncoderTest.partCountAndEncodingDetection` GREEN.

#### MESG-03 — Insufficient credits → 402/409; no AMQP published; campaign stays DRAFT
**Status: ACHIEVED**
`InsufficientCreditsException` thrown by `walletReservationClient.reserve` before `status=QUEUED` transition. `CampaignIT.insufficientCreditsBlocksQueuedTransition` confirms status != QUEUED and messages empty.

#### MESG-04 — Scheduler dispatches SCHEDULED campaigns at/after scheduledAt
**Status: ACHIEVED**
`ScheduledCampaignDispatchJob` polls SCHEDULED campaigns. `ScheduledCampaignIT.pollerDispatchesAtScheduledTime` GREEN.

#### MESG-05 — CANCELLED campaigns skipped by dispatcher
**Status: ACHIEVED**
`CampaignService.cancel` enforces state guard (only SCHEDULED/DRAFT cancellable). `ScheduledCampaignIT.cancelledCampaignNotDispatched` GREEN.

#### MESG-06 — Aggregate counts derived from outbound_messages; no cross-user data
**Status: ACHIEVED**
`CampaignService.toCampaignResponseWithCounts` queries `countByStatusForCampaign`; IDOR guard in `getMessages`. `CampaignStatusIT.aggregateStatusCountsAreCorrect` GREEN.

#### MESG-07 — Per-message status: PENDING → SENT → DELIVERED; scoped to campaign
**Status: ACHIEVED**
`SendMessageConsumer` → SENT on ACCEPTED; `DeliveryReceiptService` → DELIVERED on DLR. `DeliveryTrackingIT.perMessageStatusTransitions` GREEN.

#### MESG-08 — Dispatch response: campaignId + recipientCount + creditsReserved
**Status: ACHIEVED**
`CampaignDispatchResponse(campaignId, recipientCount, reservedCount)` returned from `executeSend`. `CampaignIT.dispatchResponseIncludesCreditsAndCount` GREEN.

#### MESG-09 — Suppressed numbers excluded before AMQP publish; no orphan messages
**Status: ACHIEVED**
Suppression applied at contact-service boundary (D-14); messaging-service publishes only the filtered list. `SendPipelineIT.suppressedNumberNotPublished` asserts 1 message persisted (not 2) when 1 number suppressed.

#### MESG-10 — Failed messages retried via DLX; permanently undeliverable → credit refund; correct counts
**Status: ACHIEVED**
HARD_FAIL → immediate dead queue route. TRANSIENT_FAIL → retry ladder (2s/4s/6s TTL in test). `DeadLetterConsumer` marks FAILED + writes `MessageRefundDue` with unique eventId. `DlxRetryIT.permanentFailureEmitsRefundDueEvent` asserts 2 FAILED messages + 2 unique `MessageRefundDue` outbox entries.

---

#### SNDR-02 — SenderIdRequest status=REQUESTED; scoped to userId
**Status: ACHIEVED**
`SenderIdService.request` persists `status=REQUESTED`. `SenderIdIT.userCanSubmitRequest` GREEN.

#### SNDR-03 — ROLE_ADMIN guard on admin endpoints; status→APPROVED
**Status: ACHIEVED**
`SenderIdAdminController` at `/api/v1/internal/**`; `SenderIdIT.adminApproveTransitionsToApproved` asserts 403 for non-admin + 200 for admin with `decidedAt` populated.

#### SNDR-04 — SenderIdDecided outbox entry on messaging.events exchange
**Status: ACHIEVED**
`SenderIdService.writeDecidedEvent` writes `OutboxEntry(eventType="SenderIdDecided")`; `OutboxRelay` relays to `messaging.events`. `SenderIdIT.senderIdDecidedEventPublished` GREEN.

---

### TDD Discipline Verification

All 8 plans follow RED-before-GREEN commit discipline:

| Plan | RED commit | GREEN commit | Notes |
|------|-----------|--------------|-------|
| 04-01 | `4cec544` placeholder failing tests | infra commits | Wave 0 |
| 04-02 | `57cbd40` real failing assertions | `9560702` + `008b743` | Two GREEN commits (Task 1, Task 2) |
| 04-03 | `7a75197` failing PhoneNormalizerTest + CsvImportIT | `be7ffc1` | |
| 04-04 | `37f344c` failing SmsEncoder + CampaignIT | `482b3f0` + `2aed25a` | |
| 04-05 | `f71fc2b` failing CampaignIT + SendPipelineIT | `d56d915` | |
| 04-06 | `c53ae9b` failing DlxRetryIT + DeliveryTrackingIT | `93b0d50` | |
| 04-07 | `a4d8f28` + `abfb5dd` RED | `1eb528f` + `39d9064` | Two sub-tasks |
| 04-08 | `507442d` failing ScheduledCampaignIT + SenderIdIT | `fe4ef05` | Resumed across session boundary; complete RED+GREEN |

**04-02 and 04-08** (flagged as resume-boundary plans): both have atomic RED+GREEN+SUMMARY sequences confirmed in git log.

---

### Credit Lifecycle Invariants

| Invariant | Evidence | Status |
|-----------|----------|--------|
| Reserve-before-QUEUED enforced | `executeSend` calls `walletReservationClient.reserve` at line 190, `status=QUEUED` set at line 218 — cannot be reordered | VERIFIED |
| No double-credit (idempotent AMQP consumers) | `MessagingEventConsumer` calls `processedEventRepository.tryInsert(eventId)` before every ledger mutation | VERIFIED |
| Permanent-fail → exactly-once refund | `MessageRefundDue` carries unique `eventId`; wallet-side `tryInsert` deduplicates; `DlxRetryIT` asserts `doesNotHaveDuplicates` on event IDs | VERIFIED |
| Suppressed numbers never published | Contact-service applies suppression at `getRecipientsForGroups`; messaging-service dispatches only returned list | VERIFIED |
| No sync HTTP in AMQP consumers (D-04) | `SendMessageConsumer` has no `walletReservationClient` injection; only `MessageEventPublisher` (outbox writes) | VERIFIED |

---

### Anti-Patterns Found

No `TBD`, `FIXME`, `XXX` markers found in any Phase 4 source file. No stub implementations. `StubSmsProvider` is intentional CI behavior (not a production stub) — `RealSmsProvider` exists as `@Profile("prod")` compile-only, consistent with the Mock-first strategy documented in the phase context.

---

### Human Verification Required

#### 1. Real Upstream SMS Round-Trip

**Test:** When a contracted upstream SMS provider is onboarded (pre-launch), switch to `@Profile("prod")`, send a small campaign (2–3 real numbers), and confirm:
- Delivery receipts arrive and update per-message status from SENT → DELIVERED
- Permanent provider failures trigger FAILED status + credit refund in the wallet ledger

**Expected:** Messages reach DELIVERED; any hard-fail triggers exactly-one credit refund credit back to the user's balance

**Why human:** `RealSmsProvider` is `@Profile("prod")` — no contracted upstream SMS provider exists at this phase. `StubSmsProvider` simulates all outcomes in CI. This is the only behavior that cannot be verified without a live provider connection.

---

### Gaps Summary

No gaps. All 22 requirement IDs (CONT-01..09, MESG-01..10, SNDR-02/03/04) are ACHIEVED with GREEN automated tests. The single human verification item is the real-provider smoke test explicitly documented as "Manual-Only" in `04-VALIDATION.md` — it is a pre-launch operational check, not a phase gap.

---

_Verified: 2026-06-21_
_Verifier: Claude (gsd-verifier)_
