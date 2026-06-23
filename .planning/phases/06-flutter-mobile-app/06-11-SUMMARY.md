---
phase: 06-flutter-mobile-app
plan: "11"
subsystem: customer-app/notifications
tags: [flutter, riverpod, notifications, polling, badge, tdd, material3]
requirements: [MOBL-04]

dependency_graph:
  requires: ["06-01", "06-07"]
  provides: ["NotificationFeedScreen", "NotificationBadge", "notification_provider (30s poll)", "notification_api", "unread count derivation"]
  affects: ["06-12"]

tech_stack:
  added: []
  patterns:
    - "Notification feed polls GET /api/v1/notifications every 30s via a Timer-backed Riverpod provider; timer cancelled on dispose (no fetch after teardown)"
    - "Unread count derived client-side as count(read == false) — no separate server count call"
    - "Notification type → Material icon mapping (6 types); unread rows use primaryContainer tileColor"
    - "NotificationBadge shared widget wraps the bell icon with a count badge; tapping routes to the feed"
    - "Dashboard bell swapped from a plain IconButton to NotificationBadge"

key_files:
  created:
    - apps/customer-app/lib/features/notifications/notification_api.dart
    - apps/customer-app/lib/features/notifications/notification_provider.dart
    - apps/customer-app/lib/features/notifications/notification_feed_screen.dart
    - apps/customer-app/lib/shared/widgets/notification_badge.dart
    - apps/customer-app/test/features/notifications/notification_feed_test.dart
  modified:
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/lib/features/dashboard/dashboard_screen.dart
---

# 06-11 — In-app notification feed + unread badge (D-01, supports MOBL-04)

## What was built

- `notification_api.dart` + `notification_provider.dart`: a polling provider that
  fetches `GET /api/v1/notifications` every 30 seconds and exposes the list plus a
  client-derived unread count (`count(read == false)`). The timer is cancelled on
  dispose so no fetch fires after the provider is torn down.
- `NotificationFeedScreen`: renders the feed with each notification type mapped to its
  UI-SPEC icon (6 types), unread rows highlighted with `primaryContainer` tileColor,
  and an empty-state heading.
- `NotificationBadge` shared widget: bell icon with an unread-count badge; tapping
  routes to the feed. Wired into the dashboard AppBar (replacing the plain bell
  `IconButton`) and the `/notifications` route (replacing the placeholder).

## Verification

- `flutter test test/features/notifications/` → **6/6 pass** (30s poll, unread count,
  unread tileColor + icons + all 6 type icons mapped, empty state, dispose-cancels-timer).
- Implementation + GREEN committed; clean working tree.

## TDD gate

- `a492731` test(06-11): RED — notification_feed_test (D-01 polling, unread badge, type icons, dispose)
- `af6c3cc` feat(06-11): 30s polling notification feed + NotificationBadge + type→icon screen (D-01) — GREEN

## Decisions & deviations

- **Resumed after a sandbox permission cutoff** that blocked only the SUMMARY.md write;
  all code + tests were already committed GREEN.
- Touched two files beyond the declared `files_modified` (deviation, intentional):
  `app_router.dart` (wire `/notifications` → `NotificationFeedScreen`) and
  `dashboard_screen.dart` (swap bell → `NotificationBadge`). These overlap with other
  Wave 4 plans on `app_router.dart`; resolved at post-wave merge.
