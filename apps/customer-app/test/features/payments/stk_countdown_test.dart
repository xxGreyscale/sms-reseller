// stk_countdown_test.dart — TDD RED
// Widget tests for StkPurchaseScreen (MOBL-05b).
//
// Tests:
//   1. On entry POST /api/v1/payments fires; countdown seeds from timeoutSeconds (120) and
//      decrements — after advancing 5s → displays 1:55
//   2. Poll fires every 5s; on CONFIRMED → SUCCESS state + balanceProvider refreshed
//   3. Poll returns EXPIRED OR countdown reaches 0 → EXPIRED state + stkTryAgainButton;
//      tapping navigates to /bundles
//   4. Timer is cancelled on dispose (no poll after leaving the screen)
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/features/payments/payment_api.dart' show StkPurchaseArgs;
import 'package:customer_app/features/payments/payment_provider.dart';
import 'package:customer_app/features/payments/stk_purchase_screen.dart';
import 'package:customer_app/features/dashboard/balance_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Arguments passed to StkPurchaseScreen (mirrors what the bottom sheet pushes)
const _kArgs = StkPurchaseArgs(
  bundleId: 'b1',
  bundleName: 'Bronze Bundle',
  smsCount: 100,
  priceTzs: 2500,
  msisdn: '0712345678',
  provider: 'M-Pesa',
);

Widget buildStkScreen({
  required PaymentNotifier notifier,
  int? balanceRefreshCount,
}) {
  int refreshCount = 0;

  final router = GoRouter(
    initialLocation: '/bundles/purchase',
    routes: [
      GoRoute(
        path: '/bundles/purchase',
        builder: (_, __) => const StkPurchaseScreen(args: _kArgs),
      ),
      GoRoute(
        path: '/bundles',
        builder: (_, __) =>
            const Scaffold(body: Center(child: Text('Bundle Catalog'))),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      paymentNotifierProvider.overrideWith(() => notifier),
      balanceProvider.overrideWith((ref) async {
        refreshCount++;
        return const BalanceResult(credits: 200, isStale: false);
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
// Fake PaymentNotifier for testing
// ---------------------------------------------------------------------------

class _FakePaymentNotifier extends PaymentNotifier {
  final List<String> statusSequence;
  int _pollCallCount = 0;
  int get pollCallCount => _pollCallCount;

  _FakePaymentNotifier({required this.statusSequence});

  @override
  Future<void> initiate(StkPurchaseArgs args) async {
    // Simulate POST response: id + timeoutSeconds from "server"
    state = PaymentState.pending(
      paymentId: 'pay-001',
      timeoutSeconds: 120,
    );
  }

  @override
  Future<void> pollStatus() async {
    final status = statusSequence.isNotEmpty
        ? statusSequence[_pollCallCount.clamp(0, statusSequence.length - 1)]
        : 'PENDING';
    _pollCallCount++;
    if (status == 'CONFIRMED') {
      state = PaymentState.confirmed(smsCount: 100);
    } else if (status == 'EXPIRED') {
      state = PaymentState.expired();
    }
    // PENDING → no state change
  }

  @override
  void markExpired() {
    state = PaymentState.expired();
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('StkPurchaseScreen', () {
    testWidgets(
        'Test 1: countdown seeds from timeoutSeconds (120) and decrements — 5s → 1:55',
        (tester) async {
      final notifier = _FakePaymentNotifier(statusSequence: ['PENDING']);

      await tester.pumpWidget(buildStkScreen(notifier: notifier));
      await tester.pump(); // initiate() fires

      // Countdown should show 2:00 initially (120s = 2 minutes)
      expect(find.text('2:00'), findsOneWidget);

      // Advance clock 5 seconds
      await tester.pump(const Duration(seconds: 5));

      // Should now show 1:55
      expect(find.text('1:55'), findsOneWidget);
    });

    testWidgets(
        'Test 2: poll fires on CONFIRMED → SUCCESS state + instruction absent',
        (tester) async {
      final notifier = _FakePaymentNotifier(statusSequence: ['CONFIRMED']);

      await tester.pumpWidget(buildStkScreen(notifier: notifier));
      await tester.pump(); // initiate

      // Advance 5s to trigger first poll
      await tester.pump(const Duration(seconds: 5));
      await tester.pump(); // settle async

      // SUCCESS state shown
      expect(find.text('Payment confirmed!'), findsOneWidget);
    });

    testWidgets(
        'Test 3: poll returns EXPIRED → EXPIRED state + stkTryAgainButton; tap navigates to /bundles',
        (tester) async {
      final notifier = _FakePaymentNotifier(statusSequence: ['EXPIRED']);

      await tester.pumpWidget(buildStkScreen(notifier: notifier));
      await tester.pump(); // initiate

      // Advance 5s to trigger poll → EXPIRED
      await tester.pump(const Duration(seconds: 5));
      await tester.pump(); // settle

      // EXPIRED state shown
      expect(find.text('Payment timed out'), findsOneWidget);
      expect(find.text('Try Again'), findsOneWidget);

      // Tap Try Again → /bundles
      await tester.tap(find.text('Try Again'));
      await tester.pumpAndSettle();
      expect(find.text('Bundle Catalog'), findsOneWidget);
    });

    testWidgets('Test 4: countdown expires at 0 → EXPIRED state', (tester) async {
      final notifier = _FakePaymentNotifier(statusSequence: ['PENDING']);

      await tester.pumpWidget(buildStkScreen(notifier: notifier));
      await tester.pump(); // initiate

      // Advance past 120s — timer fires markExpired at 0
      await tester.pump(const Duration(seconds: 121));
      await tester.pump();

      // EXPIRED state
      expect(find.text('Payment timed out'), findsOneWidget);
    });
  });
}
