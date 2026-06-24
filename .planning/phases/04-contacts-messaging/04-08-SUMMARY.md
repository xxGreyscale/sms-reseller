---
phase: 04-contacts-messaging
plan: "08"
subsystem: messaging-service
tags: [scheduler, campaign, sender-id, outbox, admin, tdd]
dependency_graph:
  requires: ["04-05"]
  provides: [scheduled-campaign-dispatch, campaign-cancel, sender-id-state-machine, sender-id-decided-event]
  affects: [messaging-service]
tech_stack:
  added: []
  patterns: [db-poll-scheduler, testable-dispatch-delegate, outbox-event, jwt-roles-converter]
key_files:
  created:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/scheduler/ScheduledCampaignDispatchJob.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdRepository.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdService.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdAdminController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdDto.java
  modified:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/config/SecurityConfig.java
    - services/messaging-service/src/test/java/com/smsreseller/messaging/SenderIdIT.java
decisions:
  - "JWT roles claim converter added to SecurityConfig so hasRole(ADMIN) evaluates correctly from roles array in token"
  - "ScheduledCampaignDispatchJob uses testable dispatch(Instant) delegate pattern (mirrors ReconciliationJob)"
  - "SenderIdDecided outbox event uses LinkedHashMap payload for deterministic JSON field order"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-21T10:43:19Z"
  tasks_completed: 2
  files_changed: 10
---

# Phase 4 Plan 08: Scheduled Campaign Dispatch + Sender-ID State Machine Summary

**One-liner:** DB-polled scheduled campaign dispatcher with cancel, plus REQUESTED→APPROVED/REJECTED sender-ID state machine with SenderIdDecided outbox event.

## Tasks Completed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| RED  | ScheduledCampaignIT + SenderIdIT (failing) | 507442d | Done (prior executor) |
| GREEN | Task 1: Scheduled campaign dispatch + cancellation | fe4ef05 | Done |
| GREEN | Task 2: Sender-ID request + admin approve/reject + outbox event | fe4ef05 | Done |

## What Was Built

### Task 1 — Scheduled Campaign Dispatch + Cancellation (MESG-04, MESG-05)

**ScheduledCampaignDispatchJob** (`scheduler/` package):
- `@Scheduled(fixedDelay=30_000)` entry-point `schedule()` delegates to `dispatch(Instant now)`
- `dispatch()` calls `campaignRepository.findByStatusAndScheduledAtBefore(SCHEDULED, now, PageRequest.of(0,50))`
- Per-item try/catch: one campaign failure does not abort the batch
- Delegates to `CampaignService.executeSend()` — reuses the full reserve→QUEUED→publish pipeline from 04-05

**CampaignService.cancel()** (added):
- IDOR-safe: looks up by `(id, userId)` — cross-user attempts get 404
- Idempotent: CANCELLED→CANCELLED is a no-op
- Guards against invalid transitions (only SCHEDULED/DRAFT can be cancelled)

**CampaignController** (cancel endpoint added):
- `POST /api/v1/campaigns/{id}/cancel` → 200 with updated campaign response

### Task 2 — Sender-ID State Machine + Decision Event (SNDR-02, SNDR-03, SNDR-04)

**SenderIdRepository**: `findByIdAndUserId` (IDOR guard), `findByUserId` (own listing), standard `findById` for admin

**SenderIdService**:
- `request(userId, senderName)` → persists REQUESTED
- `approve(requestId)` → REQUESTED→APPROVED + `decidedAt` + SenderIdDecided outbox row
- `reject(requestId, reason)` → REQUESTED→REJECTED + `rejectReason` + `decidedAt` + SenderIdDecided outbox row
- All transitions idempotent-guarded (non-REQUESTED source throws)

**SenderIdController** (`/api/v1/sender-ids/requests`):
- `POST` with `@Valid SubmitRequest` — `@Size(max=11)` + `@Pattern(regexp="[A-Za-z0-9]+")`
- `GET` — own requests list

**SenderIdAdminController** (`/api/v1/internal/sender-ids/{id}/approve|reject`):
- Secured by existing SecurityConfig `/api/v1/internal/**` → `hasRole("ADMIN")`
- Non-admin → 403 before reaching controller

**SenderIdDto**: `SubmitRequest` (with validation), `RejectRequest`, `SenderIdResponse`

**SecurityConfig — JWT roles converter added**:
- Custom `JwtAuthenticationConverter` maps the `roles` claim array to Spring Security `SimpleGrantedAuthority`
- Without this, `hasRole("ADMIN")` would never match because the default converter reads `scope`/`scp` only

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SenderIdIT type mismatch: long vs int in isGreaterThan**
- **Found during:** GREEN compile attempt
- **Issue:** `outboxCountBefore` was typed as `long` (from `.count()`) but `assertThat(decidedEntries.size())` returns `AbstractIntegerAssert` which doesn't accept `long`
- **Fix:** Cast `outboxCountBefore` to `(int)` at the assertion site
- **Files modified:** `SenderIdIT.java`
- **Commit:** fe4ef05

**2. [Rule 2 - Missing critical functionality] JWT roles claim converter missing**
- **Found during:** Security design review before implementation
- **Issue:** Spring Security's default JWT converter reads `scope`/`scp` claims only; the identity-service issues `roles: ["ROLE_ADMIN"]` — without a custom converter, `hasRole("ADMIN")` would always deny, making the 403 test pass but the approve test fail with 403
- **Fix:** Added `JwtAuthenticationConverter` bean in SecurityConfig mapping the `roles` claim
- **Files modified:** `SecurityConfig.java`
- **Commit:** fe4ef05

## Known Stubs

None — all endpoints are wired to real DB-backed services.

## Threat Surface Scan

No new network endpoints beyond what the plan specified. The `/api/v1/internal/**` guard was already in SecurityConfig from 04-04; this plan adds the controllers behind it. No new trust boundaries introduced.

## Self-Check: PASSED

- `ScheduledCampaignDispatchJob.java` exists: FOUND
- `SenderIdService.java` exists: FOUND
- `SenderIdAdminController.java` exists: FOUND
- GREEN commit fe4ef05: FOUND (git log confirms)
- Tests GREEN: `BUILD SUCCESSFUL` confirmed above
