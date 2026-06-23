import 'package:dio/dio.dart';

/// A single notification item from GET /api/v1/notifications.
class NotificationItem {
  final String id;
  final String type;
  final String message;
  final bool read;
  final DateTime createdAt;

  const NotificationItem({
    required this.id,
    required this.type,
    required this.message,
    required this.read,
    required this.createdAt,
  });

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    return NotificationItem(
      id: json['id'] as String,
      type: json['type'] as String,
      message: json['message'] as String,
      read: json['read'] as bool,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  /// Returns a copy with [read] set to true.
  NotificationItem markRead() => NotificationItem(
        id: id,
        type: type,
        message: message,
        read: true,
        createdAt: createdAt,
      );
}

/// Fetches page 0, size 20 of the notification feed.
Future<List<NotificationItem>> fetchNotifications(Dio dio) async {
  final response = await dio.get(
    '/api/v1/notifications',
    queryParameters: {'page': 0, 'size': 20},
  );
  final data = response.data as Map<String, dynamic>;
  final content = data['content'] as List<dynamic>;
  return content
      .map((e) => NotificationItem.fromJson(e as Map<String, dynamic>))
      .toList();
}
