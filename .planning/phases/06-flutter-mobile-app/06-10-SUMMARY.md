---
phase: 06-flutter-mobile-app
plan: "10"
subsystem: customer-app/campaigns
tags: [flutter, riverpod, tdd, material3, campaigns, sms-counter, composer, history, detail]
requirements: [MOBL-07, MOBL-08]

dependency_graph:
  requires: ["06-01", "06-07", "06-09"]
  provides:
    - "SmsCounter (GSM-7/UCS-2 pure Dart)"
    - "SmsCharCounter widget"
    - "CampaignStatusChip"
    - "CampaignListTile"
    - "ComposerScreen (contactIds[] immediate send)"
    - "CampaignHistoryScreen (paginated)"
    - "DetailScreen (aggregate stats + per-message)"
    - "CampaignApi"
    - "CampaignSendNotifier"
    - "CampaignHistoryNotifier"
  affects: ["app_router.dart"]

tech_stack:
  added: []
  patterns:
    - "SmsCounter pure Dart — grapheme clusters (text.characters.length) for correct emoji/UCS-2 counting"
    - "ComposerScreen ref.watch(contactsProvider) in build() to populate _allContacts before bottom sheet opens"
    - "showModalBottomSheet useRootNavigator: true for GoRouter compatibility"
    - "DetailScreenTestable testable variant accepts pre-loaded JSON for widget tests"
    - "CampaignSendNotifier/CampaignHistoryNotifier as AsyncNotifier (not AutoDispose — Riverpod 3)"
    - "Infinite scroll: ScrollController.addListener checks pixels >= maxScrollExtent - 200"

key_files:
  created:
    - apps/customer-app/lib/shared/widgets/sms_char_counter.dart
    - apps/customer-app/lib/shared/widgets/campaign_status_chip.dart
    - apps/customer-app/lib/shared/widgets/campaign_list_tile.dart
    - apps/customer-app/lib/features/campaigns/campaign_api.dart
    - apps/customer-app/lib/features/campaigns/campaign_provider.dart
    - apps/customer-app/lib/features/campaigns/composer_screen.dart
    - apps/customer-app/lib/features/campaigns/history_screen.dart
    - apps/customer-app/lib/features/campaigns/detail_screen.dart
    - apps/customer-app/test/features/campaigns/sms_char_counter_test.dart
    - apps/customer-app/test/features/campaigns/composer_test.dart
    - apps/customer-app/test/features/campaigns/history_detail_test.dart
  modified:
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/test/placeholder_mobl_test.dart

decisions:
  - "SmsCounter uses text.characters.length (grapheme clusters) not text.length (code units) — emoji counted as 1 char"
  - "ComposerScreen watches contactsProvider in build() and caches to _allContacts field so bottom sheet always receives loaded contacts"
  - "showModalBottomSheet uses useRootNavigator: true to escape GoRouter nested navigator"
  - "DetailScreenTestable exposes a test-only variant that accepts JSON directly, avoiding Dio mocking in widget tests"
  - "CampaignHistoryNotifier uses regular AsyncNotifier (AutoDisposeAsyncNotifier not exported in Riverpod 3)"
  - "MOBL-07 and MOBL-08 placeholders removed from placeholder_mobl_test.dart"
  - "ContactListScreen and AddContactScreen wired in app_router.dart (were _PlaceholderScreen)"

metrics:
  duration: ~60 min
  completed: 2026-06-23
  tasks_completed: 3
  tasks_total: 3
  files_created: 11
  files_modified: 2
---

# Phase 06 Plan 10: Campaign Surface (Composer + History + Detail) Summary

**One-liner:** Campaign composer with real-time GSM-7/UCS-2 character counting and contactIds[] targeting (MOBL-07), plus paginated campaign history and per-message delivery detail screen (MOBL-08) — full TDD RED→GREEN.

## What Was Built

### Task 1: SMS Char Counter + Status Chip + List Tile — RED→GREEN

**`sms_char_counter.dart`:**
- `SmsCounter.count(text)` — pure Dart static method returning `SmsCountResult`
- GSM-7 charset table via `_gsm7Basic` + `_gsm7Extended` strings
- Thresholds: GSM-7 single 160/multi 153, UCS-2 single 70/multi 67
- Grapheme cluster count via `text.characters.length` for correct emoji handling
- Display string: `"120/160 · 1 SMS"` / `"⚠ 45/70 · 1 SMS (UCS-2)"`
- `SmsCharCounter` widget: `colorScheme.error` for UCS-2 warning, non-blocking

