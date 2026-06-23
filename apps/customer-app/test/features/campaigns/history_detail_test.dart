// Task 3 RED: Campaign history + detail (MOBL-08)
//
// Behaviors:
// 1. History renders a page of campaigns; scrolling near bottom loads next page
// 2. Empty history → campaignsEmptyHeading
// 3. Detail shows aggregate total/delivered/failed stat tiles + overall status chip
//    + per-message rows (failed row tinted)

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/campaigns/campaign_provider.dart';
import 'package:customer_app/features/campaigns/history_screen.dart';
import 'package:customer_app/features/campaigns/detail_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Fake history notifiers
// ---------------------------------------------------------------------------

class FakeCampaignHistoryNotifier extends CampaignHistoryNotifier {
  final List<CampaignResponse> _page0;
  final List<CampaignResponse> _page1;

  FakeCampaignHistoryNotifier({
    required List<CampaignResponse> page0,
    List<CampaignResponse>? page1,
  })  : _page0 = page0,
        _page1 = page1 ?? [];

  @override
  Future<List<CampaignResponse>> build() async => _page0;

  @override
  bool get hasMore => _page1.isNotEmpty;

  @override
  Future<void> loadMore() async {
    final current = state.value ?? [];
    state = AsyncValue.data([...current, ..._page1]);
  }

  @override
  Future<void> refresh() async {}
}

// ---------------------------------------------------------------------------
// Widget helpers
// ---------------------------------------------------------------------------

Widget buildHistory({required FakeCampaignHistoryNotifier notifier}) {
  final router = GoRouter(
    initialLocation: '/campaigns',
    routes: [
      GoRoute(
        path: '/campaigns',
        builder: (_, __) => const CampaignHistoryScreen(),
      ),
      GoRoute(
        path: '/campaigns/new',
        builder: (_, __) => const Scaffold(body: Text('Composer')),
      ),
      GoRoute(
        path: '/campaigns/:id',
        builder: (_, state) => DetailScreen(
          campaignId: state.pathParameters['id']!,
        ),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      campaignHistoryProvider.overrideWith(() => notifier),
    ],
    child: MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}

Widget buildDetail({
  required String campaignId,
  required Map<String, dynamic> campaignJson,
  required List<Map<String, dynamic>> messages,
}) {
  return ProviderScope(
    child: MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: DetailScreenTestable(
        campaignId: campaignId,
        campaignJson: campaignJson,
        messages: messages,
      ),
    ),
  );
}

void main() {
  // ---------------------------------------------------------------------------
  // Test 1: History renders page + loads more on scroll
  // ---------------------------------------------------------------------------

  testWidgets('Test 1: history renders campaigns; loadMore called on scroll',
      (tester) async {
    final page0 = List.generate(
      5,
      (i) => CampaignResponse(
        id: 'c$i',
        name: 'Campaign $i',
        status: 'SENT',
        createdAt: '2026-06-01',
        totalRecipients: 10,
      ),
    );
    final page1 = [
      const CampaignResponse(
        id: 'c_extra',
        name: 'Extra Campaign',
        status: 'QUEUED',
        createdAt: '2026-06-02',
        totalRecipients: 5,
      ),
    ];

    final notifier =
        FakeCampaignHistoryNotifier(page0: page0, page1: page1);

    await tester.pumpWidget(buildHistory(notifier: notifier));
    await tester.pumpAndSettle();

    // Page 0 visible
    expect(find.text('Campaign 0'), findsOneWidget);
    expect(find.text('Campaign 4'), findsOneWidget);

    // Simulate scrolling near bottom to trigger loadMore
    await tester.drag(
        find.byType(CustomScrollView), const Offset(0, -3000));
    await tester.pumpAndSettle();

    // Page 1 loaded
    expect(find.text('Extra Campaign'), findsOneWidget);
  });

  // ---------------------------------------------------------------------------
  // Test 2: Empty history → campaignsEmptyHeading
  // ---------------------------------------------------------------------------

  testWidgets('Test 2: empty history shows campaignsEmptyHeading',
      (tester) async {
    final notifier = FakeCampaignHistoryNotifier(page0: [], page1: []);
    await tester.pumpWidget(buildHistory(notifier: notifier));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('campaignsEmptyHeading')), findsOneWidget);
  });

  // ---------------------------------------------------------------------------
  // Test 3: Detail shows stat tiles + status chip + per-message rows
  // ---------------------------------------------------------------------------

  testWidgets('Test 3: detail shows aggregate stats + per-message rows with failed tint',
      (tester) async {
    final campaignJson = {
      'id': 'camp-1',
      'name': 'My Campaign',
      'status': 'SENT',
      'totalRecipients': 10,
      'deliveredCount': 7,
      'failedCount': 3,
    };
    final messages = [
      {'recipient': '+255700000001', 'status': 'DELIVERED'},
      {'recipient': '+255700000002', 'status': 'FAILED'},
    ];

    await tester.pumpWidget(buildDetail(
      campaignId: 'camp-1',
      campaignJson: campaignJson,
      messages: messages,
    ));
    await tester.pumpAndSettle();

    // Stat tiles
    expect(find.text('10'), findsOneWidget); // total
    expect(find.text('7'), findsOneWidget);  // delivered
    expect(find.text('3'), findsOneWidget);  // failed

    // Status chip
    expect(find.byKey(const Key('campaignStatusChip')), findsOneWidget);

    // Per-message rows
    expect(find.text('+255700000001'), findsOneWidget);
    expect(find.text('+255700000002'), findsOneWidget);

    // Failed row tinted — verify the container with red-50 background
    expect(find.byKey(const Key('failedMessageRow')), findsOneWidget);
  });
}
