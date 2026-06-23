import 'package:flutter/material.dart';
import 'campaign_status_chip.dart';

/// ListTile for a campaign in the campaign history list.
///
/// Shows: name + date + recipient count + [CampaignStatusChip].
class CampaignListTile extends StatelessWidget {
  final String name;
  final String date;
  final int recipientCount;
  final String status;
  final VoidCallback? onTap;

  const CampaignListTile({
    super.key,
    required this.name,
    required this.date,
    required this.recipientCount,
    required this.status,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      tileColor: theme.colorScheme.surfaceVariant,
      title: Text(
        name,
        style: theme.textTheme.titleMedium,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '$date · $recipientCount recipients',
        style: theme.textTheme.bodySmall,
      ),
      trailing: CampaignStatusChip(status: status),
      onTap: onTap,
    );
  }
}
