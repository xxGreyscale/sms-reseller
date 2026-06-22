import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/router/app_router.dart';
import 'package:customer_app/l10n/app_localizations.dart';

/// Routes where the bottom NavigationBar must be hidden.
const _hiddenOnRoutes = {
  kSplashRoute,
  kOnboardingRoute,
  kLoginRoute,
  kRegisterRoute,
  kPendingRoute,
  kBundlesPurchaseRoute,
  kContactsAddRoute,
  kCampaignsNewRoute,
};

/// Material 3 NavigationBar with 4 destinations.
/// Renders only when [AuthState] is [Verified] and the current route is not
/// in [_hiddenOnRoutes].
class AppNavigationBar extends ConsumerWidget {
  const AppNavigationBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authAsync = ref.watch(authNotifierProvider);
    final auth = authAsync.value;

    if (auth is! Verified) return const SizedBox.shrink();

    final location = GoRouterState.of(context).matchedLocation;
    if (_hiddenOnRoutes.contains(location)) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context);

    // Determine selected index from location
    final selectedIndex = _indexForRoute(location);

    return NavigationBar(
      selectedIndex: selectedIndex < 0 ? 0 : selectedIndex,
      onDestinationSelected: (index) {
        final route = _routeForIndex(index);
        context.go(route);
      },
      destinations: [
        NavigationDestination(
          icon: const Icon(Icons.dashboard_outlined),
          selectedIcon: const Icon(Icons.dashboard),
          label: l10n.dashboardTitle,
        ),
        NavigationDestination(
          icon: const Icon(Icons.contacts_outlined),
          selectedIcon: const Icon(Icons.contacts),
          label: l10n.contactsTitle,
        ),
        NavigationDestination(
          icon: const Icon(Icons.send_outlined),
          selectedIcon: const Icon(Icons.send),
          label: l10n.campaignsTitle,
        ),
        NavigationDestination(
          icon: const Icon(Icons.notifications_outlined),
          selectedIcon: const Icon(Icons.notifications),
          label: l10n.notificationsTitle,
        ),
      ],
    );
  }

  int _indexForRoute(String location) {
    if (location == kDashboardRoute) return 0;
    if (location.startsWith(kContactsRoute)) return 1;
    if (location.startsWith(kCampaignsRoute)) return 2;
    if (location == kNotificationsRoute) return 3;
    return 0;
  }

  String _routeForIndex(int index) {
    switch (index) {
      case 0:
        return kDashboardRoute;
      case 1:
        return kContactsRoute;
      case 2:
        return kCampaignsRoute;
      case 3:
        return kNotificationsRoute;
      default:
        return kDashboardRoute;
    }
  }
}