**`campaign_status_chip.dart`:**
- Maps 8 statuses to UI-SPEC color pairs: PENDING/VERIFIED/SUCCESS/FAILED/EXPIRED/QUEUED/SENT/DELIVERED
- `Container` with `BorderRadius.circular(12)` + colored label text

**`campaign_list_tile.dart`:**
- `ListTile`: name + date + recipient count + `CampaignStatusChip` trailing

Tests: 12 green (5 unit logic + 2 widget + 5 chip color)

### Task 2: Campaign Composer (MOBL-07) — RED→GREEN

**`campaign_api.dart`:**
- `CampaignResponse` model + `MessageStatusRow` model
- `createCampaign({name, body, contactIds[], senderId?})` — uses `contactIds`, never `groupIds` (D-12/Pitfall 5)
- `sendCampaign(id)`, `listCampaigns(page, size)`, `getCampaign(id)`, `getMessages(id, page, size)`

**`campaign_provider.dart`:**
- `CampaignSendNotifier`: orchestrates create→send, returns `SendResult` sealed class
  - `SendSuccess(campaignId)` / `SendInsufficientCredits()` / `SendNetworkError()`
- `CampaignHistoryNotifier`: paginated list with `loadMore()` + `refresh()`

**`composer_screen.dart`:**
- `AppBar` title from `l10n.composerTitle`
- Recipient picker: `TextButton` → `showModalBottomSheet` (useRootNavigator: true) with `CheckboxListTile` items
- `SmsCharCounter` below message field, updates per keystroke
- `composerSendButton` gated: disabled until ≥1 contact AND non-empty message
- `LoadingOverlay` during create+send
- Success: `context.go('/campaigns/$campaignId')`
- `errorInsufficientCredits`: `ErrorBanner` + `Buy Credits` `FilledButton` → `/bundles`
- Network error: `ErrorBanner` with Try Again

Tests: 4 green (disable gate, contactIds payload, insufficient credits, network error)

### Task 3: Campaign History + Detail (MOBL-08) — RED→GREEN

**`history_screen.dart`:**
- `Scaffold(body: RefreshIndicator(child: CustomScrollView(...)))`
- `SliverAppBar` (pinned) + `SliverList` of `CampaignListTile`
- `ScrollController` listener: `pixels >= maxScrollExtent - 200` → `loadMore()`
- Empty state: `campaignsEmptyHeading` icon + text
- `RefreshIndicator` pull-to-refresh

**`detail_screen.dart`:**
- `DetailScreen`: `FutureBuilder` fetching `getCampaign` + `getMessages`
- `DetailScreenTestable`: test-only variant accepting pre-loaded JSON
- `_DetailView`: 3 stat tiles (total/delivered/failed) + `CampaignStatusChip` + per-message `ListView`
- Failed message rows: `Container(color: Color(0xFFFFEBEE))` (Red-50 tint)

**`app_router.dart` (modified):**
- `CampaignHistoryScreen` → `kCampaignsRoute`
- `DetailScreen` → `/campaigns/:id`
- `ContactListScreen` → `kContactsRoute` (was placeholder)
- `AddContactScreen` → `kContactsAddRoute` (was placeholder)

Tests: 3 green (history paginates, empty state, detail stat tiles + failed row)

## TDD Gate Compliance

- Task 1 RED: `7c701d5` — `test(06-10): RED — sms_char_counter_test`
- Task 1 GREEN: `01e4a94` — `feat(06-10): GSM-7/UCS-2 char counter + CampaignStatusChip + CampaignListTile — GREEN`
- Task 2 RED: `68e9079` — `test(06-10): RED — composer_test`
- Task 2 GREEN: `e73f532` — `feat(06-10): campaign composer with contactIds[] immediate send (MOBL-07) — GREEN`
- Task 3 RED: `7a38662` — `test(06-10): RED — history_detail_test`
- Task 3 GREEN: `996a691` — `feat(06-10): campaign history + detail screens (MOBL-08) — GREEN`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `text.length` counts UTF-16 code units, not grapheme clusters**
- **Found during:** Task 1 GREEN (Test 3a/3b emoji tests)
- **Issue:** `'😊'.length == 2` (surrogate pair), but emoji should count as 1 SMS char
- **Fix:** Changed `charCount = text.length` to `charCount = text.characters.length`
- **Files modified:** `sms_char_counter.dart`
- **Commit:** `01e4a94`

