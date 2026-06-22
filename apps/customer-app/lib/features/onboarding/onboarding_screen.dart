import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:customer_app/l10n/app_localizations.dart';
import 'package:customer_app/core/router/app_router.dart';

class _OnboardingSlide {
  final String Function(AppLocalizations l10n) title;
  final String Function(AppLocalizations l10n) body;
  final IconData icon;

  const _OnboardingSlide({
    required this.title,
    required this.body,
    required this.icon,
  });
}

final _slides = <_OnboardingSlide>[
  _OnboardingSlide(
    title: (l) => l.onboardingSlide1Title,
    body: (l) => l.onboardingSlide1Body,
    icon: Icons.group,
  ),
  _OnboardingSlide(
    title: (l) => l.onboardingSlide2Title,
    body: (l) => l.onboardingSlide2Body,
    icon: Icons.phone_android,
  ),
  _OnboardingSlide(
    title: (l) => l.onboardingSlide3Title,
    body: (l) => l.onboardingSlide3Body,
    icon: Icons.verified_user,
  ),
];

/// Onboarding screen — 3-slide PageView with skip/next/get-started navigation.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _controller = PageController();
  int _currentPage = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _next() {
    if (_currentPage < _slides.length - 1) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      context.go(kRegisterRoute);
    }
  }

  void _skip() {
    context.go(kLoginRoute);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final isLastPage = _currentPage == _slides.length - 1;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // Skip button top-right
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: _skip,
                child: Text(l10n.onboardingSkip),
              ),
            ),

            // Page view
            Expanded(
              child: PageView.builder(
                controller: _controller,
                onPageChanged: (i) => setState(() => _currentPage = i),
                itemCount: _slides.length,
                itemBuilder: (context, index) {
                  final slide = _slides[index];
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // Illustration placeholder (200 dp height)
                        SizedBox(
                          height: 200,
                          child: Icon(
                            slide.icon,
                            size: 120,
                            color: colorScheme.primary,
                          ),
                        ),
                        const SizedBox(height: 32),
                        Text(
                          slide.title(l10n),
                          style: textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                          textAlign: TextAlign.center,
                          softWrap: true,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          slide.body(l10n),
                          style: textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                          softWrap: true,
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),

            // Dot indicators
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_slides.length, (index) {
                return AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: _currentPage == index ? 12 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _currentPage == index
                        ? colorScheme.primary
                        : colorScheme.primary.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(4),
                  ),
                );
              }),
            ),
            const SizedBox(height: 16),

            // Next / Get Started button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _next,
                  child: Text(
                    isLastPage ? l10n.onboardingGetStarted : l10n.onboardingNext,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}
