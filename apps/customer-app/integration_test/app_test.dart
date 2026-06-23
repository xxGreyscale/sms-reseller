// app_test.dart — end-to-end integration spine (MOBL-09 / SC5 regression).
//
// Boots the REAL OpenDeskApp (go_router + the full Riverpod provider graph)
// against a mock Dio backend and a temp Hive store, then walks the customer
// journey that proves every wave composes:
//
//   register → PENDING wall → poll /auth/me → VERIFIED → dashboard (balance)
//   → bundle catalog → STK purchase (CONFIRMED, balance refresh)
//   → contacts → compose campaign with contactIds[] → send → campaign detail
//
// Runs under `flutter test` (no device needed): plugins are overridden, real
// HTTP is replaced by a path-routed mock Dio, and timers are torn down by
// disposing the ProviderContainer at the end.
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive.dart';
import 'package:integration_test/integration_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/core/locale/locale_notifier.dart';
import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/features/campaigns/campaign_provider.dart';
import 'package:customer_app/main.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockSecureStorage extends Mock implements FlutterSecureStorage {}

class FakeRequestOptions extends Fake implements RequestOptions {}

/// Builds a fake (non-cryptographic) JWT carrying a verification_status claim,
/// which AuthNotifier decodes locally without a network call.
String _jwt(String status) {
  String b64(String s) =>
      base64Url.encode(utf8.encode(s)).replaceAll('=', '');
  final header = b64('{"alg":"none","typ":"JWT"}');
  final payload = b64('{"sub":"user-1","verification_status":"$status"}');
  return '$header.$payload.sig';
}

