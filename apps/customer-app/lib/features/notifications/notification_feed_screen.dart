import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:customer_app/features/notifications/notification_api.dart';
import 'package:customer_app/features/notifications/notification_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';

/// Notification feed screen (/notifications).
///
/// - SliverAppBar + ListView of ListTiles.
/// - Unread items: tileColor = colorScheme.primaryContainer.
/// - Read items: white/surface background.
/// - On open: marks all visible items read locally (client-side, D-14).
/// - Empty state: notificationsEmptyHeading.
/// - Type → icon mapping per UI-SPEC.
class NotificationFeedScreen extends ConsumerStatefulWidget {
  const NotificationFeedScreen({super.key});

  @override
  ConsumerState<NotificationFeedScreen> createState() =>
      _NotificationFeedScreenState();
}

class _NotificationFeedScreenState
    extends ConsumerState<NotificationFeedScreen> {
  @override
  void initState() {
    super.initState();
    // Mark all visible items read on screen open (client-side, D-14).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(notificationFeedProvider.notifier).markAllRead();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final items = ref.watch(notificationFeedProvider);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            pinned: true,
            title: Text(l10n.notificationsTitle),
            automaticallyImplyLeading: true,
          ),
          if (items.isEmpty)
            SliverFillRemaining(
              child: Center(
                child: Text(
                  l10n.notificationsEmptyHeading,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            )
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final item = items[index];
                  return _NotificationTile(item: item);
                },
                childCount: items.length,
              ),
            ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Notification tile
// ---------------------------------------------------------------------------

class _NotificationTile extends StatelessWidget {
  final NotificationItem item;

  const _NotificationTile({required this.item});

  @override
  Widget build(BuildContext context) {
    final (icon, iconColor) = _iconForType(item.type);
    final tileColor = item.read
        ? Theme.of(context).colorScheme.surface
        : Theme.of(context).colorScheme.primaryContainer;

    return ListTile(
      tileColor: tileColor,
      leading: Icon(icon, color: iconColor),
      title: Text(
        item.message,
        style: Theme.of(context).textTheme.bodyMedium,
        softWrap: true,
        overflow: TextOverflow.ellipsis,
        maxLines: 2,
      ),
      subtitle: Text(
        _formatTimestamp(item.createdAt),
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }

  /// Maps notification type to (icon, color) per UI-SPEC.
  (IconData, Color) _iconForType(String type) {
    switch (type) {
      case 'NIDA_VERIFIED':
        return (Icons.verified_user, const Color(0xFF2E7D32)); // green
      case 'PAYMENT_CONFIRMED':
        return (Icons.payment, const Color(0xFF1565C0)); // blue
      case 'LOW_CREDIT':
        return (Icons.warning, const Color(0xFFF57F17)); // amber
      case 'CREDIT_EXPIRY':
        return (Icons.timer, const Color(0xFFF57F17)); // amber
      case 'CAMPAIGN_COMPLETED':
        return (Icons.send, const Color(0xFF1565C0)); // blue
      case 'SENDER_ID_DECISION':
        return (Icons.badge, const Color(0xFF1565C0)); // blue (approved default)
      default:
        return (Icons.notifications, const Color(0xFF1565C0));
    }
  }

  String _formatTimestamp(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return DateFormat.yMMMd().format(dt);
  }
}
