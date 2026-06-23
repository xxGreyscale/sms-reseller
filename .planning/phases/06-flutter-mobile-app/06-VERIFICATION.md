---
phase: 06-flutter-mobile-app
verified: 2026-06-23T00:00:00Z
status: gaps_found
score: 4/5 success criteria verified (MOBL-01..08 delivered; SC5/MOBL-09 outstanding)
re_verification:
  previous_status: none
  note: initial verification
gaps:
  - truth: "App is live on Google Play and Apple App Store with all required metadata (icon, screenshots, privacy policy, store description) — SC5 / MOBL-09"
    status: failed
    reason: "Plan 06-12 (MOBL-09, autonomous:false) not started — no SUMMARY.md, marked [ ] in ROADMAP. This is a KNOWN OUTSTANDING manual checkpoint, not a surprise regression."
    artifacts:
      - path: "apps/customer-app/store/"
        issue: "Directory does not exist — no icon, screenshots, EN+SW descriptions, privacy policy, or SUBMISSION_CHECKLIST.md"
      - path: "apps/customer-app/android/app/build.gradle.kts"
        issue: "Only debug signingConfig present (line 37); no release signingConfig with key.properties fallback. No android/key.properties."
      - path: "apps/customer-app/integration_test/app_test.dart"
        issue: "12-line stub ('full app boot test implemented in a later wave', expect(true, isTrue)) — NOT the register→PENDING→VERIFIED→dashboard→purchase→send e2e spine required by 06-12 Task 1"
      - path: ".github/workflows/"
        issue: "No mobile/flutter/customer CI workflow exists — 06-12 Task 2 release build + macOS-runner note not delivered"
      - path: "apps/customer-app/test/placeholder_mobl_test.dart"
        issue: "MOBL-09 row is red-by-design (intentional placeholder); 06-12 deletes it on completion"
    missing:
      - "Release signing config (key.properties fallback to debug) so flutter build appbundle --release succeeds in CI/dev"
      - "store/ metadata: icon 512/1024, feature graphic, screenshots, EN+SW descriptions, privacy policy URL, SUBMISSION_CHECKLIST.md"
      - "Real end-to-end integration test exercising auth→purchase→send spine (replaces the 12-line stub)"
      - "CI workflow building the app + flagging macOS-runner availability for iOS IPA"
      - "Manual signed AAB/IPA build + store submission (autonomous:false checkpoint)"
---

# Phase 06: Flutter Mobile App — Verification Report

**Phase Goal:** The complete Flutter customer app is published to Google Play and Apple App Store, covering the full user journey from onboarding through NIDA verification, bundle purchase, contact management, campaign sending, and history.
**Verified:** 2026-06-23
**Status:** gaps_found (feature-complete for MOBL-01..08; phase blocked on 06-12 store submission)
**Re-verification:** No — initial verification

## Goal Achievement

### Roadmap Success Criteria

| # | Success Criterion | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | Onboarding → register + NIN → PENDING screen with auto-polling | ✓ VERIFIED | MOBL-01/02 below |
| 2 | Login + dashboard (balance, recent campaigns, quick-send) + session persists across restarts via stored JWT | ✓ VERIFIED | MOBL-03/04 below |
| 3 | Azampay bundle purchase, STK 2-min countdown, balance auto-updates | ✓ VERIFIED | MOBL-05 below |
| 4 | Flat contacts (manual add) + immediate-send composer + history with detail | ✓ VERIFIED | MOBL-06/07/08 below |
| 5 | Live on Google Play + Apple App Store with all metadata | ✗ FAILED | MOBL-09 outstanding — 06-12 not started |

**Score:** 4/5 success criteria; MOBL-01..08 all delivered, SC5/MOBL-09 outstanding.

### Requirement-Level Truths (MOBL-01..09)

