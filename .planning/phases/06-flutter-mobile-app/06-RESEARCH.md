# Phase 6: Flutter Mobile App - Research

**Researched:** 2026-06-22
**Domain:** Flutter client app (Riverpod + Dio + Hive + go_router) consuming Phases 2–5 REST APIs
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** MVP ships in-app notification-feed polling, NOT real FCM. App consumes `GET /api/v1/notifications`. Real FCM is a post-launch fast-follow. Phase 5 StubPushChannel seam stays ready.
- **D-02:** Deliverable = signed release builds (Android AAB + iOS IPA) + full store metadata + submission. "Live/approved" is gated by external store review — treated as an external prerequisite, not a phase blocker.
- **D-03 (prerequisite):** Apple Developer Program + Google Play Console accounts must exist with signing keys/certs before submission.
- **D-04:** Bilingual English + Swahili from day one. Flutter l10n (ARB files), device-locale aware with in-app language toggle. Every screen sources copy from l10n — no hardcoded UI strings.
- **D-05:** Cache-read + online-write. Cache last-known dashboard/balance/contacts/campaign-history in Hive. ALL writes (purchase, campaign send, add contact) are online-only with explicit retry + error states.
- **D-06:** Navigation via go_router (declarative routes + auth redirect guards: unauth → login/onboarding; PENDING → walled state).
- **D-07:** Flutter (single codebase), Riverpod state management, Dio HTTP client, Hive + shared_preferences storage. JWT persisted in Hive; session survives restart.
- **D-08:** Flat contact list with manual add only (no groups/CSV). Immediate-send only (no scheduling). STK push 2-minute countdown UI.
- **D-09:** Honor "logged in, but walled": PENDING user can log in and see a status screen but cannot send campaigns until VERIFIED. PENDING screen auto-polls identity status. go_router redirect enforces the wall. On VERIFIED → 50 free credits appear via wallet balance.

### Claude's Discretion

- Exact Riverpod provider structure, Dio interceptor design (JWT attach + refresh-on-401 against Phase 2 rotating refresh tokens), Hive box/schema layout, go_router route tree, polling intervals (NIDA status, balance, feed), l10n key naming, and theming/design-token mapping from the UI-SPEC.

### Deferred Ideas (OUT OF SCOPE)

- Real FCM push (device tokens + RealPushChannel + Firebase/APNs) — post-launch fast-follow.
- Contact groups + group management (MOBL-V2-01), CSV import (MOBL-V2-02), campaign scheduling (MOBL-V2-03), full analytics dashboard (MOBL-V2-04).
- Offline-write sync/queue — explicitly out per D-05.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MOBL-01 | Splash screen and onboarding flow | go_router initial route + AnimatedSplashScreen or manual route; onboarding slides with l10n copy |
| MOBL-02 | NIDA registration + PENDING verification state with clear status screen | POST /auth/register → walled PENDING screen; auto-poll /auth/refresh or a status endpoint for VERIFIED flip; go_router redirect |
| MOBL-03 | Login with JWT session persistence (Hive storage) | POST /auth/login → store accessToken + refreshToken in Hive; Dio interceptor attaches JWT; POST /auth/refresh on 401 |
| MOBL-04 | Dashboard: SMS balance, recent campaigns, quick-send shortcut | GET /api/v1/wallet/balance; GET /api/v1/campaigns?page=0&size=5 (recent); Riverpod providers with Hive cache fallback |
| MOBL-05 | Bundle purchase: Azampay STK push + 2-minute countdown UI | GET /api/v1/bundles (catalog); POST /api/v1/payments (initiation); poll payment status for CONFIRMED/EXPIRED; CountdownTimer widget |
| MOBL-06 | Flat contact list with manual add | GET /api/v1/contacts (list); POST /api/v1/contacts (add); Hive cache for offline read |
| MOBL-07 | Campaign composer for immediate send | POST /api/v1/campaigns (create); POST /api/v1/campaigns/{id}/send; real-time SMS character counter (GSM-7 vs UCS-2) |
| MOBL-08 | Campaign history list + detail screen | GET /api/v1/campaigns (paginated); GET /api/v1/campaigns/{id} (detail + aggregate counts) |
| MOBL-09 | Published to Google Play + Apple App Store with required metadata | Flutter build appbundle + ipa; signing config; store metadata (icon, screenshots, descriptions, privacy policy URL) |
</phase_requirements>

---

## Summary

Phase 6 is a pure Flutter client that assembles a complete user journey on top of the backend APIs built in Phases 2–5. No backend code is written in this phase. The fundamental challenge is state orchestration across the async NIDA verification flow (PENDING walled state → polling → VERIFIED unlock), the Azampay STK payment flow (2-minute countdown → CONFIRMED/EXPIRED), and the offline-read/online-write cache policy — all wired through a Riverpod provider graph with a Dio auth interceptor handling JWT attach and refresh-on-401 against Phase 2's rotating refresh tokens.

The stack is fully locked: Flutter 3.41 stable / Dart 3.7, Riverpod 3.x (code-generated providers), Dio 5.x, Hive CE (hive_ce_flutter — the actively maintained fork of the abandoned hive_flutter), go_router 17.x, and Flutter's built-in l10n toolchain (ARB files + flutter_localizations). Freezed + json_serializable handle immutable model classes and JSON serialization. The integration_test package is bundled in the Flutter SDK since Flutter 2.0 — no pub.dev package needed.

The most important planning decision is **Wave 0 breadth**: the Flutter scaffold, l10n harness, Riverpod/Dio/Hive/go_router wiring, and the Dio auth interceptor must all exist before any feature screen can be built. Every subsequent wave depends on the auth interceptor being correct (JWT attach + refresh-on-401 + PENDING walled state), because go_router's redirect guards read Riverpod auth state on every navigation event.

