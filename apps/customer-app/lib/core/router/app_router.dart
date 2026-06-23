import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/core/auth/auth_notifier.dart';
import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/router/go_router_refresh_stream.dart';
import 'package:customer_app/features/onboarding/splash_screen.dart';
import 'package:customer_app/features/onboarding/onboarding_screen.dart';
import 'package:customer_app/features/auth/register_screen.dart';
import 'package:customer_app/features/auth/login_screen.dart';
import 'package:customer_app/features/auth/nida_pending_screen.dart';
import 'package:customer_app/features/dashboard/dashboard_screen.dart';
import 'package:customer_app/features/payments/bundle_catalog_screen.dart';
import 'package:customer_app/features/payments/payment_api.dart';
import 'package:customer_app/features/payments/stk_purchase_screen.dart';
import 'package:customer_app/shared/widgets/app_navigation_bar.dart';

// ---------------------------------------------------------------------------
// Placeholder screens (to be replaced in later waves)
// ---------------------------------------------------------------------------

class _PlaceholderScreen extends StatelessWidget {
  final String name;
  const _PlaceholderScreen(this.name);

  @override
  Widget build(BuildContext context) =>
      Scaffold(body: Center(child: Text(name)));
}

// ---------------------------------------------------------------------------
// Route constants
// ---------------------------------------------------------------------------

const kSplashRoute = '/splash';
const kOnboardingRoute = '/onboarding';
const kLoginRoute = '/login';
const kRegisterRoute = '/register';
const kPendingRoute = '/pending';
const kDashboardRoute = '/dashboard';
const kBundlesRoute = '/bundles';
const kBundlesPurchaseRoute = '/bundles/purchase';
const kContactsRoute = '/contacts';
const kContactsAddRoute = '/contacts/add';
const kCampaignsNewRoute = '/campaigns/new';
const kCampaignsRoute = '/campaigns';
const kNotificationsRoute = '/notifications';

// ---------------------------------------------------------------------------
// Public routes (accessible without authentication)
// ---------------------------------------------------------------------------

const _publicRoutes = [
  kSplashRoute,
  kOnboardingRoute,
  kLoginRoute,
  kRegisterRoute,
];

// ---------------------------------------------------------------------------
// NavigationBar shell — wraps authenticated routes
// ---------------------------------------------------------------------------

class _ShellPage extends StatelessWidget {
  final Widget child;
  const _ShellPage({required this.child});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: const AppNavigationBar(),
    );
  }
}

// ---------------------------------------------------------------------------
// Router provider
// ---------------------------------------------------------------------------

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: kSplashRoute,
    refreshListenable: GoRouterRefreshNotifier(ref),
    redirect: (BuildContext context, GoRouterState state) {
      final authAsync = ref.read(authNotifierProvider);
      final auth = authAsync.value;

      // Still loading auth state — stay put
      if (auth == null) return null;

      final location = state.matchedLocation;

      // Rule 1: unauthenticated + not on a public route → /login
      if (auth is Unauthenticated && !_publicRoutes.contains(location)) {
        return kLoginRoute;
      }

      // Rule 2: pending + not on /pending → /pending
      if (auth is Pending && location != kPendingRoute) {
        return kPendingRoute;
      }

      // Rule 3: verified + on an auth-only route → /dashboard
      if (auth is Verified &&
          (location == kLoginRoute ||
              location == kRegisterRoute ||
              location == kOnboardingRoute)) {
        return kDashboardRoute;
      }

      return null;
    },
    routes: [
      GoRoute(
        path: kSplashRoute,
        builder: (_, __) => const SplashScreen(),
      ),
      GoRoute(
        path: kOnboardingRoute,
        builder: (_, __) => const OnboardingScreen(),
      ),
      GoRoute(
        path: kLoginRoute,
        builder: (_, __) => const LoginScreen(),
      ),
      GoRoute(
        path: kRegisterRoute,
        builder: (_, __) => const RegisterScreen(),
      ),
      GoRoute(
        path: kPendingRoute,
        builder: (_, __) => const NidaPendingScreen(),
      ),
      // Shell routes — NavigationBar visible for Verified users
      ShellRoute(
        builder: (_, __, child) => _ShellPage(child: child),
        routes: [
          GoRoute(
            path: kDashboardRoute,
            builder: (_, __) => const DashboardScreen(),
          ),
          GoRoute(
            path: kBundlesRoute,
            builder: (_, __) => const BundleCatalogScreen(),
          ),
          GoRoute(
            path: kContactsRoute,
            builder: (_, __) => const _PlaceholderScreen('Contacts'),
          ),
          GoRoute(
            path: kCampaignsRoute,
            builder: (_, __) => const _PlaceholderScreen('Campaigns'),
          ),
          GoRoute(
            path: '/campaigns/:id',
            builder: (_, state) =>
                _PlaceholderScreen('Campaign ${state.pathParameters['id']}'),
          ),
          GoRoute(
            path: kNotificationsRoute,
            builder: (_, __) => const _PlaceholderScreen('Notifications'),
          ),
        ],
      ),
      // Routes that explicitly hide the NavigationBar (no shell)
      GoRoute(
        path: kBundlesPurchaseRoute,
        builder: (_, state) {
          final q = state.uri.queryParameters;
          return StkPurchaseScreen(
            args: StkPurchaseArgs(
              bundleId: q['bundleId'] ?? '',
              bundleName: q['bundleName'] ?? '',
              smsCount: int.tryParse(q['smsCount'] ?? '') ?? 0,
              priceTzs: int.tryParse(q['priceTzs'] ?? '') ?? 0,
              msisdn: q['msisdn'] ?? '',
              provider: q['provider'] ?? '',
            ),
          );
        },
      ),
      GoRoute(
        path: kContactsAddRoute,
        builder: (_, __) => const _PlaceholderScreen('Add Contact'),
      ),
      GoRoute(
        path: kCampaignsNewRoute,
        builder: (_, __) => const _PlaceholderScreen('Campaign Composer'),
      ),
    ],
  );
});
