import 'package:dio/dio.dart';

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

class CampaignResponse {
  final String id;
  final String name;
  final String status;
  final String? body;
  final int? totalRecipients;
  final int? deliveredCount;
  final int? failedCount;
  final String? createdAt;

  const CampaignResponse({
    required this.id,
    required this.name,
    required this.status,
    this.body,
    this.totalRecipients,
    this.deliveredCount,
    this.failedCount,
    this.createdAt,
  });

  factory CampaignResponse.fromJson(Map<String, dynamic> json) =>
      CampaignResponse(
        id: json['id'] as String,
        name: json['name'] as String? ?? '',
        status: json['status'] as String? ?? 'QUEUED',
        body: json['body'] as String?,
        totalRecipients: json['totalRecipients'] as int?,
        deliveredCount: json['deliveredCount'] as int?,
        failedCount: json['failedCount'] as int?,
        createdAt: json['createdAt'] as String?,
      );
}

class MessageStatusRow {
  final String recipient;
  final String status;

  const MessageStatusRow({required this.recipient, required this.status});

  factory MessageStatusRow.fromJson(Map<String, dynamic> json) =>
      MessageStatusRow(
        recipient: json['recipient'] as String? ?? '',
        status: json['status'] as String? ?? '',
      );
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

class CampaignApi {
  final Dio dio;

  const CampaignApi(this.dio);

  /// POST /api/v1/campaigns {name, senderId, body, contactIds[]}
  /// Returns {id, status}
  Future<CampaignResponse> createCampaign({
    required String name,
    required String body,
    required List<String> contactIds,
    String? senderId,
  }) async {
    final data = <String, dynamic>{
      'name': name,
      'body': body,
      'contactIds': contactIds,
      // NOTE: never pass groupIds — see D-12 / Pitfall 5
      if (senderId != null) 'senderId': senderId,
    };
    final response = await dio.post('/api/v1/campaigns', data: data);
    return CampaignResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// POST /api/v1/campaigns/{id}/send
  Future<Map<String, dynamic>> sendCampaign(String campaignId) async {
    final response = await dio.post('/api/v1/campaigns/$campaignId/send');
    return response.data as Map<String, dynamic>;
  }

  /// GET /api/v1/campaigns?page&size → Page<CampaignResponse>
  Future<Map<String, dynamic>> listCampaigns({
    int page = 0,
    int size = 20,
  }) async {
    final response = await dio.get(
      '/api/v1/campaigns',
      queryParameters: {'page': page, 'size': size},
    );
    return response.data as Map<String, dynamic>;
  }

  /// GET /api/v1/campaigns/{id}
  Future<CampaignResponse> getCampaign(String id) async {
    final response = await dio.get('/api/v1/campaigns/$id');
    return CampaignResponse.fromJson(response.data as Map<String, dynamic>);
  }

  /// GET /api/v1/campaigns/{id}/messages → Page<{recipient, status}>
  Future<Map<String, dynamic>> getMessages(String id, {
    int page = 0,
    int size = 50,
  }) async {
    final response = await dio.get(
      '/api/v1/campaigns/$id/messages',
      queryParameters: {'page': page, 'size': size},
    );
    return response.data as Map<String, dynamic>;
  }
}
