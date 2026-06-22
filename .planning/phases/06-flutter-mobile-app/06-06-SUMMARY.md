---
phase: 06-flutter-mobile-app
plan: "06"
subsystem: customer-app/auth
tags: [flutter, riverpod, auth, polling, go_router, l10n, tdd]
requirements: [MOBL-02]

dependency_graph:
  requires: ["06-01", "06-04"]
  provides: ["NIDA PENDING walled screen", "10s /auth/me auto-poller", "VERIFIED transition to /dashboard"]
  affects: ["app_router.dart guards", "auth_notifier.dart state machine"]

tech_stack:
  added: []
  patterns:
    - "Timer.periodic + ref.onDispose for lifecycle-safe polling (Pattern 4)"
    - "PendingPollerNotifier delegates to AuthNotifier.refreshAndCheckStatus() — single source of truth"
    - "go_router refreshListenable drives VERIFIED → /dashboard redirect"
    - "addPostFrameCallback for SnackBar on Verified state transition"

key_files:
  created:
    - apps/customer-app/lib/features/auth/nida_pending_screen.dart
    - apps/customer-app/lib/features/auth/pending_poller_notifier.dart
  modified:
    - apps/customer-app/lib/core/auth/auth_notifier.dart
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/test/features/auth/nida_pending_test.dart
    - apps/customer-app/test/placeholder_mobl_test.dart

decisions:
  - "PendingPollerNotifier._poll() delegates to AuthNotifier.refreshAndCheckStatus() rather than writing to state directly — avoids @visibleForTesting violation and keeps state transitions in one place"
  - "Removed unused go_router import from test to keep analyze clean"
  - "AppLocalizations.of() is non-nullable (returns AppLocalizations not AppLocalizations?); removed redundant ! operators"

metrics:
  duration: "~20 minutes"
  completed: "2026-06-22"
  tasks_completed: 1
  tasks_total: 1
  files_created: 2
  files_modified: 4
---

# Phase 06 Plan 06: NIDA PENDING Walled Screen + Auto-Poll Summary

**One-liner:** NIDA PENDING walled screen with 10s Timer.periodic polling GET /auth/me, VERIFIED state transitions to /dashboard via go_router refreshListenable (MOBL-02).

## What Was Built

### NidaPendingScreen (`nida_pending_screen.dart`)

Walled screen (no AppBar back, no bottom nav) rendered when `AuthState.pending`. Layout:
- AnimatedBuilder + AnimationController pulsing 80dp `primaryContainer` circle with `verified_user_outlined` icon
- `titleMedium` pendingScreenTitle, `bodyMedium` pendingScreenBody (80% width)
- Status row: 14dp `CircularProgressIndicator` + `pendingStatusLabel` bodySmall
- Error-colored `TextButton` calling `authNotifier.signOut()` (pendingLogoutLink)
- On `AuthState.verified`: `addPostFrameCallback` shows verifiedSuccessMessage SnackBar; go_router redirect handles navigation to /dashboard

Bilingual (EN + SW) via ARB AppLocalizations.

### PendingPollerNotifier (`pending_poller_notifier.dart`)

`Notifier<void>` that starts `Timer.periodic(10s)` in `build()` and cancels in `ref.onDispose`. Each tick calls `authNotifierProvider.notifier.refreshAndCheckStatus()`. Exposes `pollNow()` for direct test invocation.

### AuthNotifier additions (`auth_notifier.dart`)

`refreshAndCheckStatus()` calls `GET /auth/me`, reads `status` field, and if `VERIFIED` transitions state to `AuthState.verified(...)`. Transient errors are swallowed (try/catch). Also added `setPending()` helper used by tests.

### MOBL-02 placeholder removed

`test/placeholder_mobl_test.dart` — MOBL-02 row deleted; MOBL-04 through MOBL-09 remain intentionally red for future plans.

## TDD Gate Compliance

- RED commit: `test(06-06): RED — nida_pending_test.dart (MOBL-02) — 5 failing behaviors` (bb90118)
- GREEN commit: `feat(06-06): NIDA PENDING walled screen + /auth/me auto-poll (MOBL-02)` (8cac375)

All 5 behaviors proven:
1. Screen renders pendingScreenTitle + pulsing indicator + logout link (EN + SW)
2. Poller calls `/auth/me` (not `/auth/refresh`) on `pollNow()`
3. VERIFIED poll response → `AuthState.verified` + verifiedSuccessMessage SnackBar
4. Poll throwing `DioException` → no error UI, auth remains Pending, timer continues
5. `AuthState.pending` + `/campaigns/new` → redirects to `/pending` (guard)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PendingPollerNotifier directly set `authNotifierProvider.notifier.state`**
- **Found during:** flutter analyze
- **Issue:** The original implementation directly assigned `ref.read(authNotifierProvider.notifier).state = AsyncData(...)` from outside the notifier — this triggers `invalid_use_of_protected_member` and `invalid_use_of_visible_for_testing_member` warnings
- **Fix:** Replaced body of `_poll()` with a delegation call to `authNotifierProvider.notifier.refreshAndCheckStatus()` — which already implements the same GET /auth/me → VERIFIED logic correctly within the notifier itself. Removed unused imports (`auth_state.dart`, `dio_client.dart`, `secure_storage.dart`)
- **Files modified:** `pending_poller_notifier.dart`

**2. [Rule 1 - Bug] Unnecessary null-assertion on non-nullable `AppLocalizations.of()`**
- **Found during:** flutter analyze (2 warnings)
- **Fix:** Removed `!` operators; `AppLocalizations.of()` returns `AppLocalizations` (non-nullable)
- **Files modified:** `nida_pending_screen.dart`

**3. [Rule 1 - Bug] Unused `go_router` import in test**
- **Found during:** flutter analyze
- **Fix:** Removed unused import
- **Files modified:** `nida_pending_test.dart`

## Known Stubs

None — all strings from ARB AppLocalizations; no hardcoded placeholder text or empty data.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- `apps/customer-app/lib/features/auth/nida_pending_screen.dart` — created, committed in 8cac375
- `apps/customer-app/lib/features/auth/pending_poller_notifier.dart` — created, committed in 8cac375
- `flutter analyze --no-fatal-infos` → No issues found
- `flutter test test/features/auth/nida_pending_test.dart` → 5/5 passed
- MOBL-02 placeholder removed from placeholder_mobl_test.dart