**2. [Rule 1 - Bug] `contactsProvider` returns `AsyncLoading` when bottom sheet opens**
- **Found during:** Task 2 GREEN — bottom sheet showed 0 contacts despite FakeContactsNotifier
- **Issue:** `ref.read(contactsProvider)` in `_openRecipientPicker` returned `AsyncLoading` because async `build()` hadn't resolved before the tap. ProviderScope with async Notifier doesn't guarantee the state is loaded by the first `pumpAndSettle`.
- **Fix:** Added `_allContacts` field populated via `ref.watch(contactsProvider)` in `build()`, so the value is always available when the picker opens.
- **Files modified:** `composer_screen.dart`
- **Commit:** `e73f532`

**3. [Rule 1 - Bug] `showModalBottomSheet` sheet not visible in GoRouter ProviderScope test**
- **Found during:** Task 2 testing — bottom sheet appeared but contained 0 contacts
- **Issue:** The sheet rendered with empty contacts (related to deviation 2). Root cause traced via `UncontrolledProviderScope` vs `ProviderScope` debug.
- **Fix:** Combined with deviation 2 fix above. Added `useRootNavigator: true` to ensure proper overlay in tests.
- **Files modified:** `composer_screen.dart`
- **Commit:** `e73f532`

**4. [Rule 1 - Bug] `AutoDisposeAsyncNotifier` not exported in Riverpod 3**
- **Found during:** Task 2 compile error
- **Issue:** `AutoDisposeAsyncNotifier` is not re-exported by `flutter_riverpod`. Must use `AsyncNotifier` with regular `AsyncNotifierProvider`.
- **Fix:** Changed `extends AutoDisposeAsyncNotifier` → `extends AsyncNotifier` and removed `.autoDispose` from provider declarations.
- **Files modified:** `campaign_provider.dart`
- **Commit:** `e73f532`

**5. [Rule 1 - Bug] `CampaignHistoryScreen` missing `Scaffold` wrapper**
- **Found during:** Task 3 Test 1 (ListTile assertion: no Material ancestor)
- **Issue:** `RefreshIndicator(child: CustomScrollView(...))` returned directly without `Scaffold`, so `ListTile` had no `Material` ancestor.
- **Fix:** Wrapped in `Scaffold(body: RefreshIndicator(...))`.
- **Files modified:** `history_screen.dart`
- **Commit:** `996a691`

**6. [Rule 1 - Adaptation] Wired ContactListScreen + AddContactScreen in router**
- **Found during:** Task 3 commit — saw `/contacts` still used `_PlaceholderScreen`
- **Issue:** Plan note said "expected to touch app_router.dart" for campaigns; took opportunity to wire contacts screens too.
- **Fix:** Added imports + replaced `_PlaceholderScreen` for contacts routes.
- **Files modified:** `app_router.dart`
- **Commit:** `996a691`

## Known Stubs

None — all strings from `AppLocalizations`. Campaign data wired to real `CampaignApi`/`CampaignSendNotifier`/`CampaignHistoryNotifier` (overrideable in tests). No hardcoded placeholder text.

## Threat Flags

None — no new network endpoints introduced. All campaign API endpoints (`/api/v1/campaigns`, `POST .../send`, `GET .../messages`) were already defined in backend phases 04-05. JWT auth flows through the existing `AuthInterceptor`.

## Self-Check: PASSED

Files exist:
- `apps/customer-app/lib/shared/widgets/sms_char_counter.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/campaign_status_chip.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/campaign_list_tile.dart` — FOUND
- `apps/customer-app/lib/features/campaigns/campaign_api.dart` — FOUND
- `apps/customer-app/lib/features/campaigns/campaign_provider.dart` — FOUND
- `apps/customer-app/lib/features/campaigns/composer_screen.dart` — FOUND
- `apps/customer-app/lib/features/campaigns/history_screen.dart` — FOUND
- `apps/customer-app/lib/features/campaigns/detail_screen.dart` — FOUND
- `apps/customer-app/test/features/campaigns/sms_char_counter_test.dart` — FOUND
- `apps/customer-app/test/features/campaigns/composer_test.dart` — FOUND
- `apps/customer-app/test/features/campaigns/history_detail_test.dart` — FOUND

Commits:
- `7c701d5` — `test(06-10): RED — sms_char_counter_test`
- `01e4a94` — `feat(06-10): GSM-7/UCS-2 char counter + CampaignStatusChip + CampaignListTile — GREEN`
- `68e9079` — `test(06-10): RED — composer_test`
- `e73f532` — `feat(06-10): campaign composer with contactIds[] immediate send (MOBL-07) — GREEN`
- `7a38662` — `test(06-10): RED — history_detail_test`
- `996a691` — `feat(06-10): campaign history + detail screens (MOBL-08) — GREEN`