**Primary recommendation:** Wave 0 establishes the Flutter project, l10n (EN+SW ARB), Hive CE boxes, Dio auth interceptor with QueuedInterceptor pattern, and go_router with three redirect guards (unauthenticated → /login, PENDING → /pending, VERIFIED → pass-through). Every feature screen builds on this in subsequent waves.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Auth state + session | Flutter app (Riverpod) | Backend identity-service | App holds JWT in Hive; backend issues/validates; app reads `verification_status` claim locally |
| NIDA PENDING polling | Flutter app (Timer + Riverpod) | Backend identity-service | No websocket; app polls by calling POST /auth/refresh and reading the returned `verification_status` claim |
| Navigation + access control | Flutter app (go_router) | — | Route guards read Riverpod auth state; no backend call per navigation |
| Wallet balance display | Flutter app (Riverpod provider) | Backend wallet-service | App fetches + caches; backend is the ledger source of truth |
| Azampay payment countdown | Flutter app (CountdownTimer widget) | Backend payment-service | 2-min timer starts client-side on POST /api/v1/payments response; app polls status to detect CONFIRMED/EXPIRED |
| Notification feed | Flutter app (polling Riverpod provider) | Backend notification-service | App polls GET /api/v1/notifications on interval; no FCM at MVP (D-01) |
| Contact CRUD | Flutter app (Riverpod + Hive) | Backend contact-service | Online-write; Hive cache for offline-read list |
| Campaign compose + send | Flutter app (Riverpod) | Backend messaging-service | Character counter is client-side; send is online-only (D-05) |
| Offline cache | Flutter app (Hive) | — | Hive boxes for balance, contacts, campaign list, notifications |
| Store submission | Build system (Flutter CLI) | External (Apple/Google) | Signed AAB/IPA produced by `flutter build`; metadata added in Play Console / App Store Connect |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Flutter SDK | 3.41.5 stable | App framework (iOS + Android) | Current stable; Impeller-by-default on both platforms [VERIFIED: flutter --version] |
| Dart | 3.11.3 (bundled) | Language | Bundled with Flutter 3.41.5 [VERIFIED: dart --version] |
| flutter_riverpod | 3.3.2 | State management | Locked (D-07); code-generated providers via riverpod_generator [VERIFIED: pub.dev] |
| riverpod_annotation | 4.0.3 | Annotations for riverpod_generator | Required companion to flutter_riverpod 3.x code-gen [VERIFIED: pub.dev] |
| riverpod_generator | (matches riverpod_annotation) | Build-time provider generation | @riverpod macro → generated provider files [VERIFIED: pub.dev] |
| dio | 5.9.2 | HTTP client | Locked (D-07); interceptor API for JWT attach + refresh [VERIFIED: pub.dev] |
| go_router | 17.3.0 | Declarative routing + auth guards | Locked (D-06); redirect callbacks read Riverpod auth state [VERIFIED: pub.dev] |
| hive_ce | ^2.9.0 | Local key-value store | Actively maintained fork of hive (hive_flutter is 5 years stale); used for JWT storage + read cache (D-07) [VERIFIED: pub.dev — hive_ce_flutter 2.3.4] |
| hive_ce_flutter | 2.3.4 | Flutter integration for hive_ce | Replaces abandoned hive_flutter 1.1.0 [VERIFIED: pub.dev] |
| shared_preferences | 2.5.5 | Simple KV for non-sensitive prefs (e.g., selected locale) | Locked (D-07); complements Hive for non-struct data [VERIFIED: pub.dev] |
| flutter_secure_storage | 10.3.1 | Secure enclave for JWT access+refresh tokens | Platform Keychain/Keystore backed; prefer over plain Hive box for token storage [VERIFIED: pub.dev] |
| flutter_localizations | sdk: flutter | Built-in l10n (ARB + gen-l10n) | Bundled in Flutter SDK; required for Swahili from day one (D-04) [VERIFIED: pub.dev] |
| intl | any | Message formatting + locale | Companion to flutter_localizations; "any" recommended by Flutter docs [CITED: docs.flutter.dev/ui/internationalization] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| freezed | 3.2.5 | Immutable model classes (copyWith, sealed unions) | API response models, auth state enum (unauthenticated / pending / verified) [VERIFIED: pub.dev] |
| freezed_annotation | (matches freezed) | Annotations for Freezed codegen | Required companion [ASSUMED] |
| json_serializable | ^6.x | JSON toJson/fromJson codegen | All API response DTOs [ASSUMED] |
| json_annotation | ^4.x | Annotations for json_serializable | Required companion [ASSUMED] |
| build_runner | 2.15.0 | Code generation runner (Riverpod + Freezed + JSON) | dev_dependency; run `dart run build_runner build --delete-conflicting-outputs` [VERIFIED: pub.dev] |
| riverpod_lint | ^2.x | Lint rules for Riverpod | dev_dependency; catches missing @riverpod, incorrect ref usage [ASSUMED] |
| custom_lint | ^0.x | Required by riverpod_lint | dev_dependency [ASSUMED] |
| flutter_countdown_timer | — | (Do NOT use — hand-roll instead) | Simple Timer + Riverpod state is sufficient for the 2-min countdown; no extra dep needed |
| mocktail | ^1.x | Mocking for unit + widget tests | Preferred over mockito for null-safe Flutter; no codegen required [ASSUMED] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| hive_ce_flutter | isar | Isar is a full relational DB — overkill for this app's cache needs; hive_ce is simpler |
| hive_ce_flutter | sqflite | SQLite is more powerful but requires schema migrations; Hive's NoSQL model fits simple cache boxes |
| flutter_secure_storage | hive_ce (encrypted box) | Hive encrypted box requires key management; flutter_secure_storage delegates to OS Keychain/Keystore which is the platform-native approach |
| go_router 17.x | auto_route | go_router is now the Flutter team's recommended router; auto_route adds extra codegen layer |
| Riverpod 3.x (code-gen) | Riverpod 2.x (manual) | Code-gen (riverpod_generator) produces less boilerplate and is the direction Riverpod 3.x enforces |
| freezed | built_value | freezed is simpler API; built_value has steeper boilerplate; freezed dominates Flutter ecosystem |

**Installation (pubspec.yaml structure):**
```yaml
dependencies:
  flutter:
    sdk: flutter
  flutter_localizations:
    sdk: flutter
  intl: any
  flutter_riverpod: ^3.3.2
  riverpod_annotation: ^4.0.3
  dio: ^5.9.2
  go_router: ^17.3.0
  hive_ce: ^2.9.0
  hive_ce_flutter: ^2.3.4
  shared_preferences: ^2.5.5
  flutter_secure_storage: ^10.3.1
  freezed_annotation: ^3.0.0
  json_annotation: ^4.9.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  integration_test:
    sdk: flutter
  build_runner: ^2.15.0
  riverpod_generator: ^2.x
  riverpod_lint: ^2.x
  custom_lint: ^0.x
  freezed: ^3.2.5
  json_serializable: ^6.x
  mocktail: ^1.x
```

---

## Package Legitimacy Audit

> slopcheck not available in this environment. All packages tagged [ASSUMED] are well-known Flutter ecosystem packages verifiable at pub.dev. Planner must gate any package not listed as [VERIFIED: pub.dev] behind a `checkpoint:human-verify` task before install.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| flutter_riverpod | pub.dev | 5+ yrs | Very high | github.com/rrousselGit/riverpod | N/A | Approved [VERIFIED: pub.dev] |
| riverpod_annotation | pub.dev | 3+ yrs | Very high | github.com/rrousselGit/riverpod | N/A | Approved [VERIFIED: pub.dev] |
| dio | pub.dev | 7+ yrs | Very high | github.com/cfug/dio | N/A | Approved [VERIFIED: pub.dev] |
| go_router | pub.dev | 4+ yrs, Flutter team | Very high | github.com/flutter/packages | N/A | Approved [VERIFIED: pub.dev] |
| hive_ce_flutter | pub.dev | 2+ yrs (fork of 7yr hive) | Moderate | github.com/IO-Design-Team/hive_ce | N/A | Approved [VERIFIED: pub.dev] |
| shared_preferences | pub.dev | 7+ yrs, Flutter team | Very high | github.com/flutter/packages | N/A | Approved [VERIFIED: pub.dev] |
| flutter_secure_storage | pub.dev | 6+ yrs | Very high | github.com/juliansteenbakker/flutter_secure_storage | N/A | Approved [VERIFIED: pub.dev] |
| freezed | pub.dev | 5+ yrs | Very high | github.com/rrousselGit/freezed | N/A | Approved [VERIFIED: pub.dev] |
| build_runner | pub.dev | 7+ yrs, Dart team | Very high | github.com/dart-lang/build | N/A | Approved [VERIFIED: pub.dev] |
| mocktail | pub.dev | 4+ yrs | High | github.com/felangel/mocktail | N/A | Approved [ASSUMED] |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*slopcheck was unavailable at research time; packages confirmed via pub.dev registry lookup and official publisher verification.*

