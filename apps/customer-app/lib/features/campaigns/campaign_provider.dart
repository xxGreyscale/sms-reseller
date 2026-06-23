import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/dio/dio_client.dart';
import 'campaign_api.dart';

export 'campaign_api.dart' show CampaignResponse, CampaignApi, MessageStatusRow;

// ---------------------------------------------------------------------------
// Send result type
// ---------------------------------------------------------------------------

sealed class SendResult {}

class SendSuccess extends SendResult {
  final String campaignId;
  SendSuccess(this.campaignId);
}

class SendInsufficientCredits extends SendResult {}

class SendNetworkError extends SendResult {}

// ---------------------------------------------------------------------------
// Campaign send orchestrator
// ---------------------------------------------------------------------------

/// Orchestrates: POST /api/v1/campaigns (contactIds[]) then POST .../send.
/// Returns [SendResult] for the composer to react to.
class CampaignSendNotifier extends AsyncNotifier<void> {
  @override
  Future<void> build() async {}

  Future<SendResult> sendCampaign({
    required String name,
    required String body,
    required List<String> contactIds,
  }) async {
    final dio = ref.read(dioProvider);
    final api = CampaignApi(dio);
    try {
      final campaign = await api.createCampaign(
        name: name,
        body: body,
        contactIds: contactIds,
      );
      try {
        await api.sendCampaign(campaign.id);
        return SendSuccess(campaign.id);
      } on DioException catch (e) {
        final code = _extractCode(e);
        if (code == 'INSUFFICIENT_CREDITS' ||
            e.response?.statusCode == 409 ||
            e.response?.statusCode == 422) {
          return SendInsufficientCredits();
        }
        if (e.type == DioExceptionType.connectionError) {
          return SendNetworkError();
        }
        rethrow;
      }
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionError) {
        return SendNetworkError();
      }
      rethrow;
    }
  }

  String? _extractCode(DioException e) {
    try {
      final data = e.response?.data;
      if (data is Map) return data['code'] as String?;
    } catch (_) {}
    return null;
  }
}

final campaignSendProvider =
    AsyncNotifierProvider<CampaignSendNotifier, void>(
  CampaignSendNotifier.new,
);

// ---------------------------------------------------------------------------
// History provider (paginated list)
// ---------------------------------------------------------------------------

class CampaignHistoryNotifier extends AsyncNotifier<List<CampaignResponse>> {
  int _page = 0;
  bool _hasMore = true;

  @override
  Future<List<CampaignResponse>> build() async {
    _page = 0;
    _hasMore = true;
    return _fetchPage(_page);
  }

  Future<List<CampaignResponse>> _fetchPage(int page) async {
    final dio = ref.read(dioProvider);
    final api = CampaignApi(dio);
    final result = await api.listCampaigns(page: page);
    final content = (result['content'] as List<dynamic>? ?? [])
        .map((e) => CampaignResponse.fromJson(e as Map<String, dynamic>))
        .toList();
    _hasMore = !(result['last'] as bool? ?? true);
    return content;
  }

  bool get hasMore => _hasMore;

  Future<void> loadMore() async {
    if (!_hasMore) return;
    final current = state.value ?? [];
    _page++;
    final next = await _fetchPage(_page);
    state = AsyncValue.data([...current, ...next]);
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    _page = 0;
    _hasMore = true;
    state = await AsyncValue.guard(() => _fetchPage(0));
  }
}

final campaignHistoryProvider =
    AsyncNotifierProvider<CampaignHistoryNotifier, List<CampaignResponse>>(
  CampaignHistoryNotifier.new,
);
