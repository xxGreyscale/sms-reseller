import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_ce/hive.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/core/hive/hive_boxes.dart';
import 'contact_api.dart';

export 'contact_api.dart' show ContactItem;

/// State returned by [ContactsNotifier].
class ContactsState {
  final List<ContactItem> contacts;
  final bool isStale;

  const ContactsState({
    required this.contacts,
    required this.isStale,
  });
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/// Notifier for the contacts feature.
///
/// Hive cache-read + online-write pattern (mirrors balance_provider.dart):
/// 1. Fetch GET /api/v1/contacts.
/// 2. Write-through to Hive 'contacts' box on success.
/// 3. On error: return cached contacts from Hive if present (isStale=true).
/// 4. If no cache: rethrow.
class ContactsNotifier extends AsyncNotifier<ContactsState> {
  @override
  Future<ContactsState> build() async {
    return _fetch();
  }

  Future<ContactsState> _fetch() async {
    final dio = ref.read(dioProvider);
    final box = contactsBox;
    try {
      final api = ContactApi(dio);
      final contacts = await api.listContacts();
      // Write-through cache: persist each contact under its id.
      await _writeCache(box, contacts);
      return ContactsState(contacts: contacts, isStale: false);
    } on DioException {
      final cached = _readCache(box);
      if (cached.isNotEmpty) {
        return ContactsState(contacts: cached, isStale: true);
      }
      rethrow;
    }
  }

  /// Pull-to-refresh: invalidates state, re-fetches.
  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_fetch);
  }

  /// Delete a contact via the API, then remove from cache and state.
  Future<void> deleteContact(String id) async {
    final dio = ref.read(dioProvider);
    final api = ContactApi(dio);
    await api.deleteContact(id);
    final box = contactsBox;
    await box.delete(id);
    // Update in-memory state immediately.
    final current = state.value;
    if (current != null) {
      state = AsyncValue.data(ContactsState(
        contacts: current.contacts.where((c) => c.id != id).toList(),
        isStale: current.isStale,
      ));
    }
  }

  // ---------------------------------------------------------------------------
  // Hive helpers
  // ---------------------------------------------------------------------------

  Future<void> _writeCache(Box<Map> box, List<ContactItem> contacts) async {
    await box.clear();
    for (final c in contacts) {
      await box.put(c.id, c.toJson());
    }
  }

  List<ContactItem> _readCache(Box<Map> box) {
    return box.values
        .map((m) => ContactItem.fromJson(Map<String, dynamic>.from(m)))
        .toList();
  }
}

final contactsProvider =
    AsyncNotifierProvider<ContactsNotifier, ContactsState>(
  ContactsNotifier.new,
);
