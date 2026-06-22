import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/dio/dio_client.dart' show dioProvider;
import 'package:customer_app/core/hive/hive_boxes.dart';

/// Fetches up to 5 most recent campaigns (page 0, size 5).
/// Cache-fallback: if Dio throws, returns Hive 'campaigns' box data if available.
///
/// Returns list of campaign maps, each with keys:
///   id, name, createdAt, recipientCount, status
final recentCampaignsProvider =
    FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final dio = ref.read(dioProvider);
  try {
    final response = await dio
        .get('/api/v1/campaigns', queryParameters: {'page': 0, 'size': 5});
    final data = response.data as Map<String, dynamic>;
    final content = (data['content'] as List<dynamic>? ?? [])
        .cast<Map<String, dynamic>>();
    // Write-through to Hive
    final box = campaignsBox;
    for (var i = 0; i < content.length; i++) {
      box.put(i, content[i]);
    }
    return content;
  } catch (_) {
    // Fallback to cache
    final box = campaignsBox;
    if (box.isEmpty) rethrow;
    return box.values
        .take(5)
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
  }
});
