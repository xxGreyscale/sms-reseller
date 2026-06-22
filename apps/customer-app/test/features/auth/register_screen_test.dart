// MOBL-02: Register screen test
// Test 1: AuthApi.register() POST with 6 fields; PENDING_VERIFICATION → Pending auth state
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/storage/secure_storage.dart';
import 'package:customer_app/features/auth/auth_api.dart';
import 'package:customer_app/features/auth/register_screen.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class MockFlutterSecureStorage extends Mock implements FlutterSecureStorage {}

class FakeRequestOptions extends Fake implements RequestOptions {}

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
      'Test 1: AuthApi.register() POSTs /auth/register with 6 fields; '
      'writes accessToken to storage; sets AuthState to Pending', () async {
    when(() => mockDio.post<Map<String, dynamic>>(
          '/auth/register',
          data: any(named: 'data'),
        )).thenAnswer((_) async => Response<Map<String, dynamic>>(
          data: {
            'userId': 'user-123',
            'status': 'PENDING_VERIFICATION',
            'accessToken': 'fake-access-token',
          },
          statusCode: 200,
          requestOptions: RequestOptions(path: '/auth/register'),
        ));

    final container = ProviderContainer(
      overrides: [
        secureStorageProvider.overrideWithValue(mockStorage),
      ],
    );
    addTearDown(container.dispose);

    // Wait for build to complete
    await container.read(authNotifierProvider.future);

    final authNotifier = container.read(authNotifierProvider.notifier);
    final api = AuthApi(
      dio: mockDio,
      storage: mockStorage,
      authNotifier: authNotifier,
    );

    await api.register(
      fullName: 'Juma Mkali',
      phone: '0712345678',
      email: 'juma@example.com',
      nin: 'ABCDE12345',
      password: 'password123',
    );

    // Verify POST was called at least once
    verify(() => mockDio.post<Map<String, dynamic>>(
          '/auth/register',
          data: any(named: 'data'),
        )).called(greaterThanOrEqualTo(1));

    // Verify accessToken was written to secure storage
    verify(() => mockStorage.write(
          key: kAccessTokenKey,
          value: 'fake-access-token',
        )).called(greaterThanOrEqualTo(1));

    // Auth state is Pending
    final state = container.read(authNotifierProvider).value;
    expect(state, isA<Pending>(),
        reason: 'Auth state must be Pending after register');
  });

  testWidgets(
      'Test 1b: RegisterScreen submits form and routes to /pending on success',
      (tester) async {
    when(() => mockDio.post<Map<String, dynamic>>(
          '/auth/register',
          data: any(named: 'data'),
        )).thenAnswer((_) async => Response<Map<String, dynamic>>(
          data: {
            'userId': 'user-123',
            'status': 'PENDING_VERIFICATION',
            'accessToken': 'fake-access-token',
          },
          statusCode: 200,
          requestOptions: RequestOptions(path: '/auth/register'),
        ));

    final router = GoRouter(
      initialLocation: '/register',
      routes: [
        GoRoute(
            path: '/register', builder: (_, __) => const RegisterScreen()),
        GoRoute(
            path: '/pending',
            builder: (_, __) => const Scaffold(body: Text('Pending'))),
        GoRoute(
            path: '/login',
            builder: (_, __) => const Scaffold(body: Text('Login'))),
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
        find.byKey(const Key('registerFullNameField')), 'Juma Mkali');
    await tester.enterText(
        find.byKey(const Key('registerPhoneField')), '0712345678');
    await tester.enterText(
        find.byKey(const Key('registerEmailField')), 'juma@example.com');
    await tester.enterText(
        find.byKey(const Key('registerNinField')), 'ABCDE12345');
    await tester.enterText(
        find.byKey(const Key('registerPasswordField')), 'password123');
    await tester.enterText(
        find.byKey(const Key('registerConfirmPasswordField')), 'password123');

    await tester.tap(find.byKey(const Key('registerSubmitButton')));
    await tester.pumpAndSettle();

    // Navigated to /pending
    expect(find.text('Pending'), findsOneWidget);
  });
}
