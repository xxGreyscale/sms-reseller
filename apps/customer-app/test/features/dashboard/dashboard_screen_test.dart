// dashboard_screen_test.dart — TDD RED
// Widget tests for DashboardScreen (MOBL-04).
//
// Tests:
//   1. Shows balance from API (150 → "150 credits") + 3 CampaignListTiles
//   2. Offline with cached balance → cached value + StaleIndicator
//   3. No campaigns → campaignsEmptyHeading
//   4. Quick-send FAB navigates to /campaigns/new
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hive_ce/hive.dart';
import 'package:hive_ce_flutter/hive_flutter.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/dashboard/balance_provider.dart';
import 'package:customer_app/features/dashboard/recent_campaigns_provider.dart';
import 'package:customer_app/features/dashboard/dashboard_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockBox extends Mock implements Box<int> {}

class MockCampaignsBox extends Mock implements Box<Map> {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Build helper: wraps DashboardScreen in all required providers.
Widget buildDashboard({
  required Dio mockDio,
  int? cachedBalance,
  List<Map<String, dynamic>> campaigns = const [],
  bool campaignError = false,
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
      dioClientProvider.overrideWithValue(mockDio),
      balanceProvider.overrideWith((ref) async {
        if (cachedBalance != null) {
          // Simulate cached read first (stale path)
          return cachedBalance;
        }
        final data = await mockDio.get('/api/v1/wallet/balance');
        return (data.data as Map<String, dynamic>)['availableCredits'] as int;
      }),
      recentCampaignsProvider.overrideWith((ref) async {
        if (campaignError) {
          throw DioException(
            requestOptions: RequestOptions(path: ''),
            type: DioExceptionType.connectionError,
          );
        }
        return campaigns;
      }),
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
  setUpAll(() {
    registerFallbackValue(RequestOptions(path: ''));
  });

  group('DashboardScreen', () {
    testWidgets(
        'Test 1: shows balance 150 credits + 3 recent campaign tiles from API',
        (tester) async {
      final mockDio = MockDio();
      when(() => mockDio.get('/api/v1/wallet/balance')).thenAnswer(
        (_) async => Response(
          data: {'availableCredits': 150},
          statusCode: 200,
          requestOptions: RequestOptions(path: ''),
        ),
      );

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
        mockDio: mockDio,
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
      final mockDio = MockDio();
      // Dio throws — offline
      when(() => mockDio.get(any())).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: ''),
          type: DioExceptionType.connectionError,
        ),
      );

      await tester.pumpWidget(buildDashboard(
        mockDio: mockDio,
        cachedBalance: 75, // pre-seeded cache
      ));
      await tester.pumpAndSettle();

      // Cached balance shown
      expect(find.text('75 credits'), findsOneWidget);

      // StaleIndicator present (contains 'saved data' partial text)
      expect(
        find.textContaining('saved data'),
        findsOneWidget,
      );
    });

    testWidgets('Test 3: no campaigns → campaignsEmptyHeading shown',
        (tester) async {
      final mockDio = MockDio();
      when(() => mockDio.get('/api/v1/wallet/balance')).thenAnswer(
        (_) async => Response(
          data: {'availableCredits': 100},
          statusCode: 200,
          requestOptions: RequestOptions(path: ''),
        ),
      );

      await tester.pumpWidget(buildDashboard(
        mockDio: mockDio,
        campaigns: [],
      ));
      await tester.pumpAndSettle();

      // Empty state heading
      expect(find.text('No campaigns yet'), findsOneWidget);
    });

    testWidgets('Test 4: quick-send FAB navigates to /campaigns/new',
        (tester) async {
      final mockDio = MockDio();
      when(() => mockDio.get('/api/v1/wallet/balance')).thenAnswer(
        (_) async => Response(
          data: {'availableCredits': 50},
          statusCode: 200,
          requestOptions: RequestOptions(path: ''),
        ),
      );

      await tester.pumpWidget(buildDashboard(
        mockDio: mockDio,
        campaigns: [],
      ));
      await tester.pumpAndSettle();

      // Tap the FAB
      await tester.tap(find.text('Send SMS'));
      await tester.pumpAndSettle();

      // Should navigate to Campaign Composer placeholder
      expect(find.text('Campaign Composer'), findsOneWidget);
    });
  });
}
