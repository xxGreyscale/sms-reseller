import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:customer_app/core/dio/dio_client.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/campaign_status_chip.dart';
import 'campaign_api.dart';

// ---------------------------------------------------------------------------
// Production screen — fetches data from API
// ---------------------------------------------------------------------------

class DetailScreen extends ConsumerStatefulWidget {
  final String campaignId;

  const DetailScreen({super.key, required this.campaignId});

  @override
  ConsumerState<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends ConsumerState<DetailScreen> {
  late Future<_DetailData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_DetailData> _load() async {
    final dio = ref.read(dioProvider);
    final api = CampaignApi(dio);
    final campaign = await api.getCampaign(widget.campaignId);
    final messagesPage = await api.getMessages(widget.campaignId);
    final messages = (messagesPage['content'] as List<dynamic>? ?? [])
        .map((e) => MessageStatusRow.fromJson(e as Map<String, dynamic>))
        .toList();
    return _DetailData(campaign: campaign, messages: messages);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_DetailData>(
      future: _future,
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }
        if (snap.hasError) {
          return Scaffold(
            body: Center(
                child: Text(AppLocalizations.of(context)!.errorNetworkLoad)),
          );
        }
        return _DetailView(data: snap.data!);
      },
    );
  }
}

// ---------------------------------------------------------------------------
// Testable variant — accepts pre-loaded data (for widget tests)
// ---------------------------------------------------------------------------

/// Widget-test-friendly variant that accepts pre-loaded JSON data.
class DetailScreenTestable extends StatelessWidget {
  final String campaignId;
  final Map<String, dynamic> campaignJson;
  final List<Map<String, dynamic>> messages;

  const DetailScreenTestable({
    super.key,
    required this.campaignId,
    required this.campaignJson,
    required this.messages,
  });

  @override
  Widget build(BuildContext context) {
    final campaign = CampaignResponse.fromJson(campaignJson);
    final rows =
        messages.map((m) => MessageStatusRow.fromJson(m)).toList();
    return _DetailView(
        data: _DetailData(campaign: campaign, messages: rows));
  }
}

// ---------------------------------------------------------------------------
// Shared view
// ---------------------------------------------------------------------------

class _DetailData {
  final CampaignResponse campaign;
  final List<MessageStatusRow> messages;
  const _DetailData({required this.campaign, required this.messages});
}

class _DetailView extends StatelessWidget {
  final _DetailData data;
  const _DetailView({super.key, required this.data});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final c = data.campaign;
    final msgs = data.messages;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          c.name,
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Aggregate stat tiles
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _StatTile(
                          label: 'Total',
                          value: '${c.totalRecipients ?? 0}'),
                      _StatTile(
                          label: 'Delivered',
                          value: '${c.deliveredCount ?? 0}'),
                      _StatTile(
                          label: 'Failed',
                          value: '${c.failedCount ?? 0}'),
                    ],
                  ),
                  const SizedBox(height: 12),
                  CampaignStatusChip(
                    key: const Key('campaignStatusChip'),
                    status: c.status,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Message delivery status header
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Text(
              'Message Delivery Status',
              style: theme.textTheme.titleMedium,
            ),
          ),

          // Per-message rows
          ...msgs.map((m) {
            final isFailed =
                m.status.toUpperCase() == 'FAILED';
            return Container(
              key: isFailed ? const Key('failedMessageRow') : null,
              color: isFailed
                  ? const Color(0xFFFFEBEE) // Red-50 tint
                  : null,
              child: ListTile(
                title: Text(m.recipient,
                    style: theme.textTheme.bodyMedium),
                trailing: CampaignStatusChip(status: m.status),
              ),
            );
          }),
        ],
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  final String label;
  final String value;
  const _StatTile({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(value, style: theme.textTheme.headlineLarge),
        Text(label, style: theme.textTheme.labelMedium),
      ],
    );
  }
}