| Req | Truth | Status | Evidence (file:symbol + test) |
|-----|-------|--------|-------------------------------|
| MOBL-01 | Splash routes by auth state; 3-slide onboarding PageView → register/login | ✓ DELIVERED | `onboarding/splash_screen.dart` (routes to dashboard/pending/onboarding); `onboarding/onboarding_screen.dart:91` PageView.builder, 173 lines; test `onboarding_screen_test.dart` ✓ |
| MOBL-02 | NIDA register (NIN submit) → PENDING walled state; 10s /auth/me auto-poll → VERIFIED | ✓ DELIVERED | `auth/register_screen.dart` (230 lines), `auth/nida_pending_screen.dart` (155), `auth/pending_poller_notifier.dart:18` Timer.periodic(10s) → /auth/me, cancelled onDispose; router Rule 2 walls Pending to /pending (`app_router.dart:106`); test `nida_pending_test.dart`, `register_screen_test.dart` ✓ |
| MOBL-03 | Login + JWT session persisted across restarts | ✓ DELIVERED | `auth/login_screen.dart` (176), `core/auth/auth_notifier.dart:11-47` reads access/refresh tokens from FlutterSecureStorage on build, decodes JWT → derives Pending/Verified; `auth_api.dart` real POST /login; test `login_session_test.dart` ✓ |
| MOBL-04 | Dashboard: balance (cache-read/online-write + stale flag), recent campaigns, quick-send FAB | ✓ DELIVERED | `dashboard/dashboard_screen.dart` (238) BalanceCard + recentCampaignsProvider + FAB→kCampaignsNewRoute; `dashboard/balance_provider.dart:46-58` Hive write-through + cached stale fallback; tests `dashboard_screen_test.dart`, `balance_cache_test.dart` ✓ |
| MOBL-05 | Bundle catalog + Azampay STK push + 2-min (120s) countdown + status poll → balance update | ✓ DELIVERED | `payments/payment_provider.dart:26` timeoutSeconds=120, POST /api/v1/payments seeds countdown; `stk_purchase_screen.dart:106` CountdownWidget polls status every 5s, nav disabled while running; `payment_api.dart` GET /payments/{id}; tests `bundle_catalog_test.dart`, `stk_countdown_test.dart` ✓ |
| MOBL-06 | Flat contact list (cache) + online manual add + delete | ✓ DELIVERED | `contacts/contact_list_screen.dart`, `add_contact_screen.dart` (148); `contact_api.dart` GET/POST/DELETE /api/v1/contacts; tests `contact_list_test.dart` (offline + StaleIndicator), `add_contact_test.dart` ✓ |
| MOBL-07 | Composer immediate-send via contactIds + GSM-7/UCS-2 counter | ✓ DELIVERED | `campaigns/composer_screen.dart:77` maps selected contacts → contactIds, calls sendCampaign; `campaign_api.dart:78,84` POST /campaigns + /campaigns/{id}/send; tests `composer_test.dart`, `sms_char_counter_test.dart` ✓ |
| MOBL-08 | Campaign history (paginated loadMore) + per-campaign detail with aggregate + per-message rows | ✓ DELIVERED | `campaigns/history_screen.dart` (118) loadMore on scroll, `detail_screen.dart` (198) aggregate stats + failed-tint rows; `campaign_api.dart:93,102` GET /campaigns; test `history_detail_test.dart` (3 tests) ✓ |
| MOBL-09 | Live on both stores + metadata + signing + CI + e2e | ✗ OUTSTANDING | 06-12 not started — see Gaps |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `lib/core/router/app_router.dart` | All routes wired to real screens | ✓ VERIFIED | 13 routes → real screens; redirect guards (unauth→login, pending→pending, verified→dashboard). `_PlaceholderScreen` is dead code (unused, flagged by analyze) — no route uses it |
| `lib/core/auth/auth_notifier.dart` | JWT persistence + state derivation | ✓ VERIFIED | Reads secure storage, decodes JWT claims |
| `lib/features/**` (8 features) | Substantive screens + providers + API | ✓ VERIFIED | All 148–272 lines; every *_api.dart makes real dio HTTP calls |
| `apps/customer-app/store/` | Store metadata | ✗ MISSING | Directory absent (MOBL-09) |
| `android/app/build.gradle.kts` release signing | Signed AAB build | ✗ MISSING | Only debug signingConfig (MOBL-09) |
| `integration_test/app_test.dart` | e2e auth→purchase→send spine | ✗ STUB | 12-line `expect(true, isTrue)` placeholder (MOBL-09) |
| `.github/workflows/*` mobile CI | Release build + macOS note | ✗ MISSING | No mobile workflow (MOBL-09) |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| Composer | messaging backend | POST /campaigns + /campaigns/{id}/send with contactIds[] | ✓ WIRED |
| Dashboard balance | wallet backend | GET /wallet/balance + Hive write-through, stale fallback | ✓ WIRED |
| STK screen | payment backend | POST /payments → 120s countdown → 5s GET /payments/{id} poll | ✓ WIRED |
| Pending poller | identity backend | 10s Timer.periodic GET /auth/me → VERIFIED transition | ✓ WIRED |
| auth_notifier | secure storage | read/write access+refresh tokens (session persistence) | ✓ WIRED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full unit/widget suite | `flutter test` | 68 pass / 1 fail | ✓ PASS (the 1 fail is the intentional MOBL-09 placeholder red) |
| Placeholder isolation | `flutter test test/placeholder_mobl_test.dart` | -1: "MOBL-09 placeholder [E] not implemented" | ✓ EXPECTED (red-by-design, deleted by 06-12) |
| Static analysis | `flutter analyze --no-fatal-infos` | 16 issues, 0 errors | ✓ PASS (all warnings/info: unused_element on dead `_PlaceholderScreen`, redundant `!`, deprecated `surfaceVariant`, unused test imports) |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `lib/core/router/app_router.dart` | 28 | `_PlaceholderScreen` declared but unreferenced | ℹ️ Info | Dead code; no route uses it — all routes point to real screens. Safe to delete. |
| `lib/features/campaigns/composer_screen.dart` | 66,102 | Redundant `!` non-null assertion | ℹ️ Info | Lint noise, no behavior impact |
| `lib/shared/widgets/campaign_*` | 27,31 | Deprecated `surfaceVariant` | ℹ️ Info | Pre-existing Flutter deprecation; cosmetic |
| `integration_test/app_test.dart` | 9 | "implemented in a later wave" stub | ⚠️ Warning | Expected — owned by MOBL-09/06-12; not a MOBL-01..08 defect |