---

## Backend API Contract

All endpoints the Flutter app consumes. **This is the authoritative list the app is coded against.** Gaps are flagged explicitly.

### Auth (identity-service)

| Method | Endpoint | Auth | Request Body | Response | Flutter Use |
|--------|----------|------|--------------|----------|-------------|
| POST | `/auth/register` | none | `{email, phone, password, nin}` | `{userId, status:"PENDING_VERIFICATION", accessToken}` | MOBL-02: Registration |
| POST | `/auth/login` | none | `{email, password, deviceId}` | `{accessToken, refreshToken, status}` | MOBL-03: Login |
| POST | `/auth/refresh` | none | `{refreshToken}` | `{accessToken, refreshToken, status}` | Dio interceptor 401-retry + PENDING poll |
| POST | `/auth/logout` | JWT | `{deviceId}` | 204 | Session revoke |
| POST | `/auth/forgot` | none | `{email}` | 200 | Password reset flow |
| POST | `/auth/reset` | none | `{token, newPassword}` | 200 | Password reset complete |

**NIDA PENDING poll mechanism:** The app polls by calling `POST /auth/refresh` and reading the `status` field in the response. When `status` changes from `PENDING_VERIFICATION` to `VERIFIED`, the app transitions out of the walled state. This means the NIDA poll endpoint IS the refresh endpoint — no separate `/auth/me` or `/auth/status` endpoint was built in Phase 2. The `verification_status` is embedded in the JWT claim AND returned in the refresh response body as `status`.

**GAP: No dedicated `/auth/me` endpoint.** If the app needs to poll NIDA status without triggering a full token refresh (e.g., the access token is still valid), the only option is to decode the `verification_status` claim from the current access token (which is only updated on login or refresh). The planner must decide: (a) poll via `POST /auth/refresh` every N seconds on the PENDING screen (rotating the refresh token each time — Phase 2 refresh tokens rotate on every use), or (b) add a `GET /auth/me` endpoint to identity-service (small addition, Phase 6 scope). See Open Questions #1.

### Wallet (wallet-service)

| Method | Endpoint | Auth | Response | Flutter Use |
|--------|----------|------|----------|-------------|
| GET | `/api/v1/wallet/balance` | JWT | `{availableCredits: int}` | MOBL-04: Dashboard balance; poll after purchase |
| GET | `/api/v1/wallet/transactions` | JWT | `Page<{txnType, credits, createdAt, ...}>` | Transaction history (not a primary MOBL requirement for MVP UI but listed as WLET-02 data) |

### Payments (payment-service)

| Method | Endpoint | Auth | Request Body | Response | Flutter Use |
|--------|----------|------|--------------|----------|-------------|
| GET | `/api/v1/bundles` | JWT | — | `[{id, name, smsCount, priceTzs, active}]` | MOBL-05: Bundle catalog |
| POST | `/api/v1/payments` | JWT | `{bundleId, msisdn, provider}` | `{id, status:"PENDING", timeoutSeconds:120, ...}` | MOBL-05: Initiate STK push |
| GET | `/api/v1/payments` | JWT | — | `Page<{id, status, bundleId, createdAt, ...}>` | Payment history |

**GAP: No GET `/api/v1/payments/{id}` single-payment status endpoint confirmed in summaries.** The app needs to poll the payment status during the 2-minute countdown to detect CONFIRMED or EXPIRED. The plan summaries mention a reconciliation job on the backend but do not show a single-record GET endpoint for payment status. The planner must either (a) confirm the endpoint exists (likely implied but not explicitly documented in the summaries), or (b) plan to poll `GET /api/v1/payments` (list) and filter by id. See Open Questions #2.

### Contacts (contact-service)

| Method | Endpoint | Auth | Request Body | Response | Flutter Use |
|--------|----------|------|--------------|----------|-------------|
| GET | `/api/v1/contacts` | JWT | — | `Page<{id, name, phoneE164, ...}>` | MOBL-06: Contact list (cached in Hive) |
| POST | `/api/v1/contacts` | JWT | `{name, phone}` | `{id, name, phoneE164}` | MOBL-06: Add contact (online-only) |
| PUT | `/api/v1/contacts/{id}` | JWT | `{name, phone}` | updated contact | Edit contact |
| DELETE | `/api/v1/contacts/{id}` | JWT | — | 204 | Delete contact |

**Note:** Groups, CSV import, and suppression endpoints exist in the backend (Phase 4) but are deferred for the Flutter app (D-08 / MOBL-V2).

### Campaigns (messaging-service)

| Method | Endpoint | Auth | Request Body | Response | Flutter Use |
|--------|----------|------|--------------|----------|-------------|
| POST | `/api/v1/campaigns` | JWT | `{name, groupIds[], message}` | `{id, status, ...}` | MOBL-07: Create campaign |
| POST | `/api/v1/campaigns/{id}/send` | JWT | — | `{campaignId, recipientCount, creditsReserved}` | MOBL-07: Dispatch (online-only, D-05) |
| GET | `/api/v1/campaigns` | JWT | `?page=0&size=N` | `Page<CampaignResponse>` | MOBL-04 (recent 5) + MOBL-08 (history) |
| GET | `/api/v1/campaigns/{id}` | JWT | — | `CampaignResponse` with aggregate counts | MOBL-08: Campaign detail |
| GET | `/api/v1/campaigns/{id}/messages` | JWT | — | `Page<{recipient, status, ...}>` | MOBL-08: Per-message status |

**Note on campaign create payload:** Phase 4 built group-based campaigns (`groupIds[]`). For the MVP flat-contact model (D-08, no groups), the app must either (a) pass individual contact IDs if the API supports it, or (b) create a temporary single-use group per send. See Open Questions #3.

### Notifications (notification-service)

| Method | Endpoint | Auth | Response | Flutter Use |
|--------|----------|------|----------|-------------|
| GET | `/api/v1/notifications` | JWT | `Page<{id, type, message, read, createdAt}>` | In-app feed + unread badge; polled every N seconds (D-01) |

**Note:** The `NotificationDto` page is JWT-scoped by `userId`. The app polls this endpoint on a timer. No PATCH/mark-read endpoint was confirmed in Phase 5 summaries — see Open Questions #4.

---

## Architecture Patterns

### System Architecture Diagram

```
User Input
    │
    ▼
[Flutter App]
    │
    ├─ go_router (route guard reads AuthNotifier)
    │       │
    │       ├─ /onboarding  ← unauth
    │       ├─ /pending     ← PENDING_VERIFICATION (auto-polls via /auth/refresh)
    │       └─ /dashboard   ← VERIFIED
    │
    ├─ Riverpod Providers
    │       │
    │       ├─ AuthNotifier (reads JWT from flutter_secure_storage)
    │       ├─ BalanceProvider (GET /wallet/balance, Hive cache fallback)
    │       ├─ CampaignsProvider (GET /campaigns, Hive cache fallback)
    │       ├─ ContactsProvider (GET /contacts, Hive cache fallback)
    │       ├─ NotificationFeedProvider (polls GET /notifications)
    │       └─ PaymentProvider (POST /payments, poll status)
    │
    ├─ Dio (+ QueuedInterceptor)
    │       │
    │       ├─ Attach Bearer token (from flutter_secure_storage)
    │       ├─ On 401: POST /auth/refresh → store new tokens → retry
    │       └─ On refresh failure: clear tokens → AuthNotifier.signOut()
    │
    ├─ flutter_secure_storage  ← JWT access + refresh tokens
    └─ Hive CE boxes          ← read cache (balance, contacts, campaigns, feed)
    │
    ▼
[Backend Gateway]
    │
    ├─ identity-service    /auth/*
    ├─ wallet-service      /api/v1/wallet/*
    ├─ payment-service     /api/v1/payments, /api/v1/bundles
    ├─ contact-service     /api/v1/contacts
    ├─ messaging-service   /api/v1/campaigns
    └─ notification-svc    /api/v1/notifications
```

