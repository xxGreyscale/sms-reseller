---
phase: 06-flutter-mobile-app
plan: 01
subsystem: customer-app
tags: [flutter, scaffold, auth, dio, router, l10n, riverpod, hive, tdd]
dependency_graph:
  requires: []
  provides: [flutter-scaffold, auth-interceptor, router-guards, l10n-harness, auth-state]
  affects: [06-02, 06-03, 06-04, 06-05]
tech_stack:
  added:
    - flutter_riverpod 3.3.2
    - go_router 17.3.0
    - dio 5.9.2
    - hive_ce 2.9.0 + hive_ce_flutter 2.3.4
    - flutter_secure_storage 10.3.1
    - freezed 3.2.5
    - mocktail 1.0.4
    - flutter_lints 5.0.0
  patterns:
    - QueuedInterceptor with _refreshFuture gate for concurrent 401 serialisation
    - Riverpod 3 Notifier (not StateNotifier) for LocaleNotifier
    - GoRouterRefreshNotifier using ref.listen (not .stream which is removed in Riverpod 3)
    - AsyncValue.value (not .valueOrNull — removed in Riverpod 3.x)
key_files:
  created:
    - apps/customer-app/pubspec.yaml
    - apps/customer-app/lib/main.dart
    - apps/customer-app/lib/l10n/app_en.arb
    - apps/customer-app/lib/l10n/app_sw.arb
    - apps/customer-app/lib/core/auth/auth_state.dart
    - apps/customer-app/lib/core/auth/auth_notifier.dart
    - apps/customer-app/lib/core/dio/auth_interceptor.dart
    - apps/customer-app/lib/core/dio/dio_client.dart
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/lib/core/router/go_router_refresh_stream.dart
    - apps/customer-app/lib/core/storage/secure_storage.dart
    - apps/customer-app/lib/core/hive/hive_boxes.dart
    - apps/customer-app/lib/core/locale/locale_notifier.dart
    - apps/customer-app/lib/core/theme/app_theme.dart
    - apps/customer-app/test/core/dio/auth_interceptor_test.dart
    - apps/customer-app/test/core/router/redirect_guard_test.dart
    - apps/customer-app/test/placeholder_mobl_test.dart
    - apps/customer-app/integration_test/app_test.dart
  modified: []
decisions:
  - "Use _refreshFuture (Future<String>?) gate instead of _isRefreshing bool; the future is set synchronously before any await so concurrent 401 callers all await the same in-flight refresh"
  - "GoRouterRefreshNotifier takes typed ref.listen<AsyncValue<AuthState>> instead of generic ProviderListenable (not re-exported by flutter_riverpod 3.x)"
  - "LocaleNotifier migrated from StateNotifier to Notifier (Riverpod 3 removed StateNotifier)"
  - "L10n output placed at lib/l10n/ (not flutter_gen); imported as package:customer_app/l10n/app_localizations.dart"
  - "Router redirect tests use extracted _redirect() function rather than mounting GoRouter in widget test (no ProviderScope needed)"
metrics:
  duration: ~45 min
  completed: 2026-06-22
  tasks_completed: 3
  files_created: 18
---

# Phase 06 Plan 01: Flutter App Scaffold Summary

Flutter customer app scaffold — locked-stack pubspec, EN+SW l10n, Material 3 theme, Hive CE read-cache, FlutterSecureStorage JWT pipeline, Dio QueuedInterceptor auth with concurrent-401 gate, Riverpod 3 AuthNotifier + Freezed AuthState, go_router with three redirect guards, and Nyquist MOBL placeholder test map.

## What Was Built

### Task 1: Scaffold + config
- `pubspec.yaml` with locked stack (flutter_riverpod 3.3.2, go_router 17.3.0, dio 5.9.2, hive_ce 2.9.0+, freezed 3.2.5, mocktail 1.0.4, flutter_lints 5.0.0)
- `lib/l10n/app_en.arb` + `app_sw.arb` — full copywriting contract, both locales complete
- `AppTheme.light` — Material 3, `ColorScheme.fromSeed(Color(0xFF1565C0))`, 4 typography roles
- `HiveBoxes` — `initHive()` opening balance/contacts/campaigns/notifications boxes
- `secureStorageProvider` — `FlutterSecureStorage` for access_token + refresh_token
- `localeNotifierProvider` — Riverpod 3 `Notifier<Locale>` persisted to SharedPreferences
- `main.dart` — WidgetsFlutterBinding + Hive init + ProviderScope + MaterialApp.router

