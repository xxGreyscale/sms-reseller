import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/campaign_list_tile.dart';
import 'campaign_provider.dart';

class CampaignHistoryScreen extends ConsumerStatefulWidget {
  const CampaignHistoryScreen({super.key});

  @override
  ConsumerState<CampaignHistoryScreen> createState() =>
      _CampaignHistoryScreenState();
}

class _CampaignHistoryScreenState
    extends ConsumerState<CampaignHistoryScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  /// Infinite scroll: load next page when within 200 dp of bottom.
  void _onScroll() {
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 200) {
      final notifier = ref.read(campaignHistoryProvider.notifier);
      if (notifier.hasMore) {
        notifier.loadMore();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final campaignsAsync = ref.watch(campaignHistoryProvider);

    return Scaffold(
      body: RefreshIndicator(
      onRefresh: () =>
          ref.read(campaignHistoryProvider.notifier).refresh(),
      child: CustomScrollView(
        controller: _scrollController,
        slivers: [
          SliverAppBar(
            title: Text(l10n.campaignsTitle),
            pinned: true,
          ),
          campaignsAsync.when(
            loading: () => const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (e, _) => SliverFillRemaining(
              child: Center(child: Text(l10n.errorNetworkLoad)),
            ),
            data: (campaigns) {
              if (campaigns.isEmpty) {
                return SliverFillRemaining(
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.send, size: 48),
                          const SizedBox(height: 16),
                          Text(
                            l10n.campaignsEmptyHeading,
                            key: const Key('campaignsEmptyHeading'),
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            l10n.campaignsEmptyBody,
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }
              return SliverList(
                delegate: SliverChildBuilderDelegate(
                  (_, i) {
                    final c = campaigns[i];
                    return CampaignListTile(
                      name: c.name,
                      date: c.createdAt ?? '',
                      recipientCount: c.totalRecipients ?? 0,
                      status: c.status,
                      onTap: () => context.go('/campaigns/${c.id}'),
                    );
                  },
                  childCount: campaigns.length,
                ),
              );
            },
          ),
        ],
      ),
    ),
    );
  }
}
