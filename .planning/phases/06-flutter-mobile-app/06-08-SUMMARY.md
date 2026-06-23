---
phase: 06-flutter-mobile-app
plan: "08"
subsystem: customer-app/payments
tags: [flutter, riverpod, payments, azampay, stk, countdown, tdd, material3]
requirements: [MOBL-05]

dependency_graph:
  requires: ["06-02", "06-07"]
  provides: ["BundleCatalogScreen", "StkPurchaseScreen", "CountdownWidget", "BundleCard", "PaymentNotifier", "payment_api (bundles/initiate/status)", "StkPurchaseArgs"]
  affects: ["06-12"]

tech_stack:
  added: []
  patterns:
    - "CountdownWidget owns Timer.periodic(1s) seeded from server timeoutSeconds (never hardcoded); polls every 5s, calls onExpire at 0, cancels timer in dispose"
    - "PaymentNotifier (Notifier<PaymentState>) — initiate() POST /api/v1/payments, pollStatus() GET /api/v1/payments/{id}, markExpired(); invalidates balanceProvider on CONFIRMED"
    - "PaymentState via named constructors (idle/pending/confirmed/expired) with is* getters"
    - "StkPurchaseScreen ConsumerStatefulWidget fires initiate() in post-frame callback; PopScope canPop:false while pending"
    - "Route args passed via GoRouter query parameters parsed into StkPurchaseArgs in the route builder"
    - "NotifierProvider.overrideWith(() => fake) for widget-test isolation with a controllable clock via tester.pump(Duration)"

key_files:
  created:
    - apps/customer-app/lib/features/payments/payment_api.dart
    - apps/customer-app/lib/features/payments/payment_provider.dart
    - apps/customer-app/lib/features/payments/bundle_catalog_screen.dart
    - apps/customer-app/lib/features/payments/stk_purchase_screen.dart
    - apps/customer-app/lib/shared/widgets/bundle_card.dart
    - apps/customer-app/lib/shared/widgets/countdown_widget.dart
    - apps/customer-app/test/features/payments/bundle_catalog_test.dart
    - apps/customer-app/test/features/payments/stk_countdown_test.dart
  modified:
    - apps/customer-app/lib/core/router/app_router.dart
    - apps/customer-app/test/placeholder_mobl_test.dart
---

# 06-08 — Bundle purchase via Azampay STK push + 2-minute countdown (MOBL-05)

## What was built

The full bundle-purchase journey for the customer app:

- **Task 1 (MOBL-05a):** `BundleCatalogScreen` lists active bundles sorted ascending
  by `priceTzs` with TZS-formatted prices, empty-state heading, and a Buy → bottom
  sheet (msisdn `TextFormField` + 5-provider `SegmentedButton`: M-Pesa/Tigo/Airtel/
  Halo/AzamPesa) → confirm navigates to `/bundles/purchase`. `BundleCard` shared
  widget + `payment_api.dart` (bundles provider, `initiatePayment`, `getPaymentStatus`,
  `StkPurchaseArgs`).
- **Task 2 (MOBL-05b):** `StkPurchaseScreen` fires `POST /api/v1/payments` on entry,
  seeds `CountdownWidget` from the server's `timeoutSeconds` (120), polls
  `GET /api/v1/payments/{id}` every 5s, and transitions to SUCCESS on `CONFIRMED`
  (with wallet balance refresh) or EXPIRED on `EXPIRED`-or-zero (Try Again → `/bundles`,
  no auto-retry). Back navigation disabled while pending. `/bundles/purchase` route
  wired to parse args from query params.

## Verification

- `flutter test test/features/payments/` → **7/7 pass** (3 catalog + 4 countdown).
- `flutter analyze --no-fatal-infos` on the new files → no issues.
- Full-suite note: `placeholder_mobl_test.dart` MOBL-06/07/08/09 remain red-by-design
  in this isolated worktree (each removed by its own plan's branch); MOBL-05 placeholder
  removed here.

## TDD gate

Each task: RED test committed before implementation.
- `0f2b608` test(06-08): RED — bundle_catalog_test (MOBL-05a)
- `bb0b3bc` feat(06-08): catalog + BundleCard + PaymentApi (GREEN)
- `5c90f25` test(06-08): RED — stk_countdown_test (MOBL-05b)
- `f67275c` feat(06-08): countdown + polling + SUCCESS/EXPIRED (GREEN)

## Decisions & deviations

- **Resumed after a mid-task sandbox permission cutoff** (orchestrator finished Task 2:
  `countdown_widget.dart`, `stk_purchase_screen.dart`, router wiring, GREEN commit).
- Fixed a one-token arity bug in the committed RED test:
  `NotifierProvider.overrideWith` takes `Function()`, not `Function(_)` — behavioral
  assertions unchanged.
- All `stk*` l10n keys already existed (added in an earlier wave); no ARB/codegen changes
  needed. Success body uses `args.smsCount` (the status response does not echo count).
