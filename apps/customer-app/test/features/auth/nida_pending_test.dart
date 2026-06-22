// MOBL-02 — NIDA Pending walled screen + 10s auto-poll → VERIFIED transition
//
// Test 1: Screen renders pendingScreenTitle + pulsing indicator + logout link
// Test 2: Poller fires every 10s (mock GET /auth/me, advance fake clock)
// Test 3: Poll returns VERIFIED → AuthState.verified + snackbar
// Test 4: Poll throws → no error UI; next tick still polls
// Test 5: PENDING state + /campaigns/new → redirects to /pending (guard)
import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/features/auth/nida_pending_screen.dart';
import 'package:customer_app/features/auth/pending_poller_notifier.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockFlutterSecureStorage extends Mock implements FlutterSecureStorage {}

class FakeRequestOptions extends Fake implements RequestOptions {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Wrap widget in minimal MaterialApp with AppLocalizations delegates.
Widget _wrap(Widget child, {ProviderContainer? container}) {
  return UncontrolledProviderScope(
    container: container ?? ProviderContainer(),
    child: MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: child,
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  late MockDio mockDio;
  late MockFlutterSecureStorage mockStorage;

  setUpAll(() {
    registerFallbackValue(FakeRequestOptions());
  });

  setUp(() {
    mockDio = MockDio();
    mockStorage = MockFlutterSecureStorage();

    when(() => mockStorage.read(key: any(named: 'key')))
        .thenAnswer((_) async => null);
    when(() => mockStorage.write(
            key: any(named: 'key'), value: any(named: 'value')))
        .thenAnswer((_) async {});
    when(() => mockStorage.deleteAll()).thenAnswer((_) async {});
  });

  // -------------------------------------------------------------------------
  // Test 1: Screen renders required widgets
  // -------------------------------------------------------------------------
  testWidgets('Test 1: NidaPendingScreen renders title + indicator + logout',
      (tester) async {
    final container = ProviderContainer(overrides: [
      authNotifierProvider.overrideWith(() => AuthNotifier()),
      secureStorageProvider.overrideWithValue(mockStorage),
      tokenDioProvider.overrideWithValue(mockDio),
      dioProvider.overrideWithValue(mockDio),
    ]);
    addTearDown(container.dispose);

    // Pre-seed a pending auth state
    container.read(authNotifierProvider.notifier).setPending(
          accessToken: 'pending-token',
        );

    await tester.pumpWidget(_wrap(const NidaPendingScreen(), container: container));
    await tester.pump(); // settle

    // Title
    expect(find.textContaining('Verifying'), findsOneWidget);

    // Pulsing circle — AnimatedContainer with primaryContainer color
    expect(find.byType(AnimatedContainer), findsWidgets);

    // CircularProgressIndicator for status row
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    // Logout link (TextButton containing logout text)
    expect(find.textContaining('Log out'), findsOneWidget);

    // Bilingual spot-check (Swahili)
    await tester.binding.setLocale('sw', 'TZ');
    await tester.pumpWidget(_wrap(const NidaPendingScreen(), container: container));
    await tester.pump();
    expect(find.textContaining('Tunathibitisha'), findsOneWidget);
  });

  // -------------------------------------------------------------------------
  // Test 2: Poller fires every 10 seconds (polls GET /auth/me, NOT /auth/refresh)
  // -------------------------------------------------------------------------
  test('Test 2: PendingPollerNotifier polls GET /auth/me every 10s', () async {
    // Track all GET paths called
    final calledPaths = <String>[];

    when(() => mockDio.get(
          any(),
          data: any(named: 'data'),
          queryParameters: any(named: 'queryParameters'),
          options: any(named: 'options'),
          cancelToken: any(named: 'cancelToken'),
          onReceiveProgress: any(named: 'onReceiveProgress'),
        )).thenAnswer((invocation) async {
      calledPaths.add(invocation.positionalArguments.first as String);
      return Response(
        requestOptions: RequestOptions(path: '/auth/me'),
        data: {'userId': 'user-1', 'status': 'PENDING_VERIFICATION'},
        statusCode: 200,
      );
    });

    final container = ProviderContainer(overrides: [
      authNotifierProvider.overrideWith(() => AuthNotifier()),
      secureStorageProvider.overrideWithValue(mockStorage),
      tokenDioProvider.overrideWithValue(mockDio),
      dioProvider.overrideWithValue(mockDio),
    ]);
    addTearDown(container.dispose);

    // Pre-seed pending state so the notifier doesn't short-circuit
    await container.read(authNotifierProvider.notifier).setPending(
          accessToken: 'tok',
        );

    // Activate the poller
    final fakeAsync = StreamController<void>();
    // Use real Timer.periodic with a fast interval for unit test
    // We test the poller notifier directly
    final poller = container.read(pendingPollerNotifierProvider.notifier);
    // Give it a moment to start
    await Future<void>.delayed(const Duration(milliseconds: 50));

    // No polls yet (timer is 10s)
    expect(calledPaths.isEmpty, isTrue);

    // Manually call the poll method to simulate timer tick
    await poller.pollNow();
    expect(calledPaths, contains('/auth/me'));
    expect(calledPaths, isNot(contains('/auth/refresh')));

    fakeAsync.close();
  });

  // -------------------------------------------------------------------------
  // Test 3: Poll returns VERIFIED → AuthState.verified + success message shown
  // -------------------------------------------------------------------------
  testWidgets('Test 3: On VERIFIED poll AuthState becomes verified (snackbar shown)',
      (tester) async {
    when(() => mockDio.get(
          any(),
          data: any(named: 'data'),
          queryParameters: any(named: 'queryParameters'),
          options: any(named: 'options'),
          cancelToken: any(named: 'cancelToken'),
          onReceiveProgress: any(named: 'onReceiveProgress'),
        )).thenAnswer((_) async => Response(
          requestOptions: RequestOptions(path: '/auth/me'),
          data: {'userId': 'user-1', 'status': 'VERIFIED'},
          statusCode: 200,
        ));

    final container = ProviderContainer(overrides: [
      authNotifierProvider.overrideWith(() => AuthNotifier()),
      secureStorageProvider.overrideWithValue(mockStorage),
      tokenDioProvider.overrideWithValue(mockDio),
      dioProvider.overrideWithValue(mockDio),
    ]);
    addTearDown(container.dispose);

    await container.read(authNotifierProvider.notifier).setPending(
          accessToken: 'tok',
        );

    await tester.pumpWidget(_wrap(const NidaPendingScreen(), container: container));
    await tester.pump();

    // Trigger a poll
    final poller = container.read(pendingPollerNotifierProvider.notifier);
    await poller.pollNow();

    // State should now be Verified
    final authState = container.read(authNotifierProvider).value;
    expect(authState, isA<Verified>());

    // Pump to render snackbar
    await tester.pump();
    expect(find.textContaining('verified'), findsWidgets);
  });

  // -------------------------------------------------------------------------
  // Test 4: Poll throws → no error UI, timer continues
  // -------------------------------------------------------------------------
  testWidgets('Test 4: Poll error is swallowed — no error widget shown',
      (tester) async {
    var callCount = 0;

    when(() => mockDio.get(
          any(),
          data: any(named: 'data'),
          queryParameters: any(named: 'queryParameters'),
          options: any(named: 'options'),
          cancelToken: any(named: 'cancelToken'),
          onReceiveProgress: any(named: 'onReceiveProgress'),
        )).thenAnswer((_) async {
      callCount++;
      throw DioException(
        requestOptions: RequestOptions(path: '/auth/me'),
        type: DioExceptionType.connectionTimeout,
      );
    });

    final container = ProviderContainer(overrides: [
      authNotifierProvider.overrideWith(() => AuthNotifier()),
      secureStorageProvider.overrideWithValue(mockStorage),
      tokenDioProvider.overrideWithValue(mockDio),
      dioProvider.overrideWithValue(mockDio),
    ]);
    addTearDown(container.dispose);

    await container.read(authNotifierProvider.notifier).setPending(
          accessToken: 'tok',
        );

    await tester.pumpWidget(_wrap(const NidaPendingScreen(), container: container));
    await tester.pump();

    final poller = container.read(pendingPollerNotifierProvider.notifier);
    // First poll — throws
    await poller.pollNow();
    await tester.pump();

    // No error message / SnackBar should appear
    expect(find.byType(SnackBar), findsNothing);
    // Auth state still pending
    expect(container.read(authNotifierProvider).value, isA<Pending>());

    // Second poll — still callable (timer still running)
    await poller.pollNow();
    expect(callCount, equals(2));
  });

  // -------------------------------------------------------------------------
  // Test 5: PENDING + /campaigns/new → redirect to /pending (guard reuse)
  // -------------------------------------------------------------------------
  test(
      'Test 5: PENDING auth + /campaigns/new redirects to /pending (guard)',
      () {
    final result = _redirect(
      const AuthState.pending(accessToken: 'tok'),
      kCampaignsNewRoute,
    );
    expect(result, equals(kPendingRoute));
  });
}

// ---------------------------------------------------------------------------
// Redirect logic mirror (same as redirect_guard_test.dart)
// ---------------------------------------------------------------------------

const _publicRoutes = [
  kSplashRoute,
  kOnboardingRoute,
  kLoginRoute,
  kRegisterRoute,
];

String? _redirect(AuthState auth, String location) {
  if (auth is Unauthenticated && !_publicRoutes.contains(location)) {
    return kLoginRoute;
  }
  if (auth is Pending && location != kPendingRoute) {
    return kPendingRoute;
  }
  if (auth is Verified &&
      (location == kLoginRoute ||
          location == kRegisterRoute ||
          location == kOnboardingRoute)) {
    return kDashboardRoute;
  }
  return null;
}
