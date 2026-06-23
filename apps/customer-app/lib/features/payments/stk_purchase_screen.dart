// stk_purchase_screen.dart — STK push purchase + countdown (MOBL-05b)
//
// On entry fires POST /api/v1/payments (via PaymentNotifier.initiate). While
// PENDING it shows the instruction + CountdownWidget seeded from the server's
// timeoutSeconds; the countdown polls status every 5s and expires at 0. On
// CONFIRMED it swaps to a SUCCESS layout (balance already refreshed by the
// notifier); on EXPIRED it shows a Try Again CTA back to the catalog. Back
// navigation is disabled while the countdown is running (no accidental exit).
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/features/payments/payment_api.dart';
import 'package:customer_app/features/payments/payment_provider.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/shared/widgets/countdown_widget.dart';

class StkPurchaseScreen extends ConsumerStatefulWidget {
  final StkPurchaseArgs args;

  const StkPurchaseScreen({super.key, required this.args});

  @override
  ConsumerState<StkPurchaseScreen> createState() => _StkPurchaseScreenState();
}

class _StkPurchaseScreenState extends ConsumerState<StkPurchaseScreen> {
  @override
  void initState() {
    super.initState();
    // Fire STK push after first frame so the notifier override is in place.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(paymentNotifierProvider.notifier).initiate(widget.args);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(paymentNotifierProvider);
    final notifier = ref.read(paymentNotifierProvider.notifier);

    Widget body;
    if (state.isConfirmed) {
      body = _SuccessLayout(smsCount: widget.args.smsCount);
    } else if (state.isExpired) {
      body = const _ExpiredLayout();
    } else if (state.isPending) {
      body = _CountingLayout(
        timeoutSeconds: state.timeoutSeconds,
        onPoll: notifier.pollStatus,
        onExpire: notifier.markExpired,
      );
    } else {
      // idle — initiation in flight
      body = const Center(child: CircularProgressIndicator());
    }

    // Disable back while the payment is still pending.
    return PopScope(
      canPop: !state.isPending,
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.bundleCatalogTitle)),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: body,
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Counting (PENDING) layout
// ---------------------------------------------------------------------------

class _CountingLayout extends StatelessWidget {
  final int timeoutSeconds;
  final VoidCallback onPoll;
  final VoidCallback onExpire;

  const _CountingLayout({
    required this.timeoutSeconds,
    required this.onPoll,
    required this.onExpire,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.stkInstruction,
          style: theme.textTheme.bodyLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 32),
        Center(
          child: CountdownWidget(
            timeoutSeconds: timeoutSeconds,
            onPoll: onPoll,
            onExpire: onExpire,
          ),
        ),
        const SizedBox(height: 16),
        Text(
          l10n.stkCountdownLabel,
          style: theme.textTheme.labelLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.stkWaitingBody,
          style: theme.textTheme.bodyMedium,
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// SUCCESS layout
// ---------------------------------------------------------------------------

class _SuccessLayout extends StatelessWidget {
  final int smsCount;

  const _SuccessLayout({required this.smsCount});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(Icons.check_circle,
            size: 80, color: theme.colorScheme.primary),
        const SizedBox(height: 24),
        Text(
          l10n.stkSuccessTitle,
          style: theme.textTheme.headlineSmall,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.stkSuccessBody(smsCount),
          style: theme.textTheme.bodyMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 32),
        FilledButton(
          onPressed: () => context.go(kDashboardRoute),
          child: Text(l10n.dashboardTitle),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// EXPIRED layout
// ---------------------------------------------------------------------------

class _ExpiredLayout extends StatelessWidget {
  const _ExpiredLayout();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(Icons.timer_off,
            size: 80, color: theme.colorScheme.error),
        const SizedBox(height: 24),
        Text(
          l10n.stkExpiredTitle,
          style: theme.textTheme.headlineSmall,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.stkExpiredBody,
          style: theme.textTheme.bodyMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 32),
        FilledButton(
          onPressed: () => context.go(kBundlesRoute),
          child: Text(l10n.stkTryAgainButton),
        ),
      ],
    );
  }
}