### Recommended Project Structure

```
apps/customer-app/
├── lib/
│   ├── main.dart                    # ProviderScope + MaterialApp.router
│   ├── l10n/                        # ARB files
│   │   ├── app_en.arb
│   │   └── app_sw.arb
│   ├── core/
│   │   ├── auth/                    # AuthNotifier, AuthState (freezed)
│   │   ├── dio/                     # DioClient, AuthInterceptor
│   │   ├── hive/                    # HiveBoxes init, typed adapters
│   │   ├── router/                  # AppRouter (go_router), redirect guards
│   │   └── l10n/                    # Generated AppLocalizations usage helpers
│   ├── features/
│   │   ├── onboarding/              # Splash, onboarding slides
│   │   ├── auth/                    # Register, NIDA pending, Login, Reset
│   │   ├── dashboard/               # DashboardScreen, BalanceWidget, RecentCampaigns
│   │   ├── payments/                # BundleCatalog, PurchaseScreen, CountdownWidget
│   │   ├── contacts/                # ContactListScreen, AddContactScreen
│   │   ├── campaigns/               # ComposerScreen, HistoryScreen, DetailScreen
│   │   └── notifications/           # NotificationFeedScreen, BadgeWidget
│   └── shared/
│       ├── widgets/                 # AppBar, BottomNav, ErrorBanner, LoadingOverlay
│       └── models/                  # Shared freezed models
├── test/
│   ├── features/                    # Widget tests per feature
│   └── core/                        # Unit tests for interceptor, auth logic
├── integration_test/
│   └── app_test.dart                # End-to-end flows
└── pubspec.yaml
```

### Pattern 1: Riverpod Auth State (Freezed sealed class)

```dart
// Source: riverpod.dev — AsyncNotifier pattern
@freezed
sealed class AuthState with _$AuthState {
  const factory AuthState.unauthenticated() = Unauthenticated;
  const factory AuthState.pending({required String accessToken}) = Pending;
  const factory AuthState.verified({
    required String accessToken,
    required String refreshToken,
  }) = Verified;
}

@riverpod
class AuthNotifier extends _$AuthNotifier {
  @override
  Future<AuthState> build() async {
    final token = await ref.read(secureStorageProvider).read(key: 'access_token');
    if (token == null) return const AuthState.unauthenticated();
    // Decode verification_status claim without network call
    final status = JwtDecoder.decode(token)['verification_status'];
    return status == 'VERIFIED'
        ? AuthState.verified(accessToken: token, refreshToken: '...')
        : AuthState.pending(accessToken: token);
  }
}
```

### Pattern 2: Dio QueuedInterceptor for JWT refresh-on-401

The Phase 2 refresh token ROTATES on every `POST /auth/refresh` call. This means concurrent 401s must be serialized — if two requests both hit 401 simultaneously and both try to refresh, the second refresh call will fail (the first one already consumed the refresh token). Use Dio's `QueuedInterceptor` to ensure only one refresh happens at a time, with other failed requests queued.

```dart
// Source: Dio documentation + community pattern
class AuthInterceptor extends QueuedInterceptor {
  final FlutterSecureStorage _storage;
  final Dio _tokenDio; // Separate Dio instance — NOT the same instance to avoid recursion

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode != 401) {
      return handler.next(err);
    }
    final refreshToken = await _storage.read(key: 'refresh_token');
    if (refreshToken == null) {
      // No refresh token — sign out
      return handler.reject(err);
    }
    try {
      final resp = await _tokenDio.post('/auth/refresh',
          data: {'refreshToken': refreshToken});
      final newAccess = resp.data['accessToken'];
      final newRefresh = resp.data['refreshToken'];
      await _storage.write(key: 'access_token', value: newAccess);
      await _storage.write(key: 'refresh_token', value: newRefresh);
      // Retry original request with new token
      err.requestOptions.headers['Authorization'] = 'Bearer $newAccess';
      final retryResp = await _tokenDio.fetch(err.requestOptions);
      return handler.resolve(retryResp);
    } catch (_) {
      // Refresh failed — clear tokens, redirect to login
      await _storage.deleteAll();
      return handler.reject(err);
    }
  }
}
```

**Critical:** Use a separate `Dio` instance (`_tokenDio`) for the refresh call. Using the same Dio instance would cause infinite recursion when the refresh call itself gets intercepted.

### Pattern 3: go_router redirect guards

```dart
// Source: go_router docs — redirect callback
GoRouter(
  refreshListenable: GoRouterRefreshStream(ref.watch(authNotifierProvider.stream)),
  redirect: (context, state) {
    final auth = ref.read(authNotifierProvider).valueOrNull;
    final isAuth = auth is Verified;
    final isPending = auth is Pending;
    final isOnAuth = state.matchedLocation.startsWith('/login') ||
                     state.matchedLocation.startsWith('/register');

    if (auth is Unauthenticated && !isOnAuth) return '/login';
    if (isPending && state.matchedLocation != '/pending') return '/pending';
    if (isAuth && isOnAuth) return '/dashboard';
    return null;
  },
);
```

### Pattern 4: NIDA PENDING auto-poll

```dart
// PENDING screen: poll POST /auth/refresh every 10 seconds
// The refresh response carries `status` field (Phase 2 implementation)
// On status == 'VERIFIED': AuthNotifier updates → go_router redirect fires → /dashboard
@riverpod
class PendingPollerNotifier extends _$PendingPollerNotifier {
  Timer? _timer;

  @override
  void build() {
    _timer = Timer.periodic(const Duration(seconds: 10), (_) async {
      await ref.read(authNotifierProvider.notifier).refreshAndCheckStatus();
    });
    ref.onDispose(() => _timer?.cancel());
  }
}
```

**Polling interval guidance:** 10 seconds for NIDA PENDING (Phase 2 stub resolves in 3 seconds dev; real NIDA is async). 30 seconds for notification feed (REQUIREMENTS.md: "30-second polling is sufficient for non-technical users"). 5 seconds for payment status during the 2-minute countdown.

### Pattern 5: Hive CE cache-read + online-write

```dart
// Three Hive boxes: one per cacheable entity
// Each provider checks Hive first on cold start, then fetches live
@riverpod
Future<BalanceResponse> balance(BalanceRef ref) async {
  final box = Hive.box<int>('balance');
  final cached = box.get('availableCredits');
  try {
    final live = await ref.read(walletApiProvider).getBalance();
    await box.put('availableCredits', live.availableCredits);
    return live;
  } catch (_) {
    if (cached != null) return BalanceResponse(availableCredits: cached);
    rethrow; // No cache + no network = hard error
  }
}
```

### Pattern 6: Flutter l10n (ARB + gen-l10n)

