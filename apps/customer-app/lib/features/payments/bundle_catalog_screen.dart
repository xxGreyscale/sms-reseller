// bundle_catalog_screen.dart — BundleCatalogScreen (MOBL-05a)
//
// Displays active bundles sorted ascending by priceTzs.
// Buy tap → bottom sheet with msisdn + provider SegmentedButton → Confirm → /bundles/purchase
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/features/payments/payment_api.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/bundle_card.dart';

// ---------------------------------------------------------------------------
// Azampay provider list
// ---------------------------------------------------------------------------

const _kProviders = ['M-Pesa', 'Tigo', 'Airtel', 'Halo', 'AzamPesa'];

// ---------------------------------------------------------------------------
// Purchase bottom sheet
// ---------------------------------------------------------------------------

class _PurchaseBottomSheet extends StatefulWidget {
  final Map<String, dynamic> bundle;

  const _PurchaseBottomSheet({required this.bundle});

  @override
  State<_PurchaseBottomSheet> createState() => _PurchaseBottomSheetState();
}

class _PurchaseBottomSheetState extends State<_PurchaseBottomSheet> {
  final _formKey = GlobalKey<FormState>();
  final _msisdnController = TextEditingController();
  String _selectedProvider = _kProviders.first;

  @override
  void dispose() {
    _msisdnController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final bundle = widget.bundle;

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 24,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              bundle['name'] as String,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _msisdnController,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone number (msisdn)',
                hintText: '07XXXXXXXX',
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Enter a phone number' : null,
            ),
            const SizedBox(height: 16),
            // Provider SegmentedButton
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SegmentedButton<String>(
                segments: _kProviders
                    .map((p) => ButtonSegment<String>(value: p, label: Text(p)))
                    .toList(),
                selected: {_selectedProvider},
                onSelectionChanged: (sel) =>
                    setState(() => _selectedProvider = sel.first),
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: () {
                if (_formKey.currentState!.validate()) {
                  Navigator.of(context).pop();
                  context.go(
                    '$kBundlesPurchaseRoute?bundleId=${bundle['id']}'
                    '&bundleName=${Uri.encodeComponent(bundle['name'] as String)}'
                    '&smsCount=${bundle['smsCount']}'
                    '&priceTzs=${bundle['priceTzs']}'
                    '&msisdn=${Uri.encodeComponent(_msisdnController.text.trim())}'
                    '&provider=${Uri.encodeComponent(_selectedProvider)}',
                  );
                }
              },
              child: Text(l10n.purchaseConfirmButton),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// BundleCatalogScreen
// ---------------------------------------------------------------------------

class BundleCatalogScreen extends ConsumerWidget {
  const BundleCatalogScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final bundlesAsync = ref.watch(bundlesProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.bundleCatalogTitle)),
      body: bundlesAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(l10n.errorNetworkLoad)),
        data: (rawBundles) {
          // Sort ascending by priceTzs regardless of provider order
          final bundles = List<Map<String, dynamic>>.from(rawBundles)
            ..sort((a, b) =>
                (a['priceTzs'] as int).compareTo(b['priceTzs'] as int));
          if (bundles.isEmpty) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      l10n.bundlesEmptyHeading,
                      style: Theme.of(context).textTheme.titleMedium,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      l10n.bundlesEmptyBody,
                      style: Theme.of(context).textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: bundles.length,
            itemBuilder: (context, index) {
              final bundle = bundles[index];
              return BundleCard(
                id: bundle['id'] as String,
                name: bundle['name'] as String,
                smsCount: bundle['smsCount'] as int,
                priceTzs: bundle['priceTzs'] as int,
                onBuy: () => showModalBottomSheet(
                  context: context,
                  isScrollControlled: true,
                  builder: (_) => _PurchaseBottomSheet(bundle: bundle),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
