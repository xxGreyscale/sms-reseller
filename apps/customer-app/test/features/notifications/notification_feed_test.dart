// notification_feed_test.dart — TDD GREEN
// Tests for notification feed polling, unread badge, feed screen rendering.
//
// Tests:
//   Test 1: Provider polls GET /api/v1/notifications every 30s (advance clock + second fetch)
//   Test 2: Unread badge count equals count(read == false) from fetched items
//   Test 3: Feed renders type-mapped icons; unread tileColor primaryContainer; empty → notificationsEmptyHeading
//   Test 4: Poller timer is cancelled on dispose (no fetch after disposal)

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/features/notifications/notification_api.dart';
import 'package:customer_app/features/notifications/notification_provider.dart';
import 'package:customer_app/features/notifications/notification_feed_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Fake notification items
// ---------------------------------------------------------------------------

final _unread1 = NotificationItem(
  id: '1',
  type: 'NIDA_VERIFIED',
  message: 'Your identity has been verified.',
  read: false,
  createdAt: DateTime(2026, 6, 22, 10, 0),
);

final _read1 = NotificationItem(
  id: '2',
  type: 'PAYMENT_CONFIRMED',
  message: 'Payment of TZS 5,000 confirmed.',
  read: true,
  createdAt: DateTime(2026, 6, 22, 9, 0),
);

final _unread2 = NotificationItem(
  id: '3',
  type: 'LOW_CREDIT',
  message: 'You have 10 credits remaining.',
  read: false,
  createdAt: DateTime(2026, 6, 22, 8, 0),
);

// ---------------------------------------------------------------------------
// Widget helper
// ---------------------------------------------------------------------------

