import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/notifications/notification_api.dart';

/// Notifier that maintains the notification feed with 30-second polling.
///
/// - Fetches GET /api/v1/notifications on construction.
/// - Polls every 30 seconds via [Timer.periodic].
/// - Timer is cancelled in [dispose] (no leak).
/// - [unreadCount] = _items.where((n) => !n.read).length (client-derived, D-14).
class NotificationFeedNotifier extends Notifier<List<NotificationItem>> {
  Timer? _timer;
  final Future<List<NotificationItem>> Function()? _fetchOverride;

  // Mirror of state kept for unit-testable access without Riverpod machinery.
  List<NotificationItem> _items = [];

  NotificationFeedNotifier() : _fetchOverride = null;

  NotificationFeedNotifier._testable(
      Future<List<NotificationItem>> Function() fetchFn)
      : _fetchOverride = fetchFn;

  NotificationFeedNotifier._withValue(List<NotificationItem> items)
      : _fetchOverride = null,
        _items = items;

  @override
  List<NotificationItem> build() {
    _startPolling();
    ref.onDispose(_cancelTimer);
    return _items;
  }

  void _startPolling() {
    _fetch();
    _timer = Timer.periodic(const Duration(seconds: 30), (_) => _fetch());
  }

  void _cancelTimer() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _fetch() async {
    try {
      final items = _fetchOverride != null
          ? await _fetchOverride!()
          : await fetchNotifications(ref.read(dioProvider));
      _items = items;
      // Only update Riverpod state when mounted inside a ProviderScope.
      try {
        state = items;
      } catch (_) {
        // Not mounted (unit test path) — ignore.
      }
    } catch (_) {
      // Swallow poll errors silently; keep current state.
    }
  }

  /// Client-derived unread count (D-14 — no PATCH endpoint required).
  int get unreadCount => _items.where((n) => !n.read).length;

  /// Mark all currently fetched items as read locally.
  void markAllRead() {
    _items = _items.map((n) => n.markRead()).toList();
    try {
      state = _items;
    } catch (_) {}
  }

  // ---------------------------------------------------------------------------
  // Test factory constructors
  // ---------------------------------------------------------------------------

  /// Creates a notifier pre-seeded with [value] for widget tests (no timer).
  static NotificationFeedNotifier test(
          AsyncValue<List<NotificationItem>> value) =>
      NotificationFeedNotifier._withValue(value.value ?? []);

  /// Creates a production-like notifier that uses [fetchFn] instead of Dio
  /// for unit tests that control the clock.
  static NotificationFeedNotifier testable(
      Future<List<NotificationItem>> Function() fetchFn) {
    final n = NotificationFeedNotifier._testable(fetchFn);
    n._startPolling();
    return n;
  }

  /// Called by testable instances to clean up the timer.
  void dispose() => _cancelTimer();
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/// The global notification feed provider.
final notificationFeedProvider =
    NotifierProvider<NotificationFeedNotifier, List<NotificationItem>>(
  NotificationFeedNotifier.new,
);
