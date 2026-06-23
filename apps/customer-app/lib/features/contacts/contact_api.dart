import 'package:dio/dio.dart';

/// Represents a single contact from the API.
class ContactItem {
  final String id;
  final String name;
  final String phone;

  const ContactItem({
    required this.id,
    required this.name,
    required this.phone,
  });

  factory ContactItem.fromJson(Map<String, dynamic> json) => ContactItem(
        id: json['id'] as String,
        name: json['name'] as String,
        phone: (json['phoneE164'] as String?) ?? (json['phone'] as String),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'phoneE164': phone,
      };
}

/// API methods for the contacts feature.
///
/// All methods accept injectable [dio] for unit testing.
class ContactApi {
  final Dio dio;

  const ContactApi(this.dio);

  /// GET /api/v1/contacts — returns all contacts (flat list, no pagination at MVP).
  Future<List<ContactItem>> listContacts() async {
    final response = await dio.get('/api/v1/contacts');
    final data = response.data;
    List<dynamic> items;
    if (data is Map && data.containsKey('content')) {
      // Paginated response — extract content array.
      items = data['content'] as List<dynamic>;
    } else if (data is List) {
      items = data;
    } else {
      items = [];
    }
    return items
        .map((e) => ContactItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// POST /api/v1/contacts {name, phone} → ContactItem
  Future<ContactItem> createContact({
    required String name,
    required String phone,
  }) async {
    final response = await dio.post(
      '/api/v1/contacts',
      data: {'name': name, 'phone': phone},
    );
    return ContactItem.fromJson(response.data as Map<String, dynamic>);
  }

  /// DELETE /api/v1/contacts/{id} → 204
  Future<void> deleteContact(String id) async {
    await dio.delete('/api/v1/contacts/$id');
  }
}
