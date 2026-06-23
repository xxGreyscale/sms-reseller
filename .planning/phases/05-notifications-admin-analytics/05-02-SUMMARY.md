---
phase: 05-notifications-admin-analytics
plan: "02"
subsystem: messaging-service
tags: [d-12, d-13, carrier-resolver, outbox, campaign-completed, analytics, notf-05, anlx-01, anlx-03, tdd]
dependency_graph:
  requires: [05-01]
  provides: [campaign-completed-event, operator-column, anlx-01-endpoint, anlx-03-endpoint]
  affects: [05-06, 05-07]
tech_stack:
  added:
    - "libphonenumber 9.0.32 — added to messaging-service build.gradle.kts (already in version catalog from contact-service)"
  patterns:
    - "CarrierResolver: TZ MNO prefix table via libphonenumber parse + 2-digit national prefix switch; returns UNKNOWN on any parse failure (T-05-04)"
    - "CampaignCompleted outbox emit inside status!=COMPLETED guard — same @Transactional as COMPLETED flip (T-05-03 duplicate guard)"
    - "Analytics controller injects JwtAuthenticationToken in method signature (not SecurityContextHolder) — virtual-thread safe"
    - "JPQL constructor expression: new OperatorRateDto(m.operator, CAST(m.status AS string), COUNT(m)) GROUP BY operator, status"
key_files:
  created:
    - services/messaging-service/src/main/resources/db/migration/V6__add_operator_to_outbound_messages.sql
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/CarrierResolver.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/analytics/CampaignAnalyticsController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/analytics/CampaignAnalyticsService.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/analytics/CampaignStatsDto.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/analytics/OperatorRateDto.java
  modified:
    - services/messaging-service/build.gradle.kts (added libphonenumber)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/OutboundMessage.java (operator field)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/OutboundMessageRepository.java (ANLX-01/03 queries)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/DeliveryReceiptService.java (D-12 outbox emit)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java (CarrierResolver wire-in)
decisions:
  - "CampaignCompleted payload built via String.format to avoid ObjectMapper dependency in DeliveryReceiptService; consistent with existing OutboxRelay serialization pattern"
  - "JPQL constructor expression for OperatorRateDto: avoids Object[] projection boilerplate and provides type-safe results directly"
  - "operator column nullable: existing rows get backfilled by V6 UPDATE using CASE on phone_e164 prefix; new rows always populated at dispatch via CarrierResolver"
  - "Analytics scoped to JWT subject only (no ROLE_ADMIN check) per RESEARCH.md Pitfall 3 — these are user-facing owner analytics, not admin views"
metrics:
  duration: ~35 minutes
  completed: 2026-06-22
  tasks: 3
  files: 14
---

# Phase 05 Plan 02: Messaging-Service Gaps + Analytics Summary

**One-liner:** V6 operator column + CarrierResolver (TZ MNO prefix, libphonenumber) + CampaignCompleted outbox emit (D-12 gap fix, NOTF-05 unblocked) + ANLX-01/03 JWT-scoped campaign stats and operator-rate endpoints.

## What Was Built

### Task 1: operator column + CarrierResolver (D-13)

**V6 migration** (`V6__add_operator_to_outbound_messages.sql`):
- `ALTER TABLE outbound_messages ADD COLUMN IF NOT EXISTS operator VARCHAR(50)`
- Backfill `UPDATE ... SET operator = CASE WHEN phone_e164 LIKE '+25574%' THEN 'Vodacom' ...` covering Vodacom (74x/75x/76x), Tigo (71x/65x/67x), Airtel (78x/79x/68x/69x), Halotel (62x)
- `idx_outbound_messages_operator ON outbound_messages (user_id, operator)` for ANLX-03 GROUP BY

**CarrierResolver** (`CarrierResolver.java`):
- Uses `PhoneNumberUtil.getInstance()` (singleton) to parse E.164 → get national number → 2-digit prefix → switch to MNO label
- Returns `"UNKNOWN"` on null, blank, parse error, or non-TZ number — never throws (T-05-04)
- Wired into `CampaignService` at `OutboundMessage.builder()...operator(carrierResolver.resolve(phone))`

### Task 2: CampaignCompleted outbox emit (D-12 gap fix)

**DeliveryReceiptService.checkCampaignCompletion** modified:
```
if (campaign.getStatus() != CampaignStatus.COMPLETED) {
    // ... existing COMPLETED flip + log
    emitCampaignCompleted(campaign, messages);  // ← NEW
}
```

The `emitCampaignCompleted` method builds an `OutboxEntry` with:
- `aggregateType="Campaign"`, `aggregateId=campaignId`
- `eventType="CampaignCompleted"`
- `payload={"eventId","campaignId","userId","totalCount","deliveredCount","failedCount"}`

