import 'package:flutter/material.dart';
import 'package:customer_app/l10n/app_localizations.dart';

/// Small bodySmall row shown when Hive-cached data is displayed offline.
/// Displays a cache icon + "Showing saved data · Last updated {time}".
class StaleIndicator extends StatelessWidget {
  /// Human-readable time string (e.g., "3 min ago") injected by the caller.
  final String time;

  const StaleIndicator({super.key, required this.time});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textStyle = Theme.of(context).textTheme.bodySmall;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.cached,
          size: 12,
          color: textStyle?.color?.withValues(alpha: 0.7),
        ),
        const SizedBox(width: 4),
        Flexible(
          child: Text(
            l10n.staleDataIndicator(time),
            style: textStyle?.copyWith(
              color: textStyle.color?.withValues(alpha: 0.7),
            ),
            overflow: TextOverflow.ellipsis,
            softWrap: true,
          ),
        ),
      ],
    );
  }
}
