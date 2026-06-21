---
phase: 05-notifications-admin-analytics
plan: "04"
subsystem: wallet-service
tags: [admin, analytics, security, tdd]
dependency_graph:
  requires: [03-02, 03-06, 05-01]
  provides: [ADMN-03, ADMN-05, ANLX-02]
  affects: [05-08, 05-09]
tech_stack:
  added: []
  patterns:
    - JwtAuthenticationConverter with roles claim mapping (hasRole ADMIN)
    - Method-parameter JwtAuthenticationToken injection (virtual-thread safe)
    - JPQL GROUP BY CAST(createdAt AS LocalDate) aggregate query
    - Admin controller with no subject-scoping (vs analytics controller with JWT-scoping)
key_files:
  created:
    - services/wallet-service/src/main/java/com/opendesk/wallet/admin/AdminLedgerController.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/admin/AdminLedgerService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/admin/LedgerEntryDto.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/analytics/CreditUsageController.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/analytics/CreditUsageService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/analytics/CreditUsageDto.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/analytics/CreditUsageRow.java
  modified:
    - services/wallet-service/src/main/java/com/opendesk/wallet/transaction/CreditTransactionRepository.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/config/SecurityConfig.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/admin/AdminLedgerIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/analytics/CreditUsageAnalyticsIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/JwtTestHelper.java
decisions:
  - SecurityConfig extended with jwtAuthenticationConverter reading roles claim — required for hasRole("ADMIN") to work when JWT stores "ROLE_ADMIN" strings; without this the default converter does not map custom claims
  - CreditUsageController uses JwtAuthenticationToken method injection (not SecurityContextHolder) — virtual-thread safe per CLAUDE.md
  - findDailyUsageByUser filters by TxnType CONSUME + EXPIRE (not delta<0) — CreditTransaction.delta is always positive; direction is encoded in txnType
  - POST /api/v1/wallet/refunds admin-reachable via authenticated() in SecurityConfig — ROLE_ADMIN tokens pass authenticated() without any code change to RefundController (ADMN-05 verified)
metrics:
  duration: "12m"
  completed: "2026-06-22"
  tasks: 2
  files: 12
---

# Phase 05 Plan 04: Admin Ledger Inspection + Credit-Usage Analytics Summary

**One-liner:** ADMIN-guarded ledger endpoint (ADMN-03) and JWT-scoped daily credit-usage trend (ANLX-02) added to wallet-service; POST /api/v1/wallet/refunds confirmed admin-reachable (ADMN-05) with zero refund logic changes.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 RED | AdminLedgerIT + CreditUsageAnalyticsIT real failing assertions | 072ae83 | 3 test files |
| 1+2 GREEN | Admin ledger + analytics + SecurityConfig ADMIN guard | 6f0965c | 9 production files |

## What Was Built

### ADMN-03: Admin Ledger Inspection

- `GET /api/v1/admin/ledger/{userId}?page=0&size=50` — paged, newest-first
- `AdminLedgerController` delegates to `AdminLedgerService` which calls `CreditTransactionRepository.findByUserIdOrderByCreatedAtDesc`
- `LedgerEntryDto` exposes: id, date, txnType, description (human-readable), delta, referenceId
- ROLE_USER → 403; ROLE_ADMIN → 200 with paged content
- No subject-scoping — admin reads any user's transactions

### ANLX-02: Credit-Usage Spend Trend

- `GET /api/v1/analytics/credit-usage` — daily consumption aggregates, last 90 days, newest-first
- `CreditUsageController` derives userId from `auth.getToken().getSubject()` — never from a query param (T-05-10 IDOR mitigated)
- `CreditTransactionRepository.findDailyUsageByUser` JPQL: GROUP BY CAST(createdAt AS LocalDate), SUM(delta) WHERE txnType IN (CONSUME, EXPIRE) last 90 days
- GRANT/REFUND/RESERVE/RELEASE excluded — only debit types count as consumption
- Gap days are absent — client gap-fills at MVP

### ADMN-05: Refund Admin Reachability

- POST /api/v1/wallet/refunds — SecurityConfig `authenticated()` matcher covers ROLE_ADMIN tokens
- RefundController and RefundService unchanged — ADMN-05 verified via RefundIT (existing test still green)

### SecurityConfig Extensions

- `/api/v1/admin/**` → `hasRole("ADMIN")` (T-05-09 mitigated)
- `/api/v1/analytics/**` → `authenticated()` (per-plan spec)
- `jwtAuthenticationConverter` bean added: reads `roles` claim, maps each entry as `SimpleGrantedAuthority` — required for hasRole("ADMIN") to evaluate correctly with the project's JWT format

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Roles claim mapping required for hasRole("ADMIN") to work**
- **Found during:** Task 1 GREEN implementation
- **Issue:** Original SecurityConfig used `Customizer.withDefaults()` for JWT — the default converter does not map the `roles` claim. `hasRole("ADMIN")` would silently reject all callers.
- **Fix:** Added `jwtAuthenticationConverter()` bean reading the `roles` JWT claim and producing `SimpleGrantedAuthority` instances — exactly as specified in 05-PATTERNS.md §SecurityConfig.
- **Files modified:** SecurityConfig.java
- **Commit:** 6f0965c

**2. [Rule 2 - Clarity] delta<0 filter replaced with txnType filter**
- **Found during:** Task 2 implementation — reading CreditTransaction entity
- **Issue:** RESEARCH.md suggested `WHERE delta<0` but `CreditTransaction.delta` is documented as "always positive — direction is conveyed by txnType". A `delta<0` filter would return zero rows.
- **Fix:** Filtered by `txnType IN (CONSUME, EXPIRE)` which are the actual debit types. Plan intent preserved.
- **Files modified:** CreditTransactionRepository.java
- **No separate commit** — discovered during initial implementation pass.

## Known Stubs

None — all endpoints are fully wired.

## Threat Flags

None — new endpoints match the plan's threat_model exactly.

## Self-Check: PASSED

- AdminLedgerController.java: FOUND
- AdminLedgerService.java: FOUND
- LedgerEntryDto.java: FOUND
- CreditUsageController.java: FOUND
- CreditUsageService.java: FOUND
- CreditUsageDto.java: FOUND
- CreditUsageRow.java: FOUND
- CreditTransactionRepository.java (updated): FOUND
- SecurityConfig.java (updated): FOUND
- Commits 072ae83 (RED) + 6f0965c (GREEN): FOUND
