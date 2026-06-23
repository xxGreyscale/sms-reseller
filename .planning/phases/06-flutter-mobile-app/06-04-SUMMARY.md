---
phase: 06-flutter-mobile-app
plan: 04
subsystem: identity-service, notification-service
tags: [auth, api, tdd, mobl-02, d-13, d-14, idor]
dependency_graph:
  requires: []
  provides:
    - "GET /auth/me → {userId, status} non-rotating (D-13, MOBL-02) — Flutter PENDING poller (06-07)"
    - "PATCH /api/v1/notifications/{id}/read → 204/404 IDOR-guarded (D-14) — notification feed (06-11)"
  affects:
    - "06-07 (NIDA pending poll): use GET /auth/me instead of POST /auth/refresh"
    - "06-11 (notification feed): mark-read badge uses PATCH /{id}/read"
tech_stack:
  added: []
  patterns:
    - "@GetMapping /me: @AuthenticationPrincipal Jwt → userId from subject → userRepository.findById → MeResponse"
    - "NotificationRepository.findByIdAndUserId compound derived query → IDOR ownership enforcement → 404 on not-owned"
key_files:
  created:
    - services/identity-service/src/main/java/com/smsreseller/identity/web/dto/MeResponse.java
    - services/identity-service/src/test/java/com/smsreseller/identity/auth/AuthMeIT.java
    - services/notification-service/src/test/java/com/smsreseller/notification/notification/MarkReadIT.java
  modified:
    - services/identity-service/src/main/java/com/smsreseller/identity/auth/SessionController.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/Notification.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationRepository.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationService.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationController.java
decisions:
  - "GET /auth/me uses @AuthenticationPrincipal Jwt (not JwtAuthenticationToken) — mirrors existing logout pattern in same file"
  - "GET /auth/me re-reads user from DB (not from JWT claims) so a freshly-VERIFIED user sees correct status without token rotation"
  - "markAsRead returns boolean (not Optional/void) so controller maps true→204, false→404 in a single expression"
  - "IDOR returns 404 (not 403) to avoid disclosing notification existence to unauthorized callers (T-06-04-03)"
metrics:
  duration: "~12 minutes"
  completed: "2026-06-22"
  tasks: 2
  files: 9
requirements: [MOBL-02]
---

# Phase 06 Plan 04: GET /auth/me + PATCH Notifications Read Summary

**One-liner:** Non-rotating `GET /auth/me` status read (D-13, MOBL-02) and IDOR-safe `PATCH /api/v1/notifications/{id}/read` (D-14) — both IT-green with RED→GREEN TDD.

## What Was Built

### Task 1 — GET /auth/me (D-13, MOBL-02)

**Endpoint:** `GET /auth/me` in `SessionController`

**Response shape (for Flutter 06-07 PENDING poller):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING_VERIFICATION"
}
```
Status values: `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED` (mirrors `VerificationStatus` enum).

**Key properties:**
- Requires a valid JWT (`anyRequest().authenticated()` — no SecurityConfig change)
- Re-reads user status from PostgreSQL (not from JWT claims) — a freshly-VERIFIED user sees `VERIFIED` without re-logging
- Does NOT invoke `JwtIssuer` or `RefreshTokenService` — zero token rotation
- Returns 401 if user not found in DB (defensive; should not occur in practice)
- `userId` always from JWT subject — never from a request parameter (T-06-04-01)

**Note for 06-07:** The Flutter PENDING screen should poll `GET /auth/me` on a 10-second timer instead of `POST /auth/refresh`. This avoids ~60,000 refresh token rotations over a typical poll window.

**New file:** `MeResponse.java` — `record MeResponse(UUID userId, String status)`

### Task 2 — PATCH /api/v1/notifications/{id}/read (D-14)

**Endpoint:** `PATCH /api/v1/notifications/{id}/read` in `NotificationController`

**Behavior:**
- `204 No Content` — notification found, owned by caller, `read` set to `true`
- `404 Not Found` — notification not found OR belongs to a different user (IDOR guard)

**IDOR implementation:**
- `NotificationRepository.findByIdAndUserId(UUID id, UUID userId)` — compound JPA derived query
- `NotificationService.markAsRead(UUID id, UUID userId)` — returns `boolean` (true = found+owned)
- `NotificationController.markAsRead` — `userId` from `auth.getToken().getSubject()` only
- `Notification.markRead()` — explicit mutator added (class uses `@Getter`-only Lombok; no generated setter exists)

**Note for 06-11:** The notification feed badge can derive unread count client-side from the feed response, so D-14 is optional for the app. The endpoint is available if needed.

## Test Coverage

### AuthMeIT (identity-service)
| Test | Behavior | Result |
|------|----------|--------|
| `authenticatedGetMe_returns200WithUserIdAndStatus_andDoesNotRotateRefreshToken` | 200 + {userId, status} + Redis hash unchanged after 2 calls | GREEN |
| `unauthenticatedGetMe_returns401` | 401 without Bearer token | GREEN |
| `meReturnsCurrentDbStatus_verifiedUserReturnsVerified` | VERIFIED user → status "VERIFIED" | GREEN |

### MarkReadIT (notification-service)
| Test | Behavior | Result |
|------|----------|--------|
| `patchOwnNotification_returns204AndSetsReadTrue` | 204 + read flag true in DB | GREEN |
| `patchOtherUsersNotification_returns404AndReadFlagUnchanged` | 404 + read flag unchanged (IDOR) | GREEN |
| `patchNonExistentNotification_returns404` | 404 for random UUID | GREEN |

### Regression: existing tests still pass
- All identity-service tests (LoginIT, RefreshRotationIT, LogoutIT, LockoutIT, etc.) — GREEN
- All notification-service tests (NotificationFeedIT, consumer ITs) — GREEN

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 167242d | test(06-04) | RED: AuthMeIT + MarkReadIT failing tests |
| a2afee3 | feat(06-04) | GREEN: GET /auth/me + MeResponse (D-13) |
| 9e96fc4 | feat(06-04) | GREEN: PATCH /{id}/read IDOR-guarded (D-14) |

## Deviations from Plan

None — plan executed exactly as written. Both endpoints implemented per 06-PATTERNS.md blueprints.

## TDD Gate Compliance

- RED gate: commit `167242d` — `test(06-04)` RED tests committed before implementation
- GREEN gate: commits `a2afee3` and `9e96fc4` — implementation after RED

## Known Stubs

None. Both endpoints are fully wired — no placeholder data or hardcoded values.

## Threat Flags

No new threat surface beyond what the plan's threat model already covers (T-06-04-01, T-06-04-02, T-06-04-03).

## Self-Check: PASSED

Files exist and commits verified:
- `/Users/somar/Desktop/private/sms-reseller/services/identity-service/src/main/java/com/smsreseller/identity/web/dto/MeResponse.java` — exists
- `/Users/somar/Desktop/private/sms-reseller/services/identity-service/src/test/java/com/smsreseller/identity/auth/AuthMeIT.java` — exists
- `/Users/somar/Desktop/private/sms-reseller/services/notification-service/src/test/java/com/smsreseller/notification/notification/MarkReadIT.java` — exists
- Commits 167242d, a2afee3, 9e96fc4 — verified in git log
