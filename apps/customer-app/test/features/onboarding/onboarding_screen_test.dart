// MOBL-01: Onboarding screen tests — RED phase
// Tests assert: 3 slides, Get Started CTA on last slide, Skip navigates to /login,
// and bilingual l10n strings resolve correctly.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/features/onboarding/onboarding_screen.dart';

GoRouter _testRouter({String initialLocation = '/onboarding'}) {
  return GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(
        path: '/onboarding',
        builder: (_, __) => const OnboardingScreen(),
      ),
      GoRoute(
        path: '/login',
        builder: (_, __) => const Scaffold(body: Text('Login')),
      ),
      GoRoute(
        path: '/register',
        builder: (_, __) => const Scaffold(body: Text('Register')),
      ),
    ],
  );
}

Widget _wrapWithRouter({Locale locale = const Locale('en')}) {
  final router = _testRouter();
  return ProviderScope(
    child: MaterialApp.router(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: router,
    ),
  );
}

void main() {
  group('OnboardingScreen', () {
    testWidgets('Test 1: renders 3 slides and shows Get Started CTA on last slide',
        (tester) async {
      await tester.pumpWidget(_wrapWithRouter());
      await tester.pumpAndSettle();

      // Slide 1 should be visible initially
      expect(find.text('Send SMS to everyone'), findsOneWidget);

      // Swipe to slide 2
      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await tester.pumpAndSettle();
      expect(find.text('Buy credits in minutes'), findsOneWidget);

      // Swipe to slide 3 (last)
      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await tester.pumpAndSettle();
      expect(find.text('Verified and trusted'), findsOneWidget);

      // Last slide shows "Get Started" CTA
      expect(find.text('Get Started'), findsOneWidget);
    });

    testWidgets('Test 2: tapping Skip navigates to /login', (tester) async {
      await tester.pumpWidget(_wrapWithRouter());
      await tester.pumpAndSettle();

      // Find the Skip button
      expect(find.text('Skip'), findsOneWidget);
      await tester.tap(find.text('Skip'));
      await tester.pumpAndSettle();

      // Should navigate to /login
      expect(find.text('Login'), findsOneWidget);
    });

    testWidgets(
        'Test 3: all strings resolve from AppLocalizations — EN and SW spot-check',
        (tester) async {
      // English locale
      await tester.pumpWidget(_wrapWithRouter(locale: const Locale('en')));
      await tester.pumpAndSettle();

      expect(find.text('Send SMS to everyone'), findsOneWidget);
      expect(find.text('Skip'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);

      // Swahili locale
      await tester.pumpWidget(
          _wrapWithRouter(locale: const Locale('sw')));
      await tester.pumpAndSettle();

      // Swahili slide 1 title
      expect(find.text('Tuma SMS kwa kila mtu'), findsOneWidget);
      // Swahili skip button
      expect(find.text('Ruka'), findsOneWidget);
    });
  });
}
