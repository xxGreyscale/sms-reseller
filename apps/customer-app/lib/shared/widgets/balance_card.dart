import 'package:flutter/material.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/format/tzs_format.dart';

/// Large headlineLarge credit count card shown in the Dashboard SliverAppBar.
class BalanceCard extends StatelessWidget {
  final int availableCredits;

  const BalanceCard({
    super.key,
    required this.availableCredits,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      color: colorScheme.primaryContainer,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            credits(availableCredits),
            style: textTheme.headlineLarge?.copyWith(
              color: colorScheme.onPrimaryContainer,
              fontWeight: FontWeight.bold,
            ),
            overflow: TextOverflow.ellipsis,
            softWrap: true,
          ),
          const SizedBox(height: 4),
          Text(
            l10n.dashboardBalanceLabel,
            style: textTheme.labelMedium?.copyWith(
              color: colorScheme.onPrimaryContainer.withValues(alpha: 0.8),
            ),
          ),
        ],
      ),
    );
  }
}