```yaml
# l10n.yaml at project root
arb-dir: lib/l10n
template-arb-file: app_en.arb
output-localization-file: app_localizations.dart
```

```json
// lib/l10n/app_en.arb
{
  "@@locale": "en",
  "registerTitle": "Create Account",
  "nidaPendingTitle": "Verifying your identity",
  "nidaPendingBody": "We are confirming your National ID. This usually takes a few minutes.",
  "balanceLabel": "SMS Balance",
  "sendCampaignButton": "Send Now"
}
```

```json
// lib/l10n/app_sw.arb  (Swahili)
{
  "@@locale": "sw",
  "registerTitle": "Fungua Akaunti",
  "nidaPendingTitle": "Tunathibitisha utambulisho wako",
  "nidaPendingBody": "Tunakagua Kitambulisho chako cha Taifa. Hii kawaida inachukua dakika chache.",
  "balanceLabel": "Salio la SMS",
  "sendCampaignButton": "Tuma Sasa"
}
```

**In-app language toggle:** Store selected locale in shared_preferences. Wrap MaterialApp in a `LocaleNotifierProvider` (Riverpod) and pass the selected locale to `MaterialApp.locale`. Default: device locale with fallback to `en`.

### Pattern 7: Azampay STK countdown UI

```dart
// CountdownTimer widget — no extra package needed
class CountdownWidget extends ConsumerStatefulWidget { ... }

class _CountdownWidgetState extends ConsumerState<CountdownWidget> {
  late Timer _timer;
  int _remaining = 120; // 2 minutes = 120 seconds (timeoutSeconds from API response)

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_remaining <= 0) {
        t.cancel();
        ref.read(paymentProvider.notifier).markExpired();
      } else {
        setState(() => _remaining--);
        // Poll payment status every 5 seconds
        if (_remaining % 5 == 0) {
          ref.read(paymentProvider.notifier).pollStatus();
        }
      }
    });
  }
}
```

**EXPIRED state:** When countdown reaches 0 or poll returns `status == "EXPIRED"`, show a clear error state with a "Try Again" button that navigates back to the bundle catalog. Do NOT auto-retry STK push (the user must consciously re-initiate).

### Pattern 8: SMS character counter (GSM-7 / UCS-2)

Client-side only — no backend call. Phase 4's messaging-service has `SmsEncoder` as a backend utility, but the Flutter app must implement the same logic independently:

- **GSM-7 charset:** 160 chars per part; multi-part at 153 chars per part
- **UCS-2 (any non-GSM char):** 70 chars per part; multi-part at 67 chars per part
- Non-GSM characters include Arabic, Chinese, Swahili-specific combining characters not in GSM-7 extended table
- Show: "120/160 · 1 SMS" or "⚠ Non-GSM characters detected: 68/70 · 1 SMS (UCS-2)"

```dart
// Pure Dart — no package needed
class SmsCounter {
  static const _gsm7Chars = r'@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ^{}\[~]|€ÆæßÉ !"#¤%&\'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà';

  static SmsCount count(String text) { ... }
}
```

### Anti-Patterns to Avoid

- **Storing JWT access/refresh tokens in Hive plain box:** Use `flutter_secure_storage` (platform Keychain/Keystore). Hive boxes are not encrypted by default.
- **Calling POST /auth/refresh in the main Dio interceptor with the same Dio instance:** Causes infinite recursion on 401. Always use a separate `_tokenDio` instance for token refresh.
- **Polling NIDA status by decoding the access JWT claim without refreshing:** The claim is stale until the next refresh. The only way to get an updated `verification_status` is to call `POST /auth/refresh`. Decoding the current JWT just reads the old claim.
- **Using `Timer.periodic` in a Riverpod provider without `ref.onDispose`:** Timers leak across hot-restarts and widget rebuilds. Always cancel in `ref.onDispose`.
- **Hardcoding UI strings in any widget:** Every user-facing string must come from `AppLocalizations.of(context)!` (D-04 — Swahili from day one, structural requirement).
- **Starting the 2-minute countdown on the client independently of the server timeout:** The server's `timeoutSeconds` field in the POST /api/v1/payments response is the authoritative value (120). Use it to seed the countdown, not a hardcoded constant.
- **Using go_router redirect without a `refreshListenable`:** Without it, the redirect callback does not fire when auth state changes asynchronously (e.g., after a NIDA status poll). Use `GoRouterRefreshStream` wrapping the auth Riverpod stream.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT decode (read claims) | Custom base64 parser | `dart-jsonwebtoken` or just `base64.decode(parts[1])` | JWT format is standard; decoding is trivial but easy to mis-implement padding |
| HTTP retry on 401 | Manual retry loop | Dio `QueuedInterceptor` | Handles concurrent 401s safely; prevents double-refresh of rotating tokens |
| Immutable models with copyWith | Manual class | Freezed | Freezed handles equality, hashCode, toString, sealed unions — critical for Riverpod state comparison |
| JSON serialization | Manual toJson/fromJson | json_serializable | Manual serialization is error-prone; codegen is zero-maintenance |
| Platform secure storage | Hive encrypted box | flutter_secure_storage | Platform Keychain/Keystore is the correct layer for credentials; hive encryption requires manual key management |
| Locale persistence | Custom file | shared_preferences | Simple KV for `selected_locale` string; no need for Hive here |

---

## Common Pitfalls

### Pitfall 1: Rotating refresh token consumed twice on concurrent 401s
**What goes wrong:** Two requests fire simultaneously, both return 401, both try to refresh. The first succeeds; the second fails because the refresh token was already rotated.
**Why it happens:** Phase 2 refresh tokens rotate on every use (reuse detection). A second call with the old refresh token returns an error and revokes the session.
**How to avoid:** Use `QueuedInterceptor` which serializes the error handler — only one refresh fires; subsequent errors in the queue are retried with the already-refreshed token.
**Warning signs:** Intermittent "Invalid refresh token" errors, especially on app startup when multiple providers all fire simultaneous requests.

### Pitfall 2: go_router redirect not firing after async auth state change
**What goes wrong:** NIDA PENDING poll succeeds (VERIFIED), but the user stays on the PENDING screen.
**Why it happens:** go_router's redirect callback only re-evaluates when `refreshListenable` fires. If the auth Riverpod notifier updates but nothing notifies go_router, the redirect never fires.
**How to avoid:** Wrap `ref.watch(authNotifierProvider.stream)` in a `GoRouterRefreshStream` and pass it to `GoRouter(refreshListenable: ...)`.
**Warning signs:** Auth state changes in Riverpod devtools but screen does not navigate.

### Pitfall 3: PENDING screen shows stale "not verified" state after NIDA approves
**What goes wrong:** User receives NIDA verification, but the app still shows PENDING because the access JWT still carries `PENDING_VERIFICATION` claim (15-min TTL).
**Why it happens:** The `verification_status` claim in the access JWT is a 15-minute snapshot. The only way to get `VERIFIED` is to call `POST /auth/refresh`.
**How to avoid:** The PENDING poll MUST call `POST /auth/refresh`, not just decode the current access JWT. The `status` field in the refresh response reflects the DB truth.

