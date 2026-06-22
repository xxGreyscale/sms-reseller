// MOBL-03: Login + session persistence tests
// Test 2: login → tokens in mock storage + AuthState changes
// Test 3: session restore — tokens pre-seeded → boot into non-unauth state
// Test 4: wrong credentials → errorLoginFailed inline, no snackbar
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/features/auth/login_screen.dart';
import 'package:customer_app/features/auth/auth_api.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockFlutterSecureStorage extends Mock implements FlutterSecureStorage {}

class FakeRequestOptions extends Fake implements RequestOptions {}

// ---------------------------------------------------------------------------
// JWT fixture with VERIFIED status in payload
// Payload JSON: {"sub":"user-1","verification_status":"VERIFIED"}
// (base64url encoded without signature verification — used for client-side decode only)
// ---------------------------------------------------------------------------
const _fakeVerifiedJwt =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
    '.eyJzdWIiOiJ1c2VyLTEiLCJ2ZXJpZmljYXRpb25fc3RhdHVzIjoiVkVSSUZJRUQifQ'
    '.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';

const _fakeRefreshToken = 'fake-refresh-token-xyz';

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

  test(
      'Test 2: AuthApi.login() POSTs /auth/login; writes accessToken + refreshToken to storage; '
      'AuthNotifier state becomes Verified', () async {
    when(() => mockDio.post<Map<String, dynamic>>(
          '/auth/login',
          data: any(named: 'data'),
        )).thenAnswer((_) async => Response<Map<String, dynamic>>(
          data: {
            'accessToken': _fakeVerifiedJwt,
            'refreshToken': _fakeRefreshToken,
            'status': 'VERIFIED',
          },
          statusCode: 200,
          requestOptions: RequestOptions(path: '/auth/login'),
        ));

    final container = ProviderContainer(
      overrides: [
        secureStorageProvider.overrideWithValue(mockStorage),
      ],
    );
    addTearDown(container.dispose);

    // Wait for auth notifier build
    await container.read(authNotifierProvider.future);
    final authNotifier = container.read(authNotifierProvider.notifier);

    final api = AuthApi(
      dio: mockDio,
      storage: mockStorage,
      authNotifier: authNotifier,
    );

    await api.login(email: 'user@example.com', password: 'secret123');

    // Tokens written to secure storage
    verify(() => mockStorage.write(
          key: kAccessTokenKey,
          value: _fakeVerifiedJwt,
        )).called(greaterThanOrEqualTo(1));

    verify(() => mockStorage.write(
          key: kRefreshTokenKey,
          value: _fakeRefreshToken,
        )).called(greaterThanOrEqualTo(1));

    // Auth state became Verified
    final state = container.read(authNotifierProvider).value;
    expect(state, isA<Verified>(),
        reason: 'AuthState must be Verified after login with VERIFIED status');
  });

  test(
      'Test 3: session restore — tokens pre-seeded in mock storage → '
      'AuthNotifier.build() yields non-Unauthenticated state', () async {
    // Pre-seed tokens in mock storage to simulate a previous login
    when(() => mockStorage.read(key: kAccessTokenKey))
        .thenAnswer((_) async => _fakeVerifiedJwt);
    when(() => mockStorage.read(key: kRefreshTokenKey))
        .thenAnswer((_) async => _fakeRefreshToken);

    final container = ProviderContainer(
      overrides: [
        secureStorageProvider.overrideWithValue(mockStorage),
      ],
    );
    addTearDown(container.dispose);

    // Trigger build — this is what happens on app relaunch
    final state = await container.read(authNotifierProvider.future);

    // Must NOT be unauthenticated — session survives restart
    expect(state, isNot(isA<Unauthenticated>()),
        reason:
            'With pre-seeded tokens, auth state must be Pending or Verified (not Unauthenticated)');
  });

  testWidgets(
      'Test 4: wrong credentials show errorLoginFailed inline below password — NO snackbar',
      (tester) async {
    when(() => mockDio.post<Map<String, dynamic>>(
          '/auth/login',
          data: any(named: 'data'),
        )).thenThrow(DioException(
      type: DioExceptionType.badResponse,
      response: Response(
        statusCode: 401,
        requestOptions: RequestOptions(path: '/auth/login'),
      ),
      requestOptions: RequestOptions(path: '/auth/login'),
    ));

    final router = GoRouter(
      initialLocation: '/login',
      routes: [
        GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
        GoRoute(
            path: '/dashboard',
            builder: (_, __) => const Scaffold(body: Text('Dashboard'))),
        GoRoute(
            path: '/pending',
            builder: (_, __) => const Scaffold(body: Text('Pending'))),
        GoRoute(
            path: '/register',
            builder: (_, __) => const Scaffold(body: Text('Register'))),
      ],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          secureStorageProvider.overrideWithValue(mockStorage),
          authApiProvider.overrideWith((ref) => AuthApi(
                dio: mockDio,
                storage: mockStorage,
                authNotifier: ref.read(authNotifierProvider.notifier),
              )),
        ],
        child: MaterialApp.router(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
        find.byKey(const Key('loginEmailField')), 'bad@example.com');
    await tester.enterText(
        find.byKey(const Key('loginPasswordField')), 'wrongpassword');
    await tester.tap(find.byKey(const Key('loginSubmitButton')));
    await tester.pumpAndSettle();

    // Inline error message must appear
    expect(find.text('Incorrect email or password.'), findsOneWidget,
        reason: 'errorLoginFailed must be shown inline');

    // No SnackBar
    expect(find.byType(SnackBar), findsNothing,
        reason: 'errorLoginFailed must NOT appear as a snackbar');
  });
}
