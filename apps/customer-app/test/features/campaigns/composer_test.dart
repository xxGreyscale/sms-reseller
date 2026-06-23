// Task 2 RED: Campaign composer (contactIds send, MOBL-07)
//
// Behaviors:
// 1. Composer disables send until ≥1 contact selected AND message non-empty
// 2. Send POSTs /api/v1/campaigns with contactIds[] then /send; success navigates to detail
// 3. insufficient-credits response → ErrorBanner with Buy Credits action to /bundles
// 4. network error on send → Try Again banner; no navigation

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/features/campaigns/composer_screen.dart';
import 'package:customer_app/features/contacts/contacts_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class FakeRequestOptions extends Fake implements RequestOptions {}

class FakeContactsNotifier extends ContactsNotifier {
  final List<ContactItem> _contacts;
  FakeContactsNotifier(this._contacts);

  @override
  Future<ContactsState> build() async =>
      ContactsState(contacts: _contacts, isStale: false);

  @override
  Future<void> refresh() async {}

  @override
  Future<void> deleteContact(String id) async {}
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

final _contacts = [
  const ContactItem(id: 'c1', name: 'Alice', phone: '+255700000001'),
  const ContactItem(id: 'c2', name: 'Bob', phone: '+255700000002'),
];

String? _lastPushedRoute;

Widget buildComposer({required Dio dio}) {
  _lastPushedRoute = null;
  final router = GoRouter(
    initialLocation: '/campaigns/new',
    routes: [
      GoRoute(
        path: '/campaigns/new',
        builder: (_, __) => const ComposerScreen(),
      ),
      GoRoute(
        path: '/campaigns/:id',
        builder: (_, state) {
          _lastPushedRoute = '/campaigns/${state.pathParameters['id']}';
          return const Scaffold(body: Text('Detail'));
        },
      ),
      GoRoute(
        path: '/bundles',
        builder: (_, __) {
          _lastPushedRoute = '/bundles';
          return const Scaffold(body: Text('Bundles'));
        },
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      dioProvider.overrideWithValue(dio),
      contactsProvider.overrideWith(() => FakeContactsNotifier(_contacts)),
    ],
    child: MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}

void main() {
  setUpAll(() {
    registerFallbackValue(FakeRequestOptions());
  });

  // ---------------------------------------------------------------------------
  // Test 1: send disabled until ≥1 contact AND non-empty message
  // ---------------------------------------------------------------------------

  testWidgets('Test 1: send button disabled until contacts + message filled',
      (tester) async {
    final dio = MockDio();
    await tester.pumpWidget(buildComposer(dio: dio));
    await tester.pumpAndSettle();

    // Initially disabled (no contacts, no message)
    final btn = find.byKey(const Key('composerSendButton'));
    expect(tester.widget<FilledButton>(btn).onPressed, isNull);

    // Type a message — still disabled (no contacts selected)
    await tester.enterText(find.byKey(const Key('composerMessageField')), 'Hello world');
    await tester.pump();
    expect(tester.widget<FilledButton>(btn).onPressed, isNull);

    // Select a contact via bottom sheet
    await tester.tap(find.byKey(const Key('composerRecipientButton')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Alice'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('composerPickerDone')));
    await tester.pumpAndSettle();

    // Now both filled → enabled
    expect(tester.widget<FilledButton>(btn).onPressed, isNotNull);
  });

  // ---------------------------------------------------------------------------
  // Test 2: success path — POST /campaigns (contactIds[]) + /send → detail
  // ---------------------------------------------------------------------------

  testWidgets('Test 2: send posts contactIds[] then /send, navigates to detail',
      (tester) async {
    final dio = MockDio();

    // Mock POST /campaigns → returns id
    when(() => dio.post(
          '/api/v1/campaigns',
          data: any(named: 'data'),
        )).thenAnswer((_) async => Response(
          requestOptions: RequestOptions(path: '/api/v1/campaigns'),
          statusCode: 201,
          data: {'id': 'camp-1', 'status': 'QUEUED'},
        ));

    // Mock POST /campaigns/camp-1/send
    when(() => dio.post('/api/v1/campaigns/camp-1/send')).thenAnswer(
        (_) async => Response(
              requestOptions:
                  RequestOptions(path: '/api/v1/campaigns/camp-1/send'),
              statusCode: 200,
              data: {
                'campaignId': 'camp-1',
                'recipientCount': 1,
                'creditsReserved': 1,
              },
            ));

    await tester.pumpWidget(buildComposer(dio: dio));
    await tester.pumpAndSettle();

    // Select Alice
    await tester.tap(find.byKey(const Key('composerRecipientButton')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Alice'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('composerPickerDone')));
    await tester.pumpAndSettle();

    // Type message
    await tester.enterText(find.byKey(const Key('composerMessageField')), 'Hello!');
    await tester.pump();

    // Send
    await tester.tap(find.byKey(const Key('composerSendButton')));
    await tester.pumpAndSettle();

    // Verify create was called with contactIds[]
    final captured = verify(() => dio.post(
          '/api/v1/campaigns',
          data: captureAny(named: 'data'),
        )).captured;
    final payload = captured.first as Map<String, dynamic>;
    expect(payload.containsKey('contactIds'), isTrue);
    expect(payload['contactIds'], contains('c1'));
    expect(payload.containsKey('groupIds'), isFalse);

    // Navigated to detail
    expect(_lastPushedRoute, '/campaigns/camp-1');
  });

  // ---------------------------------------------------------------------------
  // Test 3: insufficient credits → ErrorBanner with Buy Credits
  // ---------------------------------------------------------------------------

  testWidgets('Test 3: insufficient credits → ErrorBanner + Buy Credits',
      (tester) async {
    final dio = MockDio();

    when(() => dio.post(
          '/api/v1/campaigns',
          data: any(named: 'data'),
        )).thenAnswer((_) async => Response(
          requestOptions: RequestOptions(path: '/api/v1/campaigns'),
          statusCode: 201,
          data: {'id': 'camp-2', 'status': 'QUEUED'},
        ));

    // /send returns 409/422 insufficient credits
    when(() => dio.post('/api/v1/campaigns/camp-2/send')).thenThrow(
      DioException(
        requestOptions: RequestOptions(path: '/api/v1/campaigns/camp-2/send'),
        response: Response(
          requestOptions:
              RequestOptions(path: '/api/v1/campaigns/camp-2/send'),
          statusCode: 422,
          data: {'code': 'INSUFFICIENT_CREDITS'},
        ),
        type: DioExceptionType.badResponse,
      ),
    );

    await tester.pumpWidget(buildComposer(dio: dio));
    await tester.pumpAndSettle();

    // Select contact + message
    await tester.tap(find.byKey(const Key('composerRecipientButton')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Alice'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('composerPickerDone')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('composerMessageField')), 'Hello!');
    await tester.pump();

    await tester.tap(find.byKey(const Key('composerSendButton')));
    await tester.pumpAndSettle();

    // ErrorBanner with errorInsufficientCredits text should appear
    expect(find.byKey(const Key('composerErrorBanner')), findsOneWidget);
    // Buy Credits button present
    expect(find.byKey(const Key('composerBuyCreditsButton')), findsOneWidget);
  });

  // ---------------------------------------------------------------------------
  // Test 4: network error → Try Again banner; no navigation
  // ---------------------------------------------------------------------------

  testWidgets('Test 4: network error → Try Again banner, no navigation',
      (tester) async {
    final dio = MockDio();

    when(() => dio.post(
          '/api/v1/campaigns',
          data: any(named: 'data'),
        )).thenThrow(DioException(
      requestOptions: RequestOptions(path: '/api/v1/campaigns'),
      type: DioExceptionType.connectionError,
    ));

    await tester.pumpWidget(buildComposer(dio: dio));
    await tester.pumpAndSettle();

    // Select contact + message
    await tester.tap(find.byKey(const Key('composerRecipientButton')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Alice'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('composerPickerDone')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('composerMessageField')), 'Hello!');
    await tester.pump();

    await tester.tap(find.byKey(const Key('composerSendButton')));
    await tester.pumpAndSettle();

    // Error banner with Try Again
    expect(find.byKey(const Key('composerErrorBanner')), findsOneWidget);
    // No navigation
    expect(_lastPushedRoute, isNull);
  });
}
