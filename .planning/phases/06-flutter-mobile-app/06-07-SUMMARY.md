---
phase: 06-flutter-mobile-app
plan: "07"
subsystem: customer-app/dashboard
tags: [flutter, riverpod, hive, dashboard, balance, tdd, material3]
requirements: [MOBL-04]

dependency_graph:
  requires: ["06-01", "06-06"]
  provides: ["DashboardScreen", "BalanceCard", "StaleIndicator", "ErrorBanner", "AppNavigationBar", "TZS format", "balance cache-read/online-write", "recent campaigns provider"]
  affects: ["06-08", "06-09", "any plan using shared widget kit"]

tech_stack:
  added: []
  patterns:
    - "BalanceResult record (credits, isStale) — FutureProvider<BalanceResult> carries staleness flag without separate state"
    - "fetchBalance() injectable function — accepts Dio + Box<int> for unit testing without ProviderScope"
    - "Hive cache-read + online-write — write-through on success, return cached on DioException, rethrow only if no cache"
    - "ShellRoute in GoRouter wraps authenticated routes for NavigationBar"
    - "FutureProvider.overrideWith for widget test isolation (no ProviderScope needed for Dio mock)"

key_files:
  created:
    - apps/customer-app/lib/shared/format/tzs_format.dart
    - apps/customer-app/lib/shared/widgets/stale_indicator.dart
    - apps/customer-app/lib/shared/widgets/error_banner.dart
    - apps/customer-app/lib/shared/widgets/app_navigation_bar.dart
    - apps/customer-app/lib/shared/widgets/balance_card.dart
    - apps/customer-app/lib/features/dashboard/balance_provider.dart
    - apps/customer-app/lib/features/dashboard/recent_campaigns_provider.dart
    - apps/customer-app/lib/features/dashboard/dashboard_screen.dart
    - apps/customer-app/test/features/dashboard/balance_cache_test.dart
    - apps/customer-app/test/features/dashboard/dashboard_screen_test.dart
  modified:
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/lib/l10n/app_en.arb
    - apps/customer-app/lib/l10n/app_sw.arb
    - apps/customer-app/lib/l10n/app_localizations.dart
    - apps/customer-app/lib/l10n/app_localizations_en.dart
    - apps/customer-app/lib/l10n/app_localizations_sw.dart
    - apps/customer-app/test/placeholder_mobl_test.dart

decisions:
  - "BalanceResult carries isStale flag instead of separate StateProvider — avoids legacy Riverpod 3 StateProvider (in legacy.dart) and provides single source of truth; FutureProvider<BalanceResult> overrideable in tests cleanly"
  - "fetchBalance() is a standalone injectable function — enables unit testing without mounting ProviderScope (balance_cache_test.dart mocks Box<int> and Dio directly)"
  - "ShellRoute wraps authenticated routes for NavigationBar — routes that hide nav bar (purchase, add-contact, composer) remain as standalone GoRoutes outside the shell"
  - "dashboardTitle ARB key added to both locales — NavigationBar labels come from AppLocalizations"
  - "MOBL-04 placeholder removed from placeholder_mobl_test.dart"

metrics:
  duration: ~90 min
  completed: 2026-06-22
  tasks_completed: 2
  tasks_total: 2
  files_created: 10
  files_modified: 7
---

# Phase 06 Plan 07: Dashboard + Shared Widget Kit + NavigationBar Shell Summary

**One-liner:** Material 3 NavigationBar shell, shared widget kit (StaleIndicator/ErrorBanner/BalanceCard/TZS formatter), and MOBL-04 Dashboard (balance cache-read/online-write, ≤5 recent campaigns, quick-send FAB) with full TDD (RED→GREEN).

## What Was Built

### Task 1: Shared Widget Kit + TZS Format + NavigationBar Shell

**`tzs_format.dart`:**
- `tzs(int) → "TZS 5,000"` using `NumberFormat('#,###','sw_TZ')` — no decimals
- `credits(int) → "150 credits"` — no TZS prefix per UI-SPEC

**`stale_indicator.dart`:**
- `bodySmall` Row with `Icons.cached` (12dp) + `staleDataIndicator(time)` from ARB
- Uses `withValues(alpha:)` for subtle muted color (Rule 1 fix — `withOpacity` deprecated)

**`error_banner.dart`:**
- `errorContainer` MaterialBanner variant with message + optional `onRetry` callback + `onDismiss`
- Exposes retry callback explicitly per UI-SPEC requirement

**`app_navigation_bar.dart`:**
- Material 3 `NavigationBar` with 4 destinations: Dashboard / Contacts / Campaigns / Notifications
- `ConsumerWidget` watching `authNotifierProvider`; renders `SizedBox.shrink()` if not `Verified`
- Hidden on 8 routes: splash, onboarding, login, register, pending, purchase, add-contact, composer
- Labels from `AppLocalizations` (bilingual EN+SW)
- Added `dashboardTitle` ARB key (EN: "Dashboard", SW: "Dashibodi") to both locales

**`app_router.dart` (modified):**
- Added `ShellRoute` wrapping authenticated screens — `_ShellPage` renders `AppNavigationBar` as `bottomNavigationBar`
- Routes that explicitly hide nav bar remain outside the shell as standalone `GoRoute`s
- `DashboardScreen` replaces `_PlaceholderScreen('Dashboard')`

### Task 2: Dashboard + Balance Provider (TDD RED→GREEN)

