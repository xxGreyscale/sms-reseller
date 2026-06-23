// bundle_card.dart — BundleCard widget per UI-SPEC § Component Inventory
//
// Displays: name (titleMedium) + SMS count (bodyMedium) + price (headlineLarge TZS)
// Recommended bundles use primaryContainer background.
// Tappable — caller provides onBuy callback.
import 'package:flutter/material.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/format/tzs_format.dart';

class BundleCard extends StatelessWidget {
  final String id;
  final String name;
  final int smsCount;
  final int priceTzs;
  final bool recommended;
  final VoidCallback onBuy;

  const BundleCard({
    super.key,
    required this.id,
    required this.name,
    required this.smsCount,
    required this.priceTzs,
    this.recommended = false,
    required this.onBuy,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context)!;
    final backgroundColor = recommended
        ? theme.colorScheme.primaryContainer
        : theme.colorScheme.surface;

    return Card(
      color: backgroundColor,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    name,
                    style: theme.textTheme.titleMedium,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '$smsCount SMS credits',
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    tzs(priceTzs),
                    style: theme.textTheme.headlineLarge,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            FilledButton(
              onPressed: onBuy,
              child: Text(l10n.bundleBuyButton),
            ),
          ],
        ),
      ),
    );
  }
}
