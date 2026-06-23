import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:customer_app/features/notifications/notification_provider.dart';

/// Material 3 [Badge] over a notification bell icon.
///
/// Shows the unread count from [notificationFeedProvider].
/// The badge is hidden when [unreadCount] is 0.
class NotificationBadge extends ConsumerWidget {
  final VoidCallback? onTap;

  const NotificationBadge({super.key, this.onTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifier = ref.watch(notificationFeedProvider.notifier);
    final unread = notifier.unreadCount;

    return Badge(
      isLabelVisible: unread > 0,
      label: Text('$unread'),
      child: IconButton(
        icon: const Icon(Icons.notifications_outlined),
        tooltip: 'Notifications',
        onPressed: onTap,
      ),
    );
  }
}
