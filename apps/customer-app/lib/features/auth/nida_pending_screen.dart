import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/features/auth/pending_poller_notifier.dart';
import 'package:customer_app/l10n/app_localizations.dart';

/// NIDA Pending walled screen (MOBL-02, D-09).
///
/// - No AppBar back arrow (Scaffold(automaticallyImplyLeading: false)).
/// - No bottom navigation bar.
/// - Auto-polls GET /auth/me every 10s via PendingPollerNotifier.
/// - On VERIFIED: go_router refreshListenable fires → redirect to /dashboard.
/// - Shows verifiedSuccessMessage SnackBar when state transitions to Verified.
class NidaPendingScreen extends ConsumerStatefulWidget {
  const NidaPendingScreen({super.key});

  @override
  ConsumerState<NidaPendingScreen> createState() => _NidaPendingScreenState();
}

class _NidaPendingScreenState extends ConsumerState<NidaPendingScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();

    // Activate the poller (auto-disposes when screen is removed)
    // ignore: unused_result
    ref.read(pendingPollerNotifierProvider);

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  void _listenForVerified(BuildContext context) {
    final auth = ref.watch(authNotifierProvider).value;
    if (auth is Verified) {
      final l10n = AppLocalizations.of(context);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.verifiedSuccessMessage)),
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    _listenForVerified(context);

    return Scaffold(
      // Walled: no back button
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Pulsing 80dp primaryContainer circle
              AnimatedBuilder(
                animation: _pulseAnimation,
                builder: (context, child) => Transform.scale(
                  scale: _pulseAnimation.value,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: colorScheme.primaryContainer,
                    ),
                    child: Icon(
                      Icons.verified_user_outlined,
                      size: 40,
                      color: colorScheme.onPrimaryContainer,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              // Title
              Text(
                l10n.pendingScreenTitle,
                style: textTheme.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              // Body (80% width, centered)
              SizedBox(
                width: MediaQuery.of(context).size.width * 0.8,
                child: Text(
                  l10n.pendingScreenBody,
                  style: textTheme.bodyMedium,
                  textAlign: TextAlign.center,
                  softWrap: true,
                ),
              ),
              const SizedBox(height: 24),
              // Status row: 14dp indicator + label
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    l10n.pendingStatusLabel,
                    style: textTheme.bodySmall,
                  ),
                ],
              ),
              const SizedBox(height: 32),
              // Logout link (error foreground TextButton)
              TextButton(
                style: TextButton.styleFrom(
                  foregroundColor: colorScheme.error,
                ),
                onPressed: () {
                  ref.read(authNotifierProvider.notifier).signOut();
                },
                child: Text(l10n.pendingLogoutLink),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
