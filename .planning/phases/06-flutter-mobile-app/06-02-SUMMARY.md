---
phase: 06-flutter-mobile-app
plan: "02"
subsystem: payment-service
tags: [payment-status, idor-guard, tdd, mobl-05, d-11]
dependency_graph:
  requires: [03-05]
  provides: [payment-get-by-id, owner-scoped-status-poll]
  affects: [06-09]
tech_stack:
  added: []
  patterns:
    - TDD RED→GREEN (PaymentByIdIT written before implementation)
    - JPA derived query findByIdAndUserId (compound WHERE id AND user_id)
    - IDOR guard: compound (id, userId) lookup — userId from JWT subject only (never path)
    - 404 on missing-or-not-owned (never 403 — existence must not leak)
key_files:
  created:
    - services/payment-service/src/test/java/com/opendesk/payment/payment/PaymentByIdIT.java
  modified:
    - services/payment-service/src/main/java/com/opendesk/payment/payment/PaymentRepository.java
    - services/payment-service/src/main/java/com/opendesk/payment/payment/PaymentService.java
    - services/payment-service/src/main/java/com/opendesk/payment/payment/PaymentController.java
decisions:
  - "findByIdAndUserId compound JPA derived query: single DB call enforces owner scope; no separate existence check avoids timing oracle"
  - "404 (not 403) for cross-user access: payment existence must not leak to unauthenticated observer (T-06-02-01)"
  - "userId sourced from JWT subject (auth.getToken().getSubject()) never from path parameter (T-06-02-02)"
  - "No SecurityConfig change needed: GET /{id} falls under existing anyRequest().authenticated() catch-all"
metrics:
  duration: "~18 minutes"
  completed: "2026-06-22"
  tasks_completed: 1
  tasks_total: 1
  files_created: 1
  files_modified: 3
---

# Phase 06 Plan 02: GET /api/v1/payments/{id} Owner-Scoped Status Endpoint Summary

**One-liner:** Owner-scoped single-payment status GET via compound (id, userId) JPA derived query — 200 for owner, 404 for cross-user or unknown — so the Flutter STK countdown screen can poll payment status every 5 seconds without scanning the paginated history list.

## What Was Built

### Task 1 — RED→GREEN: GET /api/v1/payments/{id} owner-scoped status endpoint

**RED commit:** `58749ef` — PaymentByIdIT failing (endpoint missing)
**GREEN commit:** `cc56768` — implementation green, all 3 tests pass, no regressions

**PaymentRepository** — added `findByIdAndUserId(UUID id, UUID userId)`:
```java
Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
```
JPA derives a `WHERE id = :id AND user_id = :userId` query. A single compound lookup — never lookup by id alone (T-06-02-02).

**PaymentService** — added `findByIdAndUser(UUID id, UUID userId)`:
```java
@Transactional(readOnly = true)
public Optional<Payment> findByIdAndUser(UUID id, UUID userId) {
    return paymentRepository.findByIdAndUserId(id, userId);
}
```

**PaymentController** — added `@GetMapping("/{id}")`:
```java
@GetMapping("/{id}")
public ResponseEntity<PaymentDto> getById(JwtAuthenticationToken auth, @PathVariable UUID id) {
    UUID userId = UUID.fromString(auth.getToken().getSubject());
    return paymentService.findByIdAndUser(id, userId)
            .map(p -> PaymentDto.from(p, timeoutSeconds))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```

**PaymentByIdIT** — 3 behaviors tested with Testcontainers PG16 + RSA JWT fixture:
1. `getById_owner_returns200WithStatusField` — owner gets 200, response body `status == "PENDING"`
2. `getById_crossUser_returns404_notLeakingExistence` — IDOR: different user's token gets 404
3. `getById_unknownId_returns404` — non-existent UUID gets 404

## GET /api/v1/payments/{id} Response Shape (for Flutter 06-09 STK Purchase Screen)

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "bundleId":  "...",
  "amountTzs": 3200,
  "smsCount":  200,
  "status":    "PENDING",
  "createdAt": "2026-06-22T12:00:00Z",
  "timeoutSeconds": 120
}
```

**Status values:** `PENDING` | `SUCCESS` | `CONFIRMED` | `EXPIRED` | `FAILED`

The Flutter countdown screen (06-09) polls this endpoint every 5 seconds, transitioning the
countdown state from PENDING → CONFIRMED (success) or PENDING → EXPIRED (timeout).
`timeoutSeconds: 120` is the canonical 2-minute countdown duration.

## TDD Gate Compliance

| Gate | Status | Commit |
|------|--------|--------|
| RED | PASSED | `58749ef` — test(06-02) failing test (no endpoint) |
| GREEN | PASSED | `cc56768` — feat(06-02) implementation |

## Deviations from Plan

### Auto-fixed: Missing `Optional` import in PaymentService

**Rule 1 — Bug:** `java.util.Optional` was not imported in `PaymentService.java`. The `findByIdAndUser` return type caused a compilation error. Added `import java.util.Optional;` immediately. No behavioral change — pure fix.

## Threat Mitigations

| Threat | Mitigation | Status |
|--------|------------|--------|
| T-06-02-01 Information Disclosure | userId from JWT subject only; WHERE includes user_id; 404 (not 403) on not-owned | MITIGATED |
| T-06-02-02 Elevation of Privilege | compound (id, userId) lookup — never by id alone | MITIGATED |

## Known Stubs

None — the endpoint is fully functional. `PaymentDto.status` is the live status string from the DB entity.

## Self-Check: PASSED

- [x] `./gradlew :services:payment-service:test` BUILD SUCCESSFUL (all ITs pass, no regressions)
- [x] `./gradlew :services:payment-service:test --tests "com.opendesk.payment.payment.PaymentByIdIT"` — 3/3 pass
- [x] PaymentController contains `@GetMapping("/{id}")` with `@PathVariable UUID id`
- [x] userId extracted from `auth.getToken().getSubject()` (never from path)
- [x] Cross-user test asserts `HttpStatus.NOT_FOUND` (not 403)
- [x] `findByIdAndUserId` present in PaymentRepository
- [x] No `javax.*` imports in any created/modified file
- [x] RED commit `58749ef` exists; GREEN commit `cc56768` exists