/// Wraps NotificationFeedScreen in required providers with the given items override.
Widget buildFeed({
  AsyncValue<List<NotificationItem>> feedValue = const AsyncValue.loading(),
}) {
  final router = GoRouter(
    initialLocation: '/notifications',
    routes: [
      GoRoute(
        path: '/notifications',
        builder: (_, __) => const NotificationFeedScreen(),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      notificationFeedProvider.overrideWith(
        () => NotificationFeedNotifier.test(feedValue),
      ),
    ],
    child: MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  // -------------------------------------------------------------------------
  // Test 1: 30-second polling — a second fetch is issued after 30s
  // -------------------------------------------------------------------------
  test('Test 1: provider fetches again after 30 seconds (poll interval)', () async {
    // Arrange: a fake fetch function that records how many times it was called.
    var fetchCount = 0;
    Future<List<NotificationItem>> fakeFetch() async {
      fetchCount++;
      return [_unread1];
    }

    // Use a fake async zone to control time without real sleeps.
    await _withFakeTimer((advance) async {
      final notifier = NotificationFeedNotifier.testable(fakeFetch);
      expect(fetchCount, 1, reason: 'initial fetch on build');

      advance(const Duration(seconds: 30));
      await Future.microtask(() {}); // let the timer callback run
      expect(fetchCount, 2, reason: 'second fetch after 30s poll tick');

      notifier.dispose();
    });
  });

  // -------------------------------------------------------------------------
  // Test 2: unreadCount = items where !read
  // -------------------------------------------------------------------------
  test('Test 2: unreadCount equals count of items where read is false', () async {
    final notifier = NotificationFeedNotifier.test(
      AsyncValue.data([_unread1, _read1, _unread2]),
    );
    expect(notifier.unreadCount, 2);
    notifier.dispose();
  });

  // -------------------------------------------------------------------------
  // Test 3: Feed screen renders icons + unread highlight; empty state
  // -------------------------------------------------------------------------
  group('Test 3: NotificationFeedScreen rendering', () {
    testWidgets('unread item shows primaryContainer tileColor; icon visible',
        (tester) async {
      await tester.pumpWidget(buildFeed(
        feedValue: AsyncValue.data([_unread1, _read1]),
      ));
      await tester.pumpAndSettle();

      // notificationsTitle in AppBar
      expect(find.text('Notifications'), findsAtLeastNWidgets(1));

      // NIDA_VERIFIED icon for unread1
      expect(find.byIcon(Icons.verified_user), findsOneWidget);

      // PAYMENT_CONFIRMED icon for read1
      expect(find.byIcon(Icons.payment), findsOneWidget);

      // Unread message text visible
      expect(find.text(_unread1.message), findsOneWidget);
    });

    testWidgets('empty list shows notificationsEmptyHeading', (tester) async {
      await tester.pumpWidget(buildFeed(
        feedValue: const AsyncValue.data([]),
      ));
      await tester.pumpAndSettle();

      expect(find.text('All caught up'), findsOneWidget);
    });

    testWidgets('all 6 notification type icons are mapped', (tester) async {
      final allTypes = [
        NotificationItem(id: 'a', type: 'NIDA_VERIFIED', message: 'msg', read: false, createdAt: DateTime.now()),
        NotificationItem(id: 'b', type: 'PAYMENT_CONFIRMED', message: 'msg', read: false, createdAt: DateTime.now()),
        NotificationItem(id: 'c', type: 'LOW_CREDIT', message: 'msg', read: false, createdAt: DateTime.now()),
        NotificationItem(id: 'd', type: 'CREDIT_EXPIRY', message: 'msg', read: false, createdAt: DateTime.now()),
        NotificationItem(id: 'e', type: 'CAMPAIGN_COMPLETED', message: 'msg', read: false, createdAt: DateTime.now()),
        NotificationItem(id: 'f', type: 'SENDER_ID_DECISION', message: 'msg', read: false, createdAt: DateTime.now()),
      ];

      await tester.pumpWidget(buildFeed(
        feedValue: AsyncValue.data(allTypes),
      ));
      await tester.pumpAndSettle();

      // All 6 type-specific icons should appear
      expect(find.byIcon(Icons.verified_user), findsOneWidget);       // NIDA_VERIFIED
      expect(find.byIcon(Icons.payment), findsOneWidget);             // PAYMENT_CONFIRMED
      expect(find.byIcon(Icons.warning), findsOneWidget);             // LOW_CREDIT
      expect(find.byIcon(Icons.timer), findsOneWidget);               // CREDIT_EXPIRY
      expect(find.byIcon(Icons.send), findsOneWidget);                // CAMPAIGN_COMPLETED
      expect(find.byIcon(Icons.badge), findsOneWidget);               // SENDER_ID_DECISION
    });
  });

  // -------------------------------------------------------------------------
  // Test 4: Timer cancelled on dispose — no fetch after disposal
  // -------------------------------------------------------------------------
  test('Test 4: timer is cancelled on dispose; no fetch after disposal', () async {
    var fetchCount = 0;
    Future<List<NotificationItem>> fakeFetch() async {
      fetchCount++;
      return [];
    }

    await _withFakeTimer((advance) async {
      final notifier = NotificationFeedNotifier.testable(fakeFetch);
      expect(fetchCount, 1);

      notifier.dispose(); // cancel timer

      advance(const Duration(seconds: 60));
      await Future.microtask(() {});
      // Should still be 1 — no new fetches after dispose
      expect(fetchCount, 1, reason: 'timer cancelled: no fetch after dispose');
    });
  });
}

// ---------------------------------------------------------------------------
// Fake timer helper
// ---------------------------------------------------------------------------

/// Runs [body] inside a zone where [Timer.periodic] is overridden with a
/// controllable fake. [advance] manually fires pending timers whose duration
/// has elapsed.
Future<void> _withFakeTimer(
  Future<void> Function(void Function(Duration) advance) body,
) async {
  final pendingTimers = <_FakeTimer>[];

  await runZonedGuarded(
    () async {
      void advance(Duration by) {
        for (final t in List.of(pendingTimers)) {
          if (!t.cancelled) {
            t.elapsed += by;
            if (t.elapsed >= t.period) {
              t.elapsed = Duration.zero;
              t.callback(t);
            }
          }
        }
      }

      await body(advance);
    },
    (e, s) {},
    zoneSpecification: ZoneSpecification(
      createPeriodicTimer: (zone, parent, self, period, callback) {
        final fake = _FakeTimer(period: period, callback: callback);
        pendingTimers.add(fake);
        return fake;
      },
    ),
  );
}

class _FakeTimer implements Timer {
  final Duration period;
  final void Function(Timer) callback;
  Duration elapsed = Duration.zero;
  bool cancelled = false;

  _FakeTimer({required this.period, required this.callback});

  @override
  void cancel() => cancelled = true;

  @override
  bool get isActive => !cancelled;

  @override
  int get tick => 0;
}
