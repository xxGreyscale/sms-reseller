---
phase: 06-flutter-mobile-app
plan: "09"
subsystem: customer-app/contacts
tags: [flutter, riverpod, hive, contacts, tdd, material3, offline-cache]
requirements: [MOBL-06]

dependency_graph:
  requires: ["06-01", "06-07"]
  provides: ["ContactListScreen", "AddContactScreen", "ContactsNotifier", "ContactApi", "ContactListTile", "Hive cache-read/online-write contacts"]
  affects: ["06-10"]

tech_stack:
  added: []
  patterns:
    - "Hive cache-read + online-write contacts (mirrors balance_provider.dart pattern from 06-07)"
    - "AsyncNotifier<ContactsState> with deleteContact modifying in-memory state after successful DELETE"
    - "online-only write with LoadingOverlay + errorNetworkWrite ErrorBanner (D-05, no silent queue)"
    - "SliverAppBar + SearchBar in bottom slot for contact list filter"

key_files:
  created:
    - apps/customer-app/lib/features/contacts/contact_api.dart
    - apps/customer-app/lib/features/contacts/contacts_provider.dart
    - apps/customer-app/lib/features/contacts/contact_list_screen.dart
    - apps/customer-app/lib/features/contacts/add_contact_screen.dart
    - apps/customer-app/lib/shared/widgets/contact_list_tile.dart
    - apps/customer-app/test/features/contacts/contact_list_test.dart
    - apps/customer-app/test/features/contacts/add_contact_test.dart
  modified:
    - apps/customer-app/test/placeholder_mobl_test.dart

decisions:
  - "ContactItem exported from contacts_provider.dart via re-export of contact_api.dart — consumers only import contacts_provider"
  - "deleteContact updates in-memory Riverpod state immediately after successful DELETE, removing the row without re-fetch"
  - "add_contact_screen uses context.go('/contacts') when context.canPop() is false (GoRouter shallow stack) so the test router also lands on /contacts"
  - "search is client-side filter on in-memory ContactsState.contacts — no API call per keystroke"
  - "MOBL-06 placeholder removed from placeholder_mobl_test.dart"

metrics:
  duration: ~30 min
  completed: 2026-06-23
  tasks_completed: 2
  tasks_total: 2
  files_created: 7
  files_modified: 1
---

# Phase 06 Plan 09: Contact List + Add Contact (MOBL-06) Summary

**One-liner:** Flat contact list with offline Hive cache, client-side search, and delete confirmation; online-only add-contact with LoadingOverlay and errorNetworkWrite error handling (no silent queue, D-05).

## What Was Built

### Task 1: Contact list (cache-read) + delete confirmation — RED→GREEN

**`contact_api.dart`:**
- `ContactItem` model: `id`, `name`, `phone` (maps `phoneE164` from API)
- `ContactApi.listContacts()` — handles both paginated `{content:[]}` and flat array responses
- `ContactApi.createContact()` — POST `/api/v1/contacts`
- `ContactApi.deleteContact(id)` — DELETE `/api/v1/contacts/{id}`

**`contacts_provider.dart`:**
- `ContactsState {contacts, isStale}` — carries both data and staleness flag (mirrors BalanceResult from 06-07)
- `ContactsNotifier extends AsyncNotifier<ContactsState>`:
  - Online path: write-through to Hive `'contacts'` box keyed by contact ID
  - Offline path: read from Hive, return with `isStale: true`
  - `refresh()` — pull-to-refresh invalidates state
  - `deleteContact(id)` — DELETE API → remove from Hive → update in-memory state

**`contact_list_tile.dart`:**
- `CircleAvatar` with uppercase first-letter initial + `primaryContainer` background
- `bodyMedium` name + `bodySmall` phone
- Trailing `IconButton` with `colorScheme.error` color for delete action

**`contact_list_screen.dart`:**
- `CustomScrollView` + `SliverAppBar` (pinned) with `TextField` in bottom slot for search
- Client-side search filter on `ContactsState.contacts`
- `StaleIndicator` shown at top when `isStale = true`
- Empty state: `contactsEmptyHeading` with `Icons.person_add` illustration
- `SliverList` of `ContactListTile`
- Delete: trailing icon → `AlertDialog` with error-styled confirm (`colorScheme.error` foreground)
- `FloatingActionButton` → `/contacts/add`
- `RefreshIndicator` wrapping `CustomScrollView`

