---
phase: 06-flutter-mobile-app
plan: "12"
subsystem: customer-app/release
tags: [flutter, integration-test, signing, store-metadata, ci, mobl-09]
requirements: [MOBL-09]

dependency_graph:
  requires: ["06-05", "06-06", "06-07", "06-08", "06-09", "06-10", "06-11"]
  provides: ["e2e integration spine", "android release signing (debug fallback)", "EN+SW store metadata", "privacy policy", "submission checklist", "customer-app CI workflow"]
  affects: []

tech_stack:
  added: []
  patterns:
    - "Integration spine boots the real SmsResellerApp against a path-routed mock Dio + temp Hive; runs on host via `-d flutter-tester` (no device/Android SDK needed)"
    - "Android release signing reads android/key.properties (gitignored) and falls back to the debug signingConfig when absent, so `flutter build appbundle --release` succeeds in CI/dev without the production keystore"
    - "MOBL-09 Nyquist coverage = store_assets_test.dart (asserts metadata/privacy/CI/signing deliverables exist) instead of a perpetual red placeholder"

key_files:
  created:
    - apps/customer-app/integration_test/app_test.dart
    - apps/customer-app/android/key.properties.template
    - apps/customer-app/store/metadata/android/en-US/full_description.txt
    - apps/customer-app/store/metadata/android/sw/full_description.txt
    - apps/customer-app/store/metadata/ios/en-US/description.txt
    - apps/customer-app/store/metadata/ios/sw/description.txt
    - apps/customer-app/store/PRIVACY_POLICY.md
    - apps/customer-app/store/SUBMISSION_CHECKLIST.md
    - apps/customer-app/test/store/store_assets_test.dart
    - .github/workflows/customer-app.yml
  modified:
    - apps/customer-app/android/app/build.gradle.kts
    - apps/customer-app/test/placeholder_mobl_test.dart

status: deliverables-complete-submission-pending
---

# 06-12 — Store submission prep (MOBL-09)

## What was built (Tasks 1 & 2 — complete, committed)

- **Task 1 — e2e integration spine** (`integration_test/app_test.dart`, commit a2e970d): boots the
  real `SmsResellerApp` (go_router + full Riverpod graph) against a path-routed mock Dio and a temp Hive
  store, walking PENDING wall → `/auth/me` VERIFIED → dashboard balance → bundle catalog → STK purchase
  (CONFIRMED) → contacts → campaign send (contactIds[]) → detail. Proves all waves compose. Runs on
  host via `flutter test integration_test/app_test.dart -d flutter-tester`.
- **Task 2 — release tooling & store assets** (commit bfe8186):
  - `android/app/build.gradle.kts`: release signing from `key.properties` with a **debug-signing
    fallback** when the keystore is absent; `targetSdk` floored at 34. `key.properties.template`
    committed (real `key.properties`/`*.jks` already gitignored).
  - EN+SW store descriptions (Google Play + App Store), `PRIVACY_POLICY.md` (declares phone, email,
    NIN/NIDA, payment-data collection), and `SUBMISSION_CHECKLIST.md`.
  - `.github/workflows/customer-app.yml`: Linux job (analyze + test + e2e + release AAB build) and a
    `vars.RUN_IOS_BUILD`-gated macOS IPA job (Open Question #5 — external runner/account).
  - `store_assets_test.dart` replaces the MOBL-09 red placeholder with real deliverable coverage.

## Verification

- `flutter test` → **74 pass / 0 fail** (the long-standing red-by-design MOBL-09 placeholder is gone).
- `flutter test integration_test/app_test.dart -d flutter-tester` → e2e spine **green**.
- `flutter analyze --no-fatal-infos` → 0 errors (pre-existing warnings only).

## Task 3 — store submission (BLOCKING-HUMAN, external — PENDING)

Per **D-02** the phase is "done" once signed builds are **submitted**; review/approval is external
(**D-03**). The actual `flutter build appbundle --release` / `flutter build ipa` and the store uploads
cannot run in this environment and are deferred:

- This machine has **no Android SDK and no Xcode** (`flutter doctor` both ✗) — local AAB/IPA builds
  cannot run here; use CI or a provisioned dev machine.
- **No Google Play / Apple Developer accounts or signing keys** provisioned yet (D-03).

Everything required in the repo is complete; submission is a tracked external action — see
`store/SUBMISSION_CHECKLIST.md`. Resume signal: type **"submitted"** once both stores accept the
builds, or describe a blocker.

## Decisions & deviations

- Executed **inline** (not a worktree subagent) because the build verify is heavy and Task 3 needs
  human interaction; this was a user-approved scope decision ("build all repo deliverables now").
- Plan listed `android/app/build.gradle` (Groovy) but the project uses **`build.gradle.kts`** (Kotlin
  DSL) — edited the actual file.
- Did **not** edit `ios/Runner/Info.plist` (iOS min-deployment lives in `project.pbxproj`; editing it
  blindly is risky) — min iOS 12.0 is documented in the submission checklist instead.
- AAB-build verification (`flutter build appbundle --release`) deferred to CI/provisioned env per the
  environment constraint above; the debug-fallback makes it keystore-free there.
