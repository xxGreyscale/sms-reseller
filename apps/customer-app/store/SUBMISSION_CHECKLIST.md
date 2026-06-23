# Store Submission Checklist — Open Desk Customer App (MOBL-09)

Per **D-02**, this phase is "done" once signed builds are **submitted** to both stores.
Final review/approval is external and tracked separately (**D-03** prerequisite).

> Environment note: this repo was developed on a machine without the Android SDK or
> Xcode, so the signed AAB/IPA builds and the store submissions are performed in a
> provisioned environment (or CI). Everything else (app code, e2e test, signing config,
> metadata, privacy policy, CI workflow) is complete in the repo.

## External Prerequisites (D-03 — human action)
- [ ] **Google Play Console** developer account (one-time USD 25).
- [ ] **Apple Developer Program** membership (USD 99/yr) — required to build/sign an IPA.
- [ ] **Android upload keystore** generated; `android/key.properties` + `key.jks` created locally (both gitignored). See `android/key.properties.template`.
- [ ] **Apple distribution certificate** + provisioning profile.
- [ ] CI secrets set (if signing in CI): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_PROPERTIES`, `APPLE_DISTRIBUTION_CERT`.
- [ ] **Public privacy-policy URL** hosting `store/PRIVACY_POLICY.md`.

## Build
- [ ] `cd apps/customer-app && flutter test` — full suite green.
- [ ] `flutter test integration_test/app_test.dart -d flutter-tester` — e2e spine green.
- [ ] `flutter build appbundle --release` → `build/app/outputs/bundle/release/app-release.aab` (debug-signing fallback works without the keystore; use the real `key.properties` for the production-signed build).
- [ ] On macOS: `flutter build ipa --release` → `build/ios/ipa/*.ipa`.

## Google Play (Android)
- [ ] Create the app in Play Console; package `com.opendesk.customer_app`.
- [ ] App icon **512×512**.
- [ ] Feature graphic **1024×500**.
- [ ] **≥2 phone screenshots** (dashboard, composer, purchase recommended).
- [ ] Full description EN — `store/metadata/android/en-US/full_description.txt`.
- [ ] Full description SW — `store/metadata/android/sw/full_description.txt`.
- [ ] Privacy policy URL.
- [ ] IARC content-rating questionnaire.
- [ ] Data safety form (phone, email, NIN, payment data — see PRIVACY_POLICY.md).
- [ ] Upload AAB → submit for review.

## Apple App Store (iOS)
- [ ] Create app record in App Store Connect; bundle id matches `com.opendesk.customer_app`; min iOS **12.0**.
- [ ] Screenshots per required device sizes.
- [ ] Description EN — `store/metadata/ios/en-US/description.txt`.
- [ ] Description SW — `store/metadata/ios/sw/description.txt`.
- [ ] Privacy nutrition labels (phone, email, NIN/identity, payment data).
- [ ] Age rating.
- [ ] Upload via TestFlight → submit for review.

## Done When
- [ ] Both stores show the build **submitted** (receipt emails received).
- [ ] (External, tracked separately) Review approval / live listing.