The emit is inside the same `@Transactional` as the status flip, and inside the `status!=COMPLETED` guard — so duplicate DLRs never produce a second row (T-05-03).

**Event contract for 05-06 notification consumer:**
```json
{
  "eventId": "<UUID>",
  "campaignId": "<UUID>",
  "userId": "<UUID>",
  "totalCount": 150,
  "deliveredCount": 148,
  "failedCount": 2
}
```
Exchange: `messaging.events`, routing key: `messaging.CampaignCompleted`
Consumer queue (05-06): `notification.messaging.events`

### Task 3: ANLX-01/03 analytics endpoints

**OutboundMessageRepository** new queries:
- `countByCampaignIdAndUserId` — IDOR check (if count=0, campaign not owned by user → 404)
- `countByStatusForCampaignAndUser` — per-status counts for ANLX-01
- `findOperatorRatesByUser` — JPQL constructor expression `GROUP BY operator, status` for ANLX-03

**Endpoints:**
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/analytics/campaigns/{id}/stats` | JWT (owner) | ANLX-01: {totalCount, deliveredCount, failedCount}; 404 if not owner |
| GET | `/api/v1/analytics/operator-rates` | JWT (owner) | ANLX-03: list of {operator, status, count} scoped to JWT subject |

`SecurityConfig` already covers both via `.anyRequest().authenticated()` — no special admin role needed (these are user-facing analytics, not admin views).

## TDD Gate Compliance

| Gate | Status | Commit |
|------|--------|--------|
| RED | PASSED | `e9d3e22` — CarrierResolverTest + CampaignCompletedIT + CampaignAnalyticsIT + OperatorRateAnalyticsIT as real failing assertions |
| GREEN | PASSED | `16e807d` — all tests GREEN; full 38-test suite passes |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CampaignStatus.DISPATCHING does not exist**
- **Found during:** Test compilation
- **Issue:** CampaignCompletedIT used `CampaignStatus.DISPATCHING`; enum only has DRAFT/SCHEDULED/QUEUED/SENDING/COMPLETED/CANCELLED
- **Fix:** Changed to `CampaignStatus.SENDING` in test
- **Commit:** 16e807d

**2. [Rule 1 - Bug] Campaign.builder() uses `senderId`, `name`, `body` not `senderIdName`/`message`**
- **Found during:** Test compilation
- **Issue:** Tests used wrong field names from builder (senderIdName→senderId, message→name+body)
- **Fix:** Corrected builder calls in all three IT files
- **Commit:** 16e807d

**3. [Rule 1 - Bug] findByEventType cross-test pollution**
- **Found during:** CampaignCompletedIT first run — `duplicateCompletionDoesNotEmitSecondOutboxEvent` ran first and left an outbox row; `campaignCompletionEmitsCampaignCompletedOutboxEvent` then saw 1 row instead of 0
- **Fix:** Filter outbox entries by `aggregateId.equals(campaignId.toString())` to isolate each test's campaign
- **Commit:** 16e807d

## Known Stubs

None — all production paths implemented and tested.

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-05-02 (IDOR on analytics) | MITIGATED | countByCampaignIdAndUserId returns 0 → 404; findOperatorRatesByUser WHERE userId=:userId |
| T-05-03 (duplicate CampaignCompleted) | MITIGATED | emit inside status!=COMPLETED guard; CampaignCompletedIT.duplicateCompletionDoesNotEmitSecondOutboxEvent asserts single row |
| T-05-04 (CarrierResolver throws on bad E.164) | MITIGATED | try/catch returns UNKNOWN; CarrierResolverTest.resolveNeverThrowsOnAnyInput covers null/empty/malformed |

## Self-Check: PASSED

- V6 migration contains ALTER TABLE: FOUND (`ALTER TABLE outbound_messages ADD COLUMN IF NOT EXISTS operator`)
- V6 migration contains UPDATE (backfill): FOUND (1 UPDATE statement with CASE)
- CarrierResolver.java: FOUND at services/messaging-service/src/main/java/com/smsreseller/messaging/message/CarrierResolver.java
- DeliveryReceiptService contains "CampaignCompleted" within status guard: FOUND (`eventType="CampaignCompleted"` inside `if (campaign.getStatus() != CampaignStatus.COMPLETED)` block)
- CampaignAnalyticsController contains `getSubject()`: FOUND (`auth.getToken().getSubject()`)
- SecurityConfig analytics path: covered by `.anyRequest().authenticated()` — no special admin role
- Full test suite: BUILD SUCCESSFUL (38 tests, 0 failures)
- RED commit e9d3e22 exists before GREEN commit 16e807d: CONFIRMED
