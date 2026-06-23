// contact_list_test.dart — TDD RED
// Widget tests for ContactListScreen (MOBL-06a).
//
// Tests:
//   1. Offline with pre-seeded Hive contacts → cached contacts + StaleIndicator
//   2. Empty list → contactsEmptyHeading
//   3. Delete taps shows confirmation dialog; confirm calls DELETE and removes row
//   4. Search filters visible contacts
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';

import 'package:customer_app/features/contacts/contacts_provider.dart';
import 'package:customer_app/features/contacts/contact_list_screen.dart';
import 'package:customer_app/l10n/app_localizations.dart';

// ---------------------------------------------------------------------------
// Fakes & helpers
// ---------------------------------------------------------------------------

class FakeContactsNotifier extends ContactsNotifier {
  final List<ContactItem> _initial;
  FakeContactsNotifier(this._initial);

  @override
  Future<ContactsState> build() async {
    return ContactsState(contacts: _initial, isStale: false);
  }

  @override
  Future<void> refresh() async {}

  @override
  Future<void> deleteContact(String id) async {
    final current = state.value!;
    state = AsyncValue.data(ContactsState(
      contacts: current.contacts.where((c) => c.id != id).toList(),
      isStale: current.isStale,
    ));
  }
}

class FakeStaleContactsNotifier extends ContactsNotifier {
  final List<ContactItem> _contacts;
  FakeStaleContactsNotifier(this._contacts);

  @override
  Future<ContactsState> build() async {
    return ContactsState(contacts: _contacts, isStale: true);
  }

  @override
  Future<void> refresh() async {}

  @override
  Future<void> deleteContact(String id) async {}
}

Widget buildContactList({
  required ContactsNotifier Function() createNotifier,
}) {
  final router = GoRouter(
    initialLocation: '/contacts',
    routes: [
      GoRoute(
        path: '/contacts',
        builder: (_, __) => const ContactListScreen(),
      ),
      GoRoute(
        path: '/contacts/add',
        builder: (_, __) =>
            const Scaffold(body: Center(child: Text('Add Contact'))),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      contactsProvider.overrideWith(createNotifier),
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
  group('ContactListScreen', () {
    testWidgets(
        'Test 1: offline with pre-seeded contacts shows contacts + StaleIndicator',
        (tester) async {
      final contacts = [
        ContactItem(id: '1', name: 'Alice Mwamba', phone: '+255712000001'),
        ContactItem(id: '2', name: 'Bob Juma', phone: '+255712000002'),
      ];

      await tester.pumpWidget(buildContactList(
        createNotifier: () => FakeStaleContactsNotifier(contacts),
      ));
      await tester.pumpAndSettle();

      expect(find.text('Alice Mwamba'), findsOneWidget);
      expect(find.text('Bob Juma'), findsOneWidget);
      // StaleIndicator is shown
      expect(find.textContaining('saved data'), findsOneWidget);
    });

    testWidgets('Test 2: empty contact list shows contactsEmptyHeading',
        (tester) async {
      await tester.pumpWidget(buildContactList(
        createNotifier: () => FakeContactsNotifier([]),
      ));
      await tester.pumpAndSettle();

      expect(find.text('No contacts yet'), findsOneWidget);
    });

    testWidgets(
        'Test 3: tapping delete shows dialog; confirm removes contact from list',
        (tester) async {
      final contacts = [
        ContactItem(id: '1', name: 'Alice Mwamba', phone: '+255712000001'),
      ];

      await tester.pumpWidget(buildContactList(
        createNotifier: () => FakeContactsNotifier(contacts),
      ));
      await tester.pumpAndSettle();

      // Tap the delete icon on the tile
      final deleteBtn = find.byIcon(Icons.delete_outline);
      expect(deleteBtn, findsOneWidget);
      await tester.tap(deleteBtn);
      await tester.pumpAndSettle();

      // Confirmation dialog appears
      expect(find.byType(AlertDialog), findsOneWidget);

      // The confirm button has error-colored text — find by key or text "Delete"
      final confirmBtn = find.text('Delete');
      expect(confirmBtn, findsOneWidget);
      await tester.tap(confirmBtn);
      await tester.pumpAndSettle();

      // Contact removed from list
      expect(find.text('Alice Mwamba'), findsNothing);
    });

    testWidgets('Test 4: search filters visible contacts', (tester) async {
      final contacts = [
        ContactItem(id: '1', name: 'Alice Mwamba', phone: '+255712000001'),
        ContactItem(id: '2', name: 'Bob Juma', phone: '+255712000002'),
        ContactItem(id: '3', name: 'Carol Otieno', phone: '+255712000003'),
      ];

      await tester.pumpWidget(buildContactList(
        createNotifier: () => FakeContactsNotifier(contacts),
      ));
      await tester.pumpAndSettle();

      // All contacts visible initially
      expect(find.text('Alice Mwamba'), findsOneWidget);
      expect(find.text('Bob Juma'), findsOneWidget);
      expect(find.text('Carol Otieno'), findsOneWidget);

      // Type in search field
      await tester.enterText(find.byType(TextField).first, 'Bob');
      await tester.pumpAndSettle();

      // Only matching contact visible
      expect(find.text('Bob Juma'), findsOneWidget);
      expect(find.text('Alice Mwamba'), findsNothing);
      expect(find.text('Carol Otieno'), findsNothing);
    });
  });
}
