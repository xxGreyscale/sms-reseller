// add_contact_test.dart — TDD RED
// Widget tests for AddContactScreen (MOBL-06b).
//
// Tests:
//   1. Valid name+phone → POST; on success pops back
//   2. Offline (Dio connectionError) → errorNetworkWrite ErrorBanner; no contact added to cache
//   3. LoadingOverlay shown during in-flight POST
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/features/contacts/contacts_provider.dart';
import 'package:customer_app/features/contacts/add_contact_screen.dart';
import 'package:customer_app/features/contacts/contact_api.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

class MockDio extends Mock implements Dio {}

class FakeRequestOptions extends Fake implements RequestOptions {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

class FakeContactsNotifier extends ContactsNotifier {
  bool refreshCalled = false;

  @override
  Future<ContactsState> build() async {
    return const ContactsState(contacts: [], isStale: false);
  }

  @override
  Future<void> refresh() async {
    refreshCalled = true;
  }

  @override
  Future<void> deleteContact(String id) async {}
}

Widget buildAddContact({
  required MockDio mockDio,
  required FakeContactsNotifier notifier,
}) {
  final router = GoRouter(
    initialLocation: '/contacts/add',
    routes: [
      GoRoute(
        path: '/contacts',
        builder: (_, __) =>
            const Scaffold(body: Center(child: Text('Contact List'))),
      ),
      GoRoute(
        path: '/contacts/add',
        builder: (_, __) => const AddContactScreen(),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      dioProvider.overrideWithValue(mockDio),
      contactsProvider.overrideWith(() => notifier),
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
  late MockDio mockDio;
  late FakeContactsNotifier notifier;

  setUpAll(() {
    registerFallbackValue(FakeRequestOptions());
  });

  setUp(() {
    mockDio = MockDio();
    notifier = FakeContactsNotifier();
  });

  group('AddContactScreen', () {
    testWidgets(
        'Test 1: valid name+phone → POST succeeds → pops back to contact list',
        (tester) async {
      when(() => mockDio.post(
            '/api/v1/contacts',
            data: any(named: 'data'),
          )).thenAnswer((_) async => Response(
            requestOptions: RequestOptions(path: '/api/v1/contacts'),
            statusCode: 201,
            data: {
              'id': 'new-id',
              'name': 'Test User',
              'phoneE164': '+255712000099',
            },
          ));

      await tester.pumpWidget(
          buildAddContact(mockDio: mockDio, notifier: notifier));
      await tester.pumpAndSettle();

      await tester.enterText(
          find.byKey(const Key('fullNameField')), 'Test User');
      await tester.enterText(
          find.byKey(const Key('phoneField')), '0712000099');

      await tester.tap(find.byKey(const Key('addContactSaveButton')));
      await tester.pumpAndSettle();

      // Navigated back to contact list
      expect(find.text('Contact List'), findsOneWidget);

      // POST was called
      verify(() => mockDio.post(
            '/api/v1/contacts',
            data: any(named: 'data'),
          )).called(1);
    });

    testWidgets(
        'Test 2: offline (connectionError) → ErrorBanner with errorNetworkWrite; no contact in cache',
        (tester) async {
      when(() => mockDio.post(
            '/api/v1/contacts',
            data: any(named: 'data'),
          )).thenThrow(DioException(
        type: DioExceptionType.connectionError,
        requestOptions: RequestOptions(path: '/api/v1/contacts'),
      ));

      await tester.pumpWidget(
          buildAddContact(mockDio: mockDio, notifier: notifier));
      await tester.pumpAndSettle();

      await tester.enterText(
          find.byKey(const Key('fullNameField')), 'Test User');
      await tester.enterText(
          find.byKey(const Key('phoneField')), '0712000099');

      await tester.tap(find.byKey(const Key('addContactSaveButton')));
      await tester.pumpAndSettle();

      // Error banner shown with network write message
      expect(
        find.textContaining('No connection'),
        findsOneWidget,
      );

      // Still on add contact screen (not popped)
      expect(find.byKey(const Key('addContactSaveButton')), findsOneWidget);

      // Contacts provider not refreshed (no silent cache write)
      expect(notifier.refreshCalled, isFalse);
    });

    testWidgets('Test 3: LoadingOverlay shown during in-flight POST',
        (tester) async {
      // Return a future that we can control
      final completer = Completer<Response<dynamic>>();

      when(() => mockDio.post(
            '/api/v1/contacts',
            data: any(named: 'data'),
          )).thenAnswer((_) => completer.future);

      await tester.pumpWidget(
          buildAddContact(mockDio: mockDio, notifier: notifier));
      await tester.pumpAndSettle();

      await tester.enterText(
          find.byKey(const Key('fullNameField')), 'Test User');
      await tester.enterText(
          find.byKey(const Key('phoneField')), '0712000099');

      await tester.tap(find.byKey(const Key('addContactSaveButton')));
      // pump once so the state updates but not settle (awaiting completer)
      await tester.pump();

      // LoadingOverlay or CircularProgressIndicator should be visible
      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      // Complete the future to clean up
      completer.complete(Response(
        requestOptions: RequestOptions(path: '/api/v1/contacts'),
        statusCode: 201,
        data: {'id': 'x', 'name': 'Test User', 'phoneE164': '+255712000099'},
      ));
      await tester.pumpAndSettle();
    });
  });
}