**Tests (4 green):**
- Test 1: offline with stale Hive data → contacts visible + StaleIndicator
- Test 2: empty list → `contactsEmptyHeading`
- Test 3: delete icon → dialog → confirm → row removed
- Test 4: search "Bob" → only Bob visible

### Task 2: Add contact (online-only write) — RED→GREEN

**`add_contact_screen.dart`:**
- `AppBar` title from `l10n.addContactTitle`
- Full Name `TextFormField` (key: `fullNameField`) + Phone `TextFormField` (key: `phoneField`, hint `07xx xxx xxx`)
- `FilledButton` (key: `addContactSaveButton`) — disabled while loading
- `LoadingOverlay` wraps the `Scaffold` for interaction-blocking during POST
- Success path: `ContactApi.createContact()` → `contactsProvider.notifier.refresh()` → pop/go to `/contacts`
- Offline path: catches `DioExceptionType.connectionError` → `setState(_errorMessage = l10n.errorNetworkWrite)` — does NOT write to Hive
- `ErrorBanner` shown at top with retry callback pointing to `_submit()`

**Tests (3 green):**
- Test 1: valid form → POST called → navigates to Contact List
- Test 2: connectionError → ErrorBanner shown; provider not refreshed (no cache write)
- Test 3: `CircularProgressIndicator` visible during in-flight POST

## TDD Gate Compliance

- RED commit: `5c9136c` — `test(06-09): RED — contact_list_test + add_contact_test (MOBL-06)` (compile failures, no implementation)
- GREEN commit: `9dddfa5` — `feat(06-09): contacts list + add + delete (MOBL-06) — GREEN` (7/7 pass)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing `dart:async` import for `Completer` in test file**
- **Found during:** Task 2 RED→GREEN first test run
- **Issue:** Test 3 used `Completer<Response>` to hold the POST future, but `Completer` is in `dart:async` which wasn't imported.
- **Fix:** Added `import 'dart:async';` to `add_contact_test.dart`.
- **Files modified:** `add_contact_test.dart`
- **Commit:** 9dddfa5

**2. [Rule 1 - Bug] `context.pop()` fails when GoRouter shallow stack (test router starts at `/contacts/add`)**
- **Found during:** Task 2 Test 1 first failure
- **Issue:** Test router has `/contacts/add` as `initialLocation`, so `context.pop()` with a single-item stack does nothing — router stays on add screen instead of navigating back.
- **Fix:** Added `context.canPop()` check; falls back to `context.go('/contacts')`. Works correctly in both production (FAB push creates a pop-able stack) and tests.
- **Files modified:** `add_contact_screen.dart`
- **Commit:** 9dddfa5

**3. [Rule 1 - Bug] Unused import warnings in test files**
- **Found during:** `flutter analyze --no-fatal-infos` after GREEN
- **Issue:** `package:mocktail/mocktail.dart` unused in `contact_list_test.dart`; `package:customer_app/features/contacts/contact_api.dart` unused in `add_contact_test.dart`.
- **Fix:** Removed unused imports from both test files.
- **Files modified:** `contact_list_test.dart`, `add_contact_test.dart`
- **Commit:** 9dddfa5

## Known Stubs

None — all strings from `AppLocalizations`. Contacts data wired to real `ContactsNotifier` (overrideable in tests). No hardcoded placeholder text.

## Threat Flags

None — no new network endpoints introduced. Contacts CRUD consumes existing `/api/v1/contacts` endpoints (not new). Auth flows through existing JWT interceptor.

## Self-Check: PASSED

Files exist:
- `apps/customer-app/lib/features/contacts/contact_api.dart` — FOUND
- `apps/customer-app/lib/features/contacts/contacts_provider.dart` — FOUND
- `apps/customer-app/lib/features/contacts/contact_list_screen.dart` — FOUND
- `apps/customer-app/lib/features/contacts/add_contact_screen.dart` — FOUND
- `apps/customer-app/lib/shared/widgets/contact_list_tile.dart` — FOUND
- `apps/customer-app/test/features/contacts/contact_list_test.dart` — FOUND
- `apps/customer-app/test/features/contacts/add_contact_test.dart` — FOUND

Commits exist:
- 5c9136c — `test(06-09): RED — contact_list_test + add_contact_test (MOBL-06)`
- 9dddfa5 — `feat(06-09): contacts list + add + delete (MOBL-06) — GREEN`
