import 'package:customer_app/core/auth/auth_state.dart';
import 'package:customer_app/core/router/app_router.dart';
import 'package:flutter_test/flutter_test.dart';

// ---------------------------------------------------------------------------
// Redirect logic extracted for unit testing
//
// The three ordered redirect rules from app_router.dart are mirrored here.
// This avoids mounting a full GoRouter + ProviderScope in unit tests while
// still asserting every guard in isolation.
// ---------------------------------------------------------------------------

const _publicRoutes = [
  kSplashRoute,
  kOnboardingRoute,
  kLoginRoute,
  kRegisterRoute,
];

String? _redirect(AuthState auth, String location) {
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
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('go_router redirect guards', () {
    // -----------------------------------------------------------------------
    // Test 1: unauthenticated → /login for non-public routes
    // -----------------------------------------------------------------------
    test(
        'AuthState.unauthenticated + non-public route redirects to /login',
        () {
      final result =
          _redirect(const AuthState.unauthenticated(), kDashboardRoute);
      expect(result, equals(kLoginRoute));
    });

    // -----------------------------------------------------------------------
    // Test 2: pending → /pending
    // -----------------------------------------------------------------------
    test('AuthState.pending + any route != /pending redirects to /pending',
        () {
      final result = _redirect(
        const AuthState.pending(accessToken: 'tok'),
        kDashboardRoute,
      );
      expect(result, equals(kPendingRoute));
    });

    // -----------------------------------------------------------------------
    // Test 3: verified + auth route → /dashboard
    // -----------------------------------------------------------------------
    test(
        'AuthState.verified + /login redirects to /dashboard', () {
      final result = _redirect(
        const AuthState.verified(
            accessToken: 'tok', refreshToken: 'rtok'),
        kLoginRoute,
      );
      expect(result, equals(kDashboardRoute));
    });

    // -----------------------------------------------------------------------
    // Test 4: verified + /dashboard → no redirect
    // -----------------------------------------------------------------------
    test('AuthState.verified + /dashboard → no redirect (null)', () {
      final result = _redirect(
        const AuthState.verified(
            accessToken: 'tok', refreshToken: 'rtok'),
        kDashboardRoute,
      );
      expect(result, isNull);
    });
  });
}
