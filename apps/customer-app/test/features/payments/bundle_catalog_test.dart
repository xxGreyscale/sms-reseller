// bundle_catalog_test.dart — TDD RED
// Widget tests for BundleCatalogScreen (MOBL-05a).
//
// Tests:
//   1. Catalog renders all active bundles sorted ascending by priceTzs with TZS-formatted prices
//   2. No active bundles → bundlesEmptyHeading
//   3. Tapping Buy opens bottom sheet with msisdn field + provider SegmentedButton;
//      confirming navigates to /bundles/purchase
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/features/payments/payment_api.dart';
import 'package:customer_app/features/payments/bundle_catalog_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Widget buildCatalog({
  List<Map<String, dynamic>> bundles = const [],
}) {
  final router = GoRouter(
    initialLocation: '/bundles',
    routes: [
      GoRoute(
        path: '/bundles',
        builder: (_, __) => const BundleCatalogScreen(),
      ),
      GoRoute(
        path: '/bundles/purchase',
        builder: (_, state) => Scaffold(
          body: Center(
            child: Text('Purchase:${state.uri.queryParameters['bundleId']}'),
          ),
        ),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      bundlesProvider.overrideWith((ref) async => bundles),
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
  group('BundleCatalogScreen', () {
    testWidgets(
        'Test 1: renders active bundles sorted ascending by priceTzs with TZS prices',
        (tester) async {
      final bundles = [
        {
          'id': 'b2',
          'name': 'Silver Bundle',
          'smsCount': 500,
          'priceTzs': 10000,
          'active': true,
        },
        {
          'id': 'b1',
          'name': 'Bronze Bundle',
          'smsCount': 100,
          'priceTzs': 2500,
          'active': true,
        },
        {
          'id': 'b3',
          'name': 'Gold Bundle',
          'smsCount': 2000,
          'priceTzs': 35000,
          'active': true,
        },
      ];

      await tester.pumpWidget(buildCatalog(bundles: bundles));
      await tester.pumpAndSettle();

      // Bundles are rendered
      expect(find.text('Bronze Bundle'), findsOneWidget);
      expect(find.text('Silver Bundle'), findsOneWidget);
      expect(find.text('Gold Bundle'), findsOneWidget);

      // TZS-formatted prices
      expect(find.text('TZS 2,500'), findsOneWidget);
      expect(find.text('TZS 10,000'), findsOneWidget);
      expect(find.text('TZS 35,000'), findsOneWidget);

      // Ascending order — Bronze before Silver before Gold in the list
      final bronzePos = tester.getTopLeft(find.text('Bronze Bundle')).dy;
      final silverPos = tester.getTopLeft(find.text('Silver Bundle')).dy;
      final goldPos = tester.getTopLeft(find.text('Gold Bundle')).dy;
      expect(bronzePos, lessThan(silverPos));
      expect(silverPos, lessThan(goldPos));
    });

    testWidgets('Test 2: no active bundles → bundlesEmptyHeading', (tester) async {
      await tester.pumpWidget(buildCatalog(bundles: []));
      await tester.pumpAndSettle();

      expect(find.text('No bundles available'), findsOneWidget);
    });

    testWidgets(
        'Test 3: tapping Buy opens bottom sheet with msisdn + provider SegmentedButton; confirm navigates',
        (tester) async {
      final bundles = [
        {
          'id': 'b1',
          'name': 'Bronze Bundle',
          'smsCount': 100,
          'priceTzs': 2500,
          'active': true,
        },
      ];

      await tester.pumpWidget(buildCatalog(bundles: bundles));
      await tester.pumpAndSettle();

      // Tap Buy Bundle button
      await tester.tap(find.text('Buy Bundle'));
      await tester.pumpAndSettle();

      // Bottom sheet is visible with msisdn field
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(find.byType(TextFormField), findsOneWidget);

      // 5 Azampay providers present
      expect(find.text('M-Pesa'), findsOneWidget);
      expect(find.text('Tigo'), findsOneWidget);
      expect(find.text('Airtel'), findsOneWidget);
      expect(find.text('Halo'), findsOneWidget);
      expect(find.text('AzamPesa'), findsOneWidget);

      // Enter msisdn and confirm
      await tester.enterText(find.byType(TextFormField), '0712345678');
      await tester.pumpAndSettle();
      await tester.tap(find.text('Confirm Purchase'));
      await tester.pumpAndSettle();

      // Should navigate to /bundles/purchase
      expect(find.textContaining('Purchase:'), findsOneWidget);
    });
  });
}
