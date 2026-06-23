import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/features/dashboard/balance_provider.dart';
import 'package:customer_app/features/dashboard/recent_campaigns_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/balance_card.dart';
import 'package:customer_app/shared/widgets/error_banner.dart';
import 'package:customer_app/shared/widgets/notification_badge.dart';
import 'package:customer_app/shared/widgets/stale_indicator.dart';

/// Dashboard screen (MOBL-04).
///
/// CustomScrollView with:
///   - SliverAppBar (BalanceCard in flexibleSpace, bell + lang toggle actions)
///   - StaleIndicator when balance comes from cache
///   - SliverList: "Recent Campaigns" header + ≤5 CampaignListTiles or empty state
///   - FAB.extended → /campaigns/new
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final balanceAsync = ref.watch(balanceProvider);
    final campaignsAsync = ref.watch(recentCampaignsProvider);

    final isStale = balanceAsync.value?.isStale ?? false;
    final lastUpdated = balanceAsync.hasValue ? DateTime.now() : null;

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(balanceProvider);
          ref.invalidate(recentCampaignsProvider);
        },
        child: CustomScrollView(
          slivers: [
            // ----------------------------------------------------------------
            // SliverAppBar with BalanceCard
            // ----------------------------------------------------------------
            SliverAppBar(
              pinned: true,
              expandedHeight: 160,
              automaticallyImplyLeading: false,
              actions: [
                NotificationBadge(
                  onTap: () => context.go(kNotificationsRoute),
                ),
                const SizedBox(width: 8),
              ],
              flexibleSpace: FlexibleSpaceBar(
                collapseMode: CollapseMode.pin,
                background: balanceAsync.when(
                  data: (result) => BalanceCard(availableCredits: result.credits),
                  loading: () => const Center(
                    child: CircularProgressIndicator(),
                  ),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ),
            ),

            // ----------------------------------------------------------------
            // StaleIndicator (shown when cached data displayed)
            // ----------------------------------------------------------------
            if (isStale)
              SliverToBoxAdapter(
                child: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: StaleIndicator(
                    time: lastUpdated != null
                        ? _formatRelativeTime(lastUpdated)
                        : 'unknown',
                  ),
                ),
              ),

            // ----------------------------------------------------------------
            // Error banner (network error with no cache)
            // ----------------------------------------------------------------
            if (balanceAsync.hasError && !balanceAsync.hasValue)
              SliverToBoxAdapter(
                child: ErrorBanner(
                  message: l10n.errorNetworkLoad,
                  onRetry: () => ref.invalidate(balanceProvider),
                ),
              ),

            // ----------------------------------------------------------------
            // Recent Campaigns section
            // ----------------------------------------------------------------
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
                child: Text(
                  l10n.dashboardRecentCampaigns,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ),

            campaignsAsync.when(
              data: (campaigns) {
                if (campaigns.isEmpty) {
                  return SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Center(
                        child: Text(
                          l10n.campaignsEmptyHeading,
                          style: Theme.of(context).textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                  );
                }

                final visibleCampaigns = campaigns.take(5).toList();
                return SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      if (index == visibleCampaigns.length) {
                        return TextButton(
                          onPressed: () => context.go(kCampaignsRoute),
                          child: Text(l10n.dashboardViewAll),
                        );
                      }
                      final campaign = visibleCampaigns[index];
                      return _CampaignListTile(campaign: campaign);
                    },
                    childCount: visibleCampaigns.length +
                        (campaigns.length > 5 ? 1 : 0),
                  ),
                );
              },
              loading: () => const SliverToBoxAdapter(
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, __) => SliverToBoxAdapter(
                child: ErrorBanner(
                  message: l10n.errorNetworkLoad,
                  onRetry: () => ref.invalidate(recentCampaignsProvider),
                ),
              ),
            ),

            // Bottom padding for FAB
            const SliverToBoxAdapter(child: SizedBox(height: 80)),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go(kCampaignsNewRoute),
        label: Text(l10n.dashboardQuickSendFab),
        icon: const Icon(Icons.send),
      ),
    );
  }

  String _formatRelativeTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes} min ago';
    return DateFormat.jm().format(dt);
  }
}

// ---------------------------------------------------------------------------
// CampaignListTile — inline widget for recent campaigns
// ---------------------------------------------------------------------------

class _CampaignListTile extends StatelessWidget {
  final Map<String, dynamic> campaign;

  const _CampaignListTile({required this.campaign});

  @override
  Widget build(BuildContext context) {
    final status = campaign['status'] as String? ?? '';
    final name = campaign['name'] as String? ?? '';
    final recipientCount = campaign['recipientCount'] as int? ?? 0;

    return ListTile(
      title: Text(
        name,
        style: Theme.of(context).textTheme.bodyMedium,
        overflow: TextOverflow.ellipsis,
        softWrap: true,
      ),
      trailing: _StatusChip(status: status),
      subtitle: Text(
        '$recipientCount recipients',
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String status;
  const _StatusChip({required this.status});

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = _colors(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        status,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(color: fg),
      ),
    );
  }

  (Color, Color) _colors(String status) {
    switch (status) {
      case 'SENT':
      case 'QUEUED':
        return (const Color(0xFFE3F2FD), const Color(0xFF1565C0));
      case 'FAILED':
        return (const Color(0xFFFFEBEE), const Color(0xFFC62828));
      case 'PENDING':
        return (const Color(0xFFFFF9C4), const Color(0xFFF57F17));
      default:
        return (const Color(0xFFE8F5E9), const Color(0xFF2E7D32));
    }
  }
}
