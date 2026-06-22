// dashboard_screen_test.dart — TDD GREEN
// Widget tests for DashboardScreen (MOBL-04).
//
// Tests:
//   1. Shows balance from API (150 → "150 credits") + 3 CampaignListTiles
//   2. Offline with cached balance → cached value + StaleIndicator
//   3. No campaigns → campaignsEmptyHeading
//   4. Quick-send FAB navigates to /campaigns/new
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/features/dashboard/balance_provider.dart';
import 'package:customer_app/features/dashboard/recent_campaigns_provider.dart';
import 'package:customer_app/features/dashboard/dashboard_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Build helper: wraps DashboardScreen in all required providers.
Widget buildDashboard({
  int credits = 0,
  bool isStale = false,
  List<Map<String, dynamic>> campaigns = const [],
}) {
  final router = GoRouter(
    initialLocation: '/dashboard',
    routes: [
      GoRoute(
        path: '/dashboard',
        builder: (_, __) => const DashboardScreen(),
      ),
      GoRoute(
        path: '/campaigns/new',
        builder: (_, __) =>
            const Scaffold(body: Center(child: Text('Campaign Composer'))),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      balanceProvider.overrideWith(
        (ref) async => BalanceResult(credits: credits, isStale: isStale),
      ),
      recentCampaignsProvider.overrideWith(
        (ref) async => campaigns,
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
  group('DashboardScreen', () {
    testWidgets(
        'Test 1: shows balance 150 credits + 3 recent campaign tiles from API',
        (tester) async {
      final campaigns = [
        {
          'id': '1',
          'name': 'Campaign Alpha',
          'createdAt': '2026-06-01T10:00:00Z',
          'recipientCount': 100,
          'status': 'SENT',
        },
        {
          'id': '2',
          'name': 'Campaign Beta',
          'createdAt': '2026-06-02T10:00:00Z',
          'recipientCount': 50,
          'status': 'QUEUED',
        },
        {
          'id': '3',
          'name': 'Campaign Gamma',
          'createdAt': '2026-06-03T10:00:00Z',
          'recipientCount': 200,
          'status': 'FAILED',
        },
      ];

      await tester.pumpWidget(buildDashboard(
        credits: 150,
        campaigns: campaigns,
      ));
      await tester.pumpAndSettle();

      // Balance shown
      expect(find.text('150 credits'), findsOneWidget);

      // 3 campaign tiles
      expect(find.text('Campaign Alpha'), findsOneWidget);
      expect(find.text('Campaign Beta'), findsOneWidget);
      expect(find.text('Campaign Gamma'), findsOneWidget);
    });

    testWidgets(
        'Test 2: offline with cached balance → shows cached value + StaleIndicator',
        (tester) async {
      await tester.pumpWidget(buildDashboard(
        credits: 75,
        isStale: true,
        campaigns: [],
      ));
      await tester.pumpAndSettle();

      // Cached balance shown
      expect(find.text('75 credits'), findsOneWidget);

      // StaleIndicator present (contains partial text 'saved data')
      expect(find.textContaining('saved data'), findsOneWidget);
    });

    testWidgets('Test 3: no campaigns → campaignsEmptyHeading shown',
        (tester) async {
      await tester.pumpWidget(buildDashboard(
        credits: 100,
        campaigns: [],
      ));
      await tester.pumpAndSettle();

      // Empty state heading
      expect(find.text('No campaigns yet'), findsOneWidget);
    });

    testWidgets('Test 4: quick-send FAB navigates to /campaigns/new',
        (tester) async {
      await tester.pumpWidget(buildDashboard(
        credits: 50,
        campaigns: [],
      ));
      await tester.pumpAndSettle();

      // Find and tap the extended FAB
      final fabFinder = find.byType(FloatingActionButton);
      expect(fabFinder, findsOneWidget);
      await tester.tap(fabFinder);
      await tester.pumpAndSettle();

      // Should navigate to Campaign Composer
      expect(find.text('Campaign Composer'), findsOneWidget);
    });
  });
}
