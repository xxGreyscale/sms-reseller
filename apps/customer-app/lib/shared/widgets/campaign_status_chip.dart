import 'package:flutter/material.dart';

/// Color-coded chip for campaign / message delivery status.
///
/// Status → color mapping from UI-SPEC status color system:
///   PENDING  → Yellow-100 bg / Yellow-900 text
///   VERIFIED / SUCCESS → Green-50 bg / Green-800 text
///   FAILED / EXPIRED   → Red-50 bg   / Red-800 text
///   QUEUED / SENT      → Blue-50 bg  / Blue-800 text
class CampaignStatusChip extends StatelessWidget {
  final String status;

  const CampaignStatusChip({super.key, required this.status});

  static const _colors = {
    'PENDING': (bg: Color(0xFFFFF9C4), text: Color(0xFFF57F17)),
    'VERIFIED': (bg: Color(0xFFE8F5E9), text: Color(0xFF2E7D32)),
    'SUCCESS': (bg: Color(0xFFE8F5E9), text: Color(0xFF2E7D32)),
    'FAILED': (bg: Color(0xFFFFEBEE), text: Color(0xFFC62828)),
    'EXPIRED': (bg: Color(0xFFFFEBEE), text: Color(0xFFC62828)),
    'QUEUED': (bg: Color(0xFFE3F2FD), text: Color(0xFF1565C0)),
    'SENT': (bg: Color(0xFFE3F2FD), text: Color(0xFF1565C0)),
    'DELIVERED': (bg: Color(0xFFE8F5E9), text: Color(0xFF2E7D32)),
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final pair =
        _colors[status.toUpperCase()] ??
        (bg: theme.colorScheme.surfaceVariant, text: theme.colorScheme.onSurface);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: pair.bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        status,
        style: theme.textTheme.labelMedium?.copyWith(color: pair.text),
      ),
    );
  }
}
