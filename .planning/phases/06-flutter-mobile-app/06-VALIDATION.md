---
phase: 6
slug: flutter-mobile-app
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-22
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Flutter framework** | `flutter_test` (widget/unit) + `integration_test` (end-to-end flows) + `mocktail`/mock Dio for API contract tests; `riverpod` ProviderContainer overrides for state tests |
| **Backend framework** | JUnit 5 + Testcontainers 1.21.2 (for the D-11..D-14 backend additions in identity/payment/messaging/notification services) |
| **App location** | `apps/customer-app` (Flutter project — created in Wave 0) |
| **Quick run command** | `cd apps/customer-app && flutter test` (widget/unit) |
| **Full suite command** | `cd apps/customer-app && flutter test integration_test` ; `./gradlew :services:identity-service:test :services:payment-service:test :services:messaging-service:test :services:notification-service:test` (backend gap additions) |
| **Estimated runtime** | ~60–120s Flutter unit/widget + integration; backend additions ~60s |

---

## Sampling Rate

- **After every task commit:** Run the touched layer's quick test (`flutter test` for app tasks, `./gradlew :services:<svc>:test` for backend-gap tasks)
- **After every plan wave:** Run `flutter test` + `flutter test integration_test` + the affected backend suites
- **Before `/gsd:verify-work`:** Full Flutter suite green + backend gap suites green + `flutter build apk` (and `flutter build ipa` if macOS CI available) succeed
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

> Populated by the planner / Wave 0 (one row per task). Every MOBL requirement maps to at least
> one automated row (widget/integration test). Store submission (MOBL-09) is a manual checkpoint.
> The 4 backend additions (D-11..D-14) get backend integration tests in their owning services.

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| _TBD by planner_ | | | MOBL-01..09 + D-11..D-14 backend | | | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Flutter project scaffold at `apps/customer-app` (Flutter 3.41.x), Riverpod 3 (`@riverpod` codegen) + go_router 17 + Dio + hive_ce_flutter + flutter_secure_storage + flutter l10n (EN+SW ARB)
- [ ] Test harness: `flutter_test` + `integration_test` + mock Dio; one failing placeholder test per MOBL requirement so the validation map is non-empty
- [ ] Backend-gap RED tests in owning services: `GET /payments/{id}` (D-11), campaign `contactIds` (D-12), `GET /auth/me` (D-13), optional notif read (D-14)
- [ ] CI note: confirm/flag `macos-latest` runner availability for `flutter build ipa` (Open Question #5)

*Detailed Wave 0 task list produced by the planner.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| App published to Google Play + Apple App Store | MOBL-09 | Requires Apple/Google developer accounts + external store review (D-02/D-03) | Build signed AAB + IPA, upload to Play Console + App Store Connect with metadata (icon, screenshots, privacy policy, descriptions); submit for review |
| Real Azampay STK on a physical handset (in-app) | MOBL-05 | Needs live merchant creds + a real phone (Phase-0 dependency); stub gateway covers flow in CI | When merchant account arrives: real purchase from the app, confirm STK prompt + balance update |
| Real NIDA verification in-app | MOBL-02 | Needs live NIDA access (Phase-0); StubNidaVerificationService drives PENDING→VERIFIED in dev | When NIDA access arrives: register with a real NIN, confirm PENDING→VERIFIED transition |

*All other app behaviors have automated widget/integration coverage against mock Dio + the live/stubbed backend.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Flutter scaffold + test harness + backend-gap RED tests)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