### Task 2: Auth interceptor (TDD GREEN)
- `AuthInterceptor extends QueuedInterceptor` — attaches Bearer on every request
- `_refreshFuture` gate: set synchronously before first await; all concurrent 401s await the same future → exactly 1 `/auth/refresh` call
- `_tokenDio` (separate Dio instance) used for refresh to avoid recursive interception
- On refresh failure: `deleteAll()` + reject original error
- 4 behavior tests all green

### Task 3: Router + test harness (TDD GREEN + RED-by-design)
- `app_router.dart` — GoRouter with 14 routes, three ordered redirect rules, `refreshListenable: GoRouterRefreshNotifier(ref)`
- `GoRouterRefreshNotifier` — `ChangeNotifier` using `ref.listen<AsyncValue<AuthState>>`
- `redirect_guard_test.dart` — 4 tests (unauth→login, pending→pending, verified→dashboard, verified+/dashboard→null) all GREEN
- `placeholder_mobl_test.dart` — 9 `fail('not implemented')` placeholders MOBL-01..09 (RED by design)
- `integration_test/app_test.dart` — skeleton with `integration_test` sdk import

## Test Commands (for later feature plans)

```bash
# Confirm scaffold compiles
cd apps/customer-app && flutter analyze --no-fatal-infos

# Unit tests (interceptor + router) — must all pass
flutter test test/core/

# MOBL placeholder map — all 9 must fail (expected)
flutter test test/placeholder_mobl_test.dart

# Run all unit tests (core pass, widget placeholder pass, MOBL 9 fail)
flutter test test/
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Riverpod 3.x API incompatibilities in scaffold**
- **Found during:** Task 1 flutter analyze
- **Issue:** Scaffold used `StateNotifier`, `authNotifierProvider.stream`, and `.valueOrNull` — all removed or renamed in Riverpod 3.3.2. Also `flutter_gen` l10n path incorrect; `encryptedSharedPreferences` deprecated.
- **Fix:** `LocaleNotifier` migrated to `Notifier`; `.stream` replaced by `GoRouterRefreshNotifier` with `ref.listen`; `.valueOrNull` → `.value`; l10n imported from `package:customer_app/l10n/`; `AndroidOptions` removed from `secureStorageProvider`.
- **Files modified:** `locale_notifier.dart`, `go_router_refresh_stream.dart`, `app_router.dart`, `main.dart`, `secure_storage.dart`
- **Commit:** 6043eda

**2. [Rule 1 - Bug] Concurrent 401 gate failed with _isRefreshing bool approach**
- **Found during:** Task 2 TDD RED→GREEN
- **Issue:** `_isRefreshing` bool was set after the first `await`, so concurrent callers passed the gate before it was set. Switching to `Completer<String>` had `completeError` leaking. Final fix: `Future<String>? _refreshFuture` set synchronously (before any await) and completed in `_doRefresh()`.
- **Fix:** Replaced bool gate with `_refreshFuture` pattern; moved storage.deleteAll into `_doRefresh` catch block.
- **Files modified:** `auth_interceptor.dart`
- **Commit:** a00e2c9

**3. [Rule 2 - Missing] flutter_lints not in pubspec**
- **Found during:** Task 1 flutter analyze
- **Issue:** `analysis_options.yaml` included `package:flutter_lints/flutter.yaml` but `flutter_lints` was missing from `dev_dependencies`, causing an analysis error.
- **Fix:** Added `flutter_lints: ^5.0.0` to dev_dependencies.
- **Files modified:** `pubspec.yaml`
- **Commit:** 6043eda

## Self-Check

Files exist:
- apps/customer-app/lib/core/dio/auth_interceptor.dart: FOUND
- apps/customer-app/lib/core/router/app_router.dart: FOUND
- apps/customer-app/test/core/dio/auth_interceptor_test.dart: FOUND
- apps/customer-app/test/core/router/redirect_guard_test.dart: FOUND
- apps/customer-app/test/placeholder_mobl_test.dart: FOUND
- apps/customer-app/integration_test/app_test.dart: FOUND

Commits exist:
- 6043eda: chore(06-01) scaffold
- a00e2c9: feat(06-01) auth interceptor
- b3cc048: test(06-01) router guards + MOBL placeholders

## Self-Check: PASSED