No TBD/FIXME/XXX debt markers found in MOBL-01..08 source. No hollow/unwired data paths in delivered features.

### Human Verification Required

MOBL-09 (06-12) is an `autonomous:false` manual checkpoint requiring human action and external accounts. The following are inherently out of automated scope and must be completed/verified by a human:

#### 1. Signed release builds
**Test:** Build production-signed AAB (`flutter build appbundle --release` with real keystore) and iOS IPA (on macOS runner).
**Expected:** Both artifacts produced and uploaded.
**Why human:** Requires production keystore, Apple Developer cert, and macOS hardware — no automated path.

#### 2. Store submission + metadata
**Test:** Create Play + App Store listings with icon, screenshots, EN+SW descriptions, privacy policy, content ratings; submit.
**Expected:** Apps submitted (review approval is external/async per D-02).
**Why human:** External developer accounts + manual console submission.

### Gaps Summary

MOBL-01 through MOBL-08 are **feature-complete and genuinely wired** — every requirement traces to a substantive screen + provider + real backend API call, backed by a passing test. Session persistence, the NIDA PENDING wall + 10s poller, the 120s STK countdown with status polling, balance cache-read/online-write, contactIds-based composer send, and paginated history/detail are all implemented end-to-end (not stubbed). The single failing test is the intentional MOBL-09 placeholder; analyze reports only warnings/info (no errors).

The phase is **blocked on plan 06-12 (MOBL-09 / SC5)**, which is not started: no `store/` metadata, no release signing config, no mobile CI workflow, and the `integration_test/app_test.dart` is a 12-line stub rather than the required register→purchase→send e2e spine. MOBL-09 is an `autonomous:false` manual checkpoint (signed AAB/IPA + store submission) requiring external accounts and human action.

**Overall verdict:** Feature-complete for MOBL-01..08; phase NOT done — blocked on 06-12 store submission (MOBL-09), which combines automatable artifacts (signing config, store metadata scaffold, CI, e2e test) and a human-only submission checkpoint.

---

_Verified: 2026-06-23_
_Verifier: Claude (gsd-verifier)_