Response<dynamic> _ok(String path, dynamic data) => Response<dynamic>(
      requestOptions: RequestOptions(path: path),
      statusCode: 200,
      data: data,
    );

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late MockDio dio;
  late MockSecureStorage storage;
  late Directory hiveDir;
  // Mutable access token so the test can flip PENDING → VERIFIED mid-flow.
  late String accessToken;

  setUpAll(() async {
    registerFallbackValue(FakeRequestOptions());
    hiveDir = await Directory.systemTemp.createTemp('opendesk_e2e_hive');
    Hive.init(hiveDir.path);
  });

  tearDownAll(() async {
    await Hive.close();
    if (hiveDir.existsSync()) hiveDir.deleteSync(recursive: true);
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    accessToken = _jwt('PENDING_VERIFICATION');

    // Fresh Hive boxes per run.
    await Hive.openBox<int>('balance');
    await Hive.openBox<Map>('contacts');
    await Hive.openBox<Map>('campaigns');
    await Hive.openBox<Map>('notifications');
    await Hive.box<int>('balance').clear();
    await Hive.box<Map>('contacts').clear();
    await Hive.box<Map>('campaigns').clear();
    await Hive.box<Map>('notifications').clear();

    storage = MockSecureStorage();
    when(() => storage.read(key: kAccessTokenKey))
        .thenAnswer((_) async => accessToken);
    when(() => storage.read(key: kRefreshTokenKey))
        .thenAnswer((_) async => 'refresh-token');
    when(() => storage.write(
        key: any(named: 'key'),
        value: any(named: 'value'))).thenAnswer((_) async {});
    when(() => storage.deleteAll()).thenAnswer((_) async {});

    dio = MockDio();
    when(() => dio.get(any(), queryParameters: any(named: 'queryParameters')))
        .thenAnswer((inv) async {
      final path = inv.positionalArguments[0] as String;
      if (path == '/auth/me') return _ok(path, {'status': 'VERIFIED'});
      if (path == '/api/v1/wallet/balance') {
        return _ok(path, {'availableCredits': 200});
      }
      if (path == '/api/v1/bundles') {
        return _ok(path, [
          {
            'id': 'b1',
            'name': 'Bronze Bundle',
            'smsCount': 100,
            'priceTzs': 2500,
            'active': true,
          },
        ]);
      }
      if (path.endsWith('/messages')) return _ok(path, {'content': []});
      if (path.startsWith('/api/v1/payments/')) {
        return _ok(path, {'id': 'pay-001', 'status': 'CONFIRMED'});
      }
      if (path == '/api/v1/contacts') {
        return _ok(path, [
          {'id': 'c1', 'name': 'Asha Mbeki', 'phoneE164': '+255712345678'},
        ]);
      }
      if (path == '/api/v1/notifications') return _ok(path, {'content': []});
      if (path == '/api/v1/campaigns') return _ok(path, {'content': []});
      if (path.startsWith('/api/v1/campaigns/')) {
        return _ok(path, {
          'id': 'camp-1',
          'name': 'Welcome blast',
          'status': 'QUEUED',
          'body': 'Karibu!',
          'totalRecipients': 1,
        });
      }
      return _ok(path, {});
    });
    when(() => dio.post(any(), data: any(named: 'data')))
        .thenAnswer((inv) async {
      final path = inv.positionalArguments[0] as String;
      if (path == '/api/v1/payments') {
        return _ok(path, {
          'id': 'pay-001',
          'status': 'PENDING',
          'timeoutSeconds': 120,
        });
      }
      if (path == '/api/v1/contacts') {
        return _ok(path,
            {'id': 'c2', 'name': 'Juma Said', 'phoneE164': '+255713000000'});
      }
      if (path.endsWith('/send')) return _ok(path, {'status': 'QUEUED'});
      if (path == '/api/v1/campaigns') {
        return _ok(path, {'id': 'camp-1', 'status': 'QUEUED'});
      }
      return _ok(path, {});
    });
    when(() => dio.delete(any())).thenAnswer(
        (inv) async => _ok(inv.positionalArguments[0] as String, {}));
  });

  Future<ProviderContainer> bootApp(WidgetTester tester) async {
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(overrides: [
      sharedPreferencesProvider.overrideWithValue(prefs),
      secureStorageProvider.overrideWithValue(storage),
      tokenDioProvider.overrideWithValue(dio),
      dioProvider.overrideWithValue(dio),
    ]);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const OpenDeskApp(),
      ),
    );
    // Let splash resolve auth + router redirect run.
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    return container;
  }

  String _location(ProviderContainer c) =>
      c.read(appRouterProvider).routerDelegate.currentConfiguration.uri
          .toString();

  testWidgets(
      'e2e spine: PENDING wall → VERIFIED → dashboard → purchase → send → detail',
      (tester) async {
    final container = await bootApp(tester);
    final router = container.read(appRouterProvider);

    // --- 1. Walled at PENDING (no dashboard access while unverified) ---
    expect(_location(container), kPendingRoute);

    // --- 2. /auth/me returns VERIFIED → wall lifts → dashboard reachable ---
    accessToken = _jwt('VERIFIED');
    await container.read(authNotifierProvider.notifier).refreshAndCheckStatus();
    for (var i = 0; i < 5; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    // Pre-verify the guard bounced dashboard → /pending; now it is allowed.
    router.go(kDashboardRoute);
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    expect(_location(container), kDashboardRoute);
    // Balance from GET /api/v1/wallet/balance is rendered.
    expect(find.textContaining('200'), findsWidgets);

    // --- 3. Bundle catalog → STK purchase → CONFIRMED ---
    router.go(kBundlesRoute);
    for (var i = 0; i < 3; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    expect(find.text('Bronze Bundle'), findsOneWidget);

    router.go('$kBundlesPurchaseRoute?bundleId=b1&bundleName=Bronze%20Bundle'
        '&smsCount=100&priceTzs=2500&msisdn=0712345678&provider=M-Pesa');
    await tester.pump(); // mount
    await tester.pump(); // post-frame initiate()
    await tester.pump(const Duration(milliseconds: 100));
    // Countdown seeded from server timeoutSeconds = 120.
    expect(find.text('2:00'), findsOneWidget);
    // Advance 5s → status poll returns CONFIRMED → SUCCESS layout.
    await tester.pump(const Duration(seconds: 5));
    await tester.pump();
    expect(find.text('Payment confirmed!'), findsOneWidget);

    // --- 4. Contacts list loads from API ---
    router.go(kContactsRoute);
    for (var i = 0; i < 3; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    expect(find.text('Asha Mbeki'), findsOneWidget);

    // --- 5. Send a campaign targeting contactIds[] (real send orchestrator) ---
    final sendResult = await container
        .read(campaignSendProvider.notifier)
        .sendCampaign(
          name: 'Welcome blast',
          body: 'Karibu!',
          contactIds: const ['c1'],
        );
    expect(sendResult, isA<SendSuccess>());
    final campaignId = (sendResult as SendSuccess).campaignId;
    expect(campaignId, 'camp-1');

    // --- 6. Campaign detail renders the created campaign ---
    router.go('/campaigns/$campaignId');
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    expect(find.textContaining('Welcome blast'), findsWidgets);

    // Tear down: cancels all periodic timers (pending poller, notif feed, etc.)
    container.dispose();
  });
}