### Pitfall 4: hive_flutter vs hive_ce_flutter confusion
**What goes wrong:** Importing `hive_flutter` 1.1.0 (published 5 years ago, no updates) causes compatibility issues with Flutter 3.41.
**Why it happens:** The original `hive` + `hive_flutter` packages from the HiveDB author are abandoned. The community fork `hive_ce` / `hive_ce_flutter` is the maintained path.
**How to avoid:** Use `hive_ce: ^2.9.0` and `hive_ce_flutter: ^2.3.4`. Do NOT add `hive` or `hive_flutter` to pubspec.yaml.

### Pitfall 5: Campaign send fails because Phase 4 API expects groupIds[]
**What goes wrong:** The MVP app sends a campaign but has no groups (D-08 — flat contacts only), so `groupIds: []` results in zero recipients and the backend rejects with 400 or queues an empty campaign.
**Why it happens:** Phase 4's campaign system was designed for group-based targeting. The Flutter app has individual contacts, not groups.
**How to avoid:** See Open Questions #3. The planner must resolve this before the campaign composer can be built.

### Pitfall 6: `integration_test` imported from pub.dev instead of SDK
**What goes wrong:** `pub.dev` package `integration_test` is deprecated and pinned to old Flutter versions.
**Why it happens:** Old tutorials reference the pub.dev package.
**How to avoid:** Always use `integration_test: sdk: flutter` in dev_dependencies.

### Pitfall 7: TZS amounts displayed with decimal places
**What goes wrong:** Backend stores `priceTzs` as `long` (integer TZS). If the Flutter app casts to `double` and displays with `.00`, it looks wrong for a TZ context.
**Why it happens:** JSON decoding of a long integer into Dart's `double` type or using `NumberFormat.currency()` with decimal digits.
**How to avoid:** Use `NumberFormat.decimalPattern('sw_TZ')` or custom formatter that displays TZS as integer (e.g., "TZS 5,000").

---

## Code Examples

### Hive CE Initialization (main.dart)

```dart
// Source: github.com/IO-Design-Team/hive_ce docs
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  // Register adapters for typed boxes
  Hive.registerAdapter(CachedContactAdapter());
  Hive.registerAdapter(CachedCampaignAdapter());
  // Open boxes
  await Hive.openBox<CachedContact>('contacts');
  await Hive.openBox<CachedCampaign>('campaigns');
  await Hive.openBox<int>('balance');
  await Hive.openBox<Map>('notifications');

  runApp(ProviderScope(child: const MyApp()));
}
```

### l10n.yaml

```yaml
# l10n.yaml — project root
arb-dir: lib/l10n
template-arb-file: app_en.arb
output-localization-file: app_localizations.dart
nullable-getter: false
```

### Widget Test with mock Dio