**`balance_provider.dart`:**
- `fetchBalance(dio, box)` — injectable unit-testable function
  - Online: GET `/api/v1/wallet/balance` → write-through to Hive `'balance'` box → return
  - Offline with cache: catch DioException → `box.get('availableCredits')` → return cached int
  - Offline no cache: rethrow (caller shows error)
- `BalanceResult {credits, isStale}` — carries staleness in a single value avoiding separate provider
- `FutureProvider<BalanceResult>` wraps the real flow for production use

**`recent_campaigns_provider.dart`:**
- `FutureProvider<List<Map<String, dynamic>>>` fetching `GET /api/v1/campaigns?page=0&size=5`
- Write-through to Hive `'campaigns'` box; fallback to cached values on error

**`balance_card.dart`:**
- `headlineLarge` credit count (bold) + `labelMedium` "SMS Credits" label
- `primaryContainer` background per UI-SPEC color tokens

**`dashboard_screen.dart`:**
- `CustomScrollView` + `SliverAppBar` (expandedHeight 160, pinned) with `BalanceCard` in `flexibleSpace`
- `StaleIndicator` shown below appbar when `balanceAsync.value?.isStale == true`
- `ErrorBanner` shown when balance has error and no value (no-cache offline path)
- `SliverList` for recent campaigns: up to 5 `_CampaignListTile`s or `campaignsEmptyHeading` empty state
- `FloatingActionButton.extended` ("Send SMS") → `context.go(kCampaignsNewRoute)`
- `RefreshIndicator` wraps `CustomScrollView`; pull-to-refresh invalidates both providers

**Tests (7 green):**
- `balance_cache_test.dart` (3 unit tests): online write-through, offline cache fallback, offline no-cache rethrow
- `dashboard_screen_test.dart` (4 widget tests):
  1. Balance 150 + 3 campaign tiles
  2. Stale balance 75 + StaleIndicator visible
  3. Empty campaigns → `campaignsEmptyHeading`
  4. FAB tap → `/campaigns/new`

## TDD Gate Compliance

- RED commit: `b7e5443` — `test(06-07): RED — balance_cache_test + dashboard_screen_test (MOBL-04)` (both files failed to compile — no implementation files existed)
- GREEN commit: `0d4fe22` — `feat(06-07): DashboardScreen + balance cache-read/online-write (MOBL-04)` (7/7 pass)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `StateProvider` not available without legacy import in Riverpod 3**
- **Found during:** Task 2 implementation (compile error)
- **Issue:** `StateProvider` lives in `package:riverpod/legacy.dart` in Riverpod 3; importing `flutter_riverpod` does not expose it. Also, `valueOrNull` is removed from `AsyncValue` in Riverpod 3.
- **Fix:** Replaced separate `StateProvider<bool>` stale flag + `StateProvider<DateTime?>` with a `BalanceResult` record returned by `balanceProvider` itself. Replaced `.valueOrNull` with `.value`. Matches the decision pattern documented in 06-01 SUMMARY.
- **Files modified:** `balance_provider.dart`, `dashboard_screen.dart`
- **Commit:** 0d4fe22

**2. [Rule 1 - Bug] `dioClientProvider` referenced but provider is named `dioProvider`**
- **Found during:** Task 2 first compile attempt
- **Issue:** Plan and initial code used `dioClientProvider` but `dio_client.dart` exports `dioProvider`.
- **Fix:** All references updated to `dioProvider`.
- **Files modified:** `balance_provider.dart`, `recent_campaigns_provider.dart`, `dashboard_screen_test.dart`
- **Commit:** 0d4fe22

**3. [Rule 2 - Missing] `dashboardTitle` ARB key missing**
- **Found during:** Task 1 — `AppNavigationBar` needed the key for the Dashboard nav label
- **Fix:** Added `dashboardTitle: "Dashboard"` (EN) and `"Dashibodi"` (SW) to both ARB files; regenerated l10n.
- **Files modified:** `app_en.arb`, `app_sw.arb`, `app_localizations.dart`, `app_localizations_en.dart`, `app_localizations_sw.dart`
- **Commit:** 5c87b9b

## Known Stubs

None — all strings from ARB AppLocalizations. Balance and campaigns data wired to real providers (overrideable in tests). No hardcoded placeholder text.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. Existing `/api/v1/wallet/balance` and `/api/v1/campaigns` endpoints consumed (not new).

## Self-Check: PASSED

Files exist:
- `apps/customer-app/lib/shared/format/tzs_format.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/stale_indicator.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/error_banner.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/app_navigation_bar.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/balance_card.dart` — FOUND
- `apps/customer-app/lib/features/dashboard/balance_provider.dart` — FOUND
- `apps/customer-app/lib/features/dashboard/recent_campaigns_provider.dart` — FOUND
- `apps/customer-app/lib/features/dashboard/dashboard_screen.dart` — FOUND
- `apps/customer-app/test/features/dashboard/balance_cache_test.dart` — FOUND
- `apps/customer-app/test/features/dashboard/dashboard_screen_test.dart` — FOUND

Commits:
- 5c87b9b — `feat(06-07): shared widget kit + TZS format + Verified-only NavigationBar shell`
- b7e5443 — `test(06-07): RED — balance_cache_test + dashboard_screen_test (MOBL-04)`
- 0d4fe22 — `feat(06-07): DashboardScreen + balance cache-read/online-write (MOBL-04) — GREEN`