```dart
// Source: mocktail pub.dev + flutter_test SDK
void main() {
  late MockDio mockDio;

  setUp(() {
    mockDio = MockDio();
  });

  testWidgets('Dashboard shows balance from API', (tester) async {
    when(() => mockDio.get('/api/v1/wallet/balance'))
        .thenAnswer((_) async => Response(
              data: {'availableCredits': 150},
              statusCode: 200,
              requestOptions: RequestOptions(path: ''),
            ));

    await tester.pumpWidget(
      ProviderScope(
        overrides: [dioProvider.overrideWithValue(mockDio)],
        child: const MaterialApp(home: DashboardScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('150'), findsOneWidget);
  });
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| hive + hive_flutter | hive_ce + hive_ce_flutter | 2023 (fork) | hive_flutter is abandoned; hive_ce is the maintained community fork |
| Riverpod 1.x/2.x (manual providers) | Riverpod 3.x (code-generated via @riverpod) | 2024-2025 | Less boilerplate; better type safety; breaking changes from 2.x |
| go_router 6-10.x patterns | go_router 17.x (stateful navigation, shell routes) | 2024-2025 | ShellRoute for bottom nav; StatefulShellRoute for nested nav; redirect callback API stable |
| flutter_test mockito (codegen mocks) | mocktail (no codegen) | 2021+ | mocktail uses Mockito-like API without build_runner; simpler for Flutter widget tests |
| integration_test from pub.dev | integration_test: sdk: flutter | Flutter 2.0 | Bundled in SDK; pub.dev package is deprecated |
| Pages Router (old Flutter nav 1.0) | go_router (declarative) | 2022+ | go_router is the Flutter team's recommended solution since Navigator 2.0 |

**Deprecated/outdated:**
- `hive` + `hive_flutter`: abandoned by original author; replaced by `hive_ce`
- `provider` package: superseded by Riverpod for new projects
- `flutter_bloc` is still valid but not the locked choice (D-07 locks Riverpod)
- `dio_http_cache`: old caching interceptor; Hive-based cache (D-05) is the project's approach
- `integration_test` from pub.dev (version 1.0.2+3): use SDK variant

---

## Validation Architecture

**Nyquist note:** All MOBL requirements need verification. The mix of widget tests, integration tests, and manual store checks maps as follows.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | flutter_test (SDK bundled) + integration_test (SDK bundled) |
| Config file | none — `flutter test` discovers tests in `test/` and `integration_test/` |
| Quick run command | `flutter test test/` |
| Full suite command | `flutter test test/ && flutter test integration_test/` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| MOBL-01 | Splash screen renders; onboarding flow navigates to register | widget test | `flutter test test/features/onboarding/` | Assert screen text + navigation |
| MOBL-02 | Registration → PENDING state shown; poll detects VERIFIED → navigate to dashboard | widget test + integration test | `flutter test test/features/auth/` | Mock Dio for PENDING→VERIFIED transition |
| MOBL-03 | Login → JWT stored in secure storage; second launch reads token; Dio attaches Bearer header | widget test (unit for interceptor) | `flutter test test/core/dio/` | Test interceptor with mock Dio + mock SecureStorage |
| MOBL-03 | Refresh-on-401 rotates token and retries request | unit test | `flutter test test/core/dio/auth_interceptor_test.dart` | |
| MOBL-04 | Dashboard shows balance, recent campaigns (≤5), quick-send shortcut | widget test | `flutter test test/features/dashboard/` | Mock balance=150, mock 3 campaigns |
| MOBL-04 | Dashboard shows cached balance when offline | widget test | `flutter test test/features/dashboard/` | Pre-seed Hive box; mock Dio throws NetworkException |
| MOBL-05 | Bundle catalog renders all active bundles | widget test | `flutter test test/features/payments/` | Mock GET /bundles response |
| MOBL-05 | 2-minute countdown displays and decrements | widget test | `flutter test test/features/payments/` | Use `tester.pump(Duration(seconds: 5))` to advance timer |
| MOBL-05 | EXPIRED state shown when countdown reaches 0 or poll returns EXPIRED | widget test | `flutter test test/features/payments/` | Mock poll returning EXPIRED |
| MOBL-06 | Contact list shows cached contacts when offline | widget test | `flutter test test/features/contacts/` | Pre-seed Hive contacts box |
| MOBL-06 | Add contact (online) appears in list and cache | widget test | `flutter test test/features/contacts/` | Mock POST /contacts; verify Hive updated |
| MOBL-07 | Campaign composer shows character count (GSM-7 + UCS-2 mode) | widget test | `flutter test test/features/campaigns/` | Enter GSM-7 text → assert "160/160"; enter emoji → assert UCS-2 warning |
| MOBL-07 | Send campaign online-only; error state shown when offline | widget test | `flutter test test/features/campaigns/` | Mock Dio throws; assert error banner |
| MOBL-08 | Campaign history list shows paginated campaigns | widget test | `flutter test test/features/campaigns/` | Mock GET /campaigns page |
| MOBL-08 | Campaign detail shows aggregate counts + per-message status | widget test | `flutter test test/features/campaigns/` | Mock GET /campaigns/{id} + messages |
| MOBL-09 | Signed AAB and IPA build successfully | CI build (manual verify) | `flutter build appbundle --release` + `flutter build ipa --release` | Requires signing config |
| MOBL-09 | Store metadata (icon, screenshots, descriptions, privacy URL) is complete | manual store check | — | Verify in Play Console + App Store Connect |
| MOBL-09 | App submitted to both stores | manual | — | Confirm submission receipt emails |
| D-04 (Swahili) | All visible strings resolve from ARB; no hardcoded strings | `flutter_test` + static analysis | `flutter analyze` + widget test with `sw` locale | Set locale to `sw` in test; assert key strings appear |
| D-09 (PENDING wall) | PENDING user cannot navigate to campaign composer | widget test | `flutter test test/core/router/` | Mount router with PENDING auth state; attempt /campaigns navigation; assert redirect to /pending |

### Sampling Rate

- **Per task commit:** `flutter test test/` (unit + widget, ~15–30 seconds)
- **Per wave merge:** `flutter test test/ && flutter test integration_test/` (full including device tests)
- **Phase gate:** Full suite green + manual store check for MOBL-09 before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `test/core/dio/auth_interceptor_test.dart` — covers MOBL-03 (interceptor unit tests)
- [ ] `test/core/router/redirect_guard_test.dart` — covers D-09 (PENDING wall)
- [ ] `test/features/onboarding/` — covers MOBL-01
- [ ] `test/features/auth/` — covers MOBL-02, MOBL-03
- [ ] `test/features/dashboard/` — covers MOBL-04
- [ ] `test/features/payments/` — covers MOBL-05
- [ ] `test/features/contacts/` — covers MOBL-06
- [ ] `test/features/campaigns/` — covers MOBL-07, MOBL-08
- [ ] `integration_test/app_test.dart` — end-to-end happy path

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT access token (15-min) + rotating refresh (7-day); stored in flutter_secure_storage |
| V3 Session Management | yes | Refresh rotation on every use; on 401-after-failed-refresh, clear all tokens and navigate to login |
| V4 Access Control | yes | go_router redirect guards enforce walled state; PENDING cannot reach campaign composer |
| V5 Input Validation | yes | flutter_form_builder / Form validators for register (email, phone, NIN), add contact, campaign message |
| V6 Cryptography | no | JWT is validated server-side; client only decodes (base64, no cryptographic verification needed client-side) |

### Known Threat Patterns for Flutter

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JWT in Hive plain box | Information Disclosure | Use flutter_secure_storage for access + refresh tokens |
| Dio instance reused for token refresh (recursion) | Denial of Service | Separate Dio instance for token refresh calls |
| Concurrent 401s consuming rotating refresh token | Spoofing | QueuedInterceptor serializes refresh; only one refresh fires |
| Hardcoded API base URL in code | Tampering | Use `--dart-define=API_BASE_URL=...` at build time; never commit prod URL to source |
| Screenshot/recent-apps screen capture of sensitive balance | Information Disclosure | Set `FLAG_SECURE` on Android via `flutter_windowmanager` package if required (MVP: accept risk) |

---

## Store Submission Requirements (MOBL-09)

### Android (Google Play)

- **Build:** `flutter build appbundle --release` → `.aab` file (preferred over APK for Play Store)
- **Signing:** `key.jks` keystore + `key.properties` (never commit to git); configure in `android/app/build.gradle`
- **Target SDK:** Android 14+ (API 34) required by Google Play as of Aug 2024
- **Metadata required:** App icon (512×512 PNG), feature graphic (1024×500), min 2 screenshots per form factor, short description (80 chars), full description (4000 chars), privacy policy URL
- **Privacy policy URL:** Required (NIDA/payment data collected) — host at a static URL before submission
- **Content rating:** Fill out the IARC questionnaire in Play Console

### iOS (Apple App Store)

- **Build:** `flutter build ipa --release` (requires macOS + Xcode)
- **Signing:** Distribution certificate + provisioning profile from Apple Developer Program (D-03 prerequisite)
- **Minimum iOS:** 12.0+ (Flutter 3.41 drops iOS 11)
- **Metadata required:** App icon (1024×1024 PNG), min 3 screenshots per device size (6.5", 5.5", iPad optional), App Store description, keywords (100 chars), privacy policy URL, age rating
- **Privacy nutrition labels:** Declare data collected (phone number, email, NIN, payment data) in App Store Connect before submission
- **TestFlight:** Submit to TestFlight first for internal testing before App Store review

### Shared

- **App name:** Consistent across both stores
- **Bundle ID / Application ID:** Set once in `pubspec.yaml` (build_name, build_number) and propagate to both platform configs
- **Swahili in store listing:** Provide store description in Swahili as an additional language listing (supports Tanzania App Store/Play localization)

---

## Open Questions

1. **NIDA PENDING poll endpoint (CRITICAL — blocks Wave 1 auth planning)**
   - What we know: Phase 2 built `POST /auth/refresh` which re-reads user status from DB and returns `status` in the response. The PENDING screen can poll via refresh, but this rotates the refresh token on every poll call. With a 10-second poll interval and a 7-day refresh TTL, this is ~60,000 rotations over a 7-day window — functionally fine but generates significant Redis churn.
   - What's unclear: Whether a lightweight `GET /auth/me` endpoint (returns `{userId, status}` without token rotation) was built or is needed. No such endpoint appeared in the Phase 2 summaries.
   - Recommendation: Add `GET /auth/me` to identity-service in Phase 6 Wave 0 (the app is a client of the same system; this is a legitimate thin addition). Alternatively, accept polling via refresh and document the Redis key churn.

2. **Single payment status endpoint (CRITICAL — blocks Wave 2 payment countdown)**
   - What we know: `POST /api/v1/payments` returns `{id, status:"PENDING", timeoutSeconds:120}`. The app must poll to detect CONFIRMED or EXPIRED during the countdown.
   - What's unclear: Whether `GET /api/v1/payments/{id}` (single record) exists. The summaries show `GET /api/v1/payments` (list, paginated) but not a single-record endpoint. Polling the paginated list and filtering by id is workable but wasteful.
   - Recommendation: Confirm whether `PaymentController` in payment-service has a `GET /api/v1/payments/{id}` handler. If not, add it in Phase 6 Wave 0 as a thin backend addition (single-record lookup by payment UUID).

3. **Campaign targeting: group-based API vs flat-contact MVP (CRITICAL — blocks Wave 3 campaign composer)**
   - What we know: Phase 4's `POST /api/v1/campaigns` takes `groupIds[]`. The Flutter MVP has no groups (D-08). The app has individual contacts.
   - What's unclear: Whether the campaign API supports targeting by `contactIds[]` directly, or whether the backend requires groups. If groups are required, the app must create a temporary group per campaign send or the backend needs a `contactIds[]` variant.
   - Recommendation: Check Phase 4 `CampaignController` / `CreateCampaignRequest` for a `contactIds[]` field. If absent, the simplest fix is to add `contactIds[]` to `CreateCampaignRequest` in messaging-service (Phase 6 Wave 0 backend task). This avoids the "temp group" hack.

4. **Notification mark-as-read endpoint**
   - What we know: `GET /api/v1/notifications` returns a feed with a `read` boolean. Phase 5 summaries do not mention a PATCH or PUT endpoint to mark notifications as read.
   - What's unclear: Whether `PATCH /api/v1/notifications/{id}/read` was built or needs to be added.
   - Recommendation: For MVP polling-only model (D-01), marking individual notifications as read is a nice-to-have. The unread badge can be derived client-side as `count(read == false)`. Accept the gap for MVP; if mark-read is needed, add a `PATCH /api/v1/notifications/{id}/read` to notification-service.

5. **iOS build machine (MOBL-09)**
   - What we know: `flutter build ipa` requires macOS + Xcode. D-02 requires IPA submission.
   - What's unclear: Whether the CI/CD pipeline (GitHub Actions) has a macOS runner configured. Phase 1 CI was set up for the backend (Linux containers); iOS builds need `runs-on: macos-latest`.
   - Recommendation: Update GitHub Actions CI in Phase 6 to add a macOS build job for `flutter build ipa`. Confirm Apple Developer account exists (D-03 prerequisite).

6. **Riverpod 3.x breaking changes from training data**
   - What we know: Riverpod 3.3.2 is current. The code-gen API (`@riverpod` annotation) is stable. Riverpod 2.x patterns (manual `Provider(...)` constructors) are deprecated.
   - What's unclear: Exact list of Riverpod 3.x breaking changes vs 2.x (training knowledge is [ASSUMED]).
   - Recommendation: Wave 0 planner should consult `riverpod.dev/docs/whats_new` before writing provider structure. Do not copy Riverpod 2.x examples from online tutorials.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Flutter SDK | All mobile builds | ✓ | 3.41.5 stable (Dart 3.11.3) [VERIFIED] | — |
| Xcode | iOS IPA build | macOS CI runner required | 15+ | Linux runner cannot build iOS |
| Android Studio / SDK tools | AAB build | Standard CI | Latest | Available on Linux CI |
| Apple Developer account | iOS submission | External prerequisite (D-03) | N/A | Phase blocked on this for MOBL-09 |
| Google Play Console account | Android submission | External prerequisite (D-03) | N/A | Phase blocked on this for MOBL-09 |

**Missing dependencies with no fallback:**
- Apple Developer Program account + Distribution Certificate (must exist before `flutter build ipa --release` is submittable — D-03 prerequisite)
- macOS CI runner for iOS builds

**Missing dependencies with fallback:**
- Flutter SDK not installed in dev environment — install from flutter.dev before Wave 0

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ~~Dart version bundled with Flutter 3.41 is Dart 3.7~~ — RESOLVED: Dart 3.11.3 verified via `dart --version` | Standard Stack | n/a — verified |
| A2 | `hive_ce` package name and API are equivalent to `hive` with breaking changes only in adapters | Standard Stack | Medium — if API surface differs significantly, code examples need adjustment |
| A3 | `freezed_annotation` version 3.0.0 is compatible with `freezed` 3.2.5 | Standard Stack | Low — companion packages are versioned together |
| A4 | `json_serializable ^6.x` and `json_annotation ^4.x` are current and compatible | Standard Stack | Low — well-established, slow-moving packages |
| A5 | `riverpod_generator` version matches `riverpod_annotation ^4.0.3` | Standard Stack | Medium — must match exactly; check pub.dev before installing |
| A6 | `mocktail ^1.x` is compatible with Flutter 3.41 / Dart 3.7 | Standard Stack | Low — mocktail is actively maintained |
| A7 | Phase 2 `POST /auth/refresh` response body includes a `status` field (not just a new JWT) | Backend API Contract | HIGH RISK — if `status` is only embedded in the JWT claim and not in the response body, the PENDING poll strategy changes. The Phase 2 LoginIT summary mentions `TokenResponse{accessToken, refreshToken}` without explicitly listing `status` as a top-level field |
| A8 | Campaign history list endpoint supports `?page=0&size=5` pagination | Backend API Contract | Low — standard Spring Data pageable pattern confirmed in other endpoints |
| A9 | `GoRouterRefreshStream` helper class exists in go_router 17.x | Architecture Patterns | Medium — API may have changed; verify in go_router 17.x docs |
| A10 | Riverpod 3.x `GoRouterRefreshStream` integration pattern works as described | Architecture Patterns | Medium — training knowledge; verify against go_router 17.x + Riverpod 3.x docs |

---

## Sources

### Primary (HIGH confidence)
- [pub.dev/packages/flutter_riverpod](https://pub.dev/packages/flutter_riverpod) — version 3.3.2 confirmed
- [pub.dev/packages/riverpod_annotation](https://pub.dev/packages/riverpod_annotation) — version 4.0.3 confirmed
- [pub.dev/packages/dio](https://pub.dev/packages/dio) — version 5.9.2 confirmed
- [pub.dev/packages/go_router](https://pub.dev/packages/go_router) — version 17.3.0 confirmed
- [pub.dev/packages/hive_ce_flutter](https://pub.dev/packages/hive_ce_flutter) — version 2.3.4 confirmed
- [pub.dev/packages/hive_flutter](https://pub.dev/packages/hive_flutter) — 1.1.0, 5 years stale, confirmed abandoned
- [pub.dev/packages/shared_preferences](https://pub.dev/packages/shared_preferences) — version 2.5.5 confirmed
- [pub.dev/packages/flutter_secure_storage](https://pub.dev/packages/flutter_secure_storage) — version 10.3.1 confirmed
- [pub.dev/packages/freezed](https://pub.dev/packages/freezed) — version 3.2.5 confirmed
- [pub.dev/packages/build_runner](https://pub.dev/packages/build_runner) — version 2.15.0 confirmed
- [pub.dev/packages/integration_test](https://pub.dev/packages/integration_test) — confirmed deprecated; use sdk: flutter
- [docs.flutter.dev/ui/internationalization](https://docs.flutter.dev/ui/internationalization) — ARB l10n setup
- Phase 2–5 SUMMARY.md files — backend API endpoint contracts (authenticated reads of project files)

### Secondary (MEDIUM confidence)
- [riverpod.dev/docs/whats_new](https://riverpod.dev/docs/whats_new) — Riverpod 3.0 breaking changes [referenced via WebSearch]
- [dev.to/7twilight/mastering-auth-in-flutter-with-dio](https://dev.to/7twilight/mastering-auth-in-flutter-with-dio-from-simple-access-tokens-to-a-refresh-flow-27cf) — QueuedInterceptor refresh pattern

### Tertiary (LOW confidence / ASSUMED)
- Flutter 3.41 bundled Dart version: [ASSUMED]
- `riverpod_generator` version compatibility: [ASSUMED]
- `GoRouterRefreshStream` API in go_router 17.x: [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Standard stack (versions): HIGH — all package versions verified against pub.dev registry
- Backend API contract: MEDIUM-HIGH — derived from Phase 2–5 SUMMARY.md files; two gaps flagged (Open Questions #1 and #2)
- Architecture patterns: MEDIUM — Riverpod 3.x + go_router 17.x patterns based on documentation references; specific API surface needs Wave 0 validation
- Store submission: HIGH — Google Play + Apple App Store requirements are stable and well-documented

**Research date:** 2026-06-22
**Valid until:** 2026-07-22 (30 days — Flutter/package ecosystem; store submission requirements are stable longer)
