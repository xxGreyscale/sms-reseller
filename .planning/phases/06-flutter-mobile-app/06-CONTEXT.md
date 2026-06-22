# Phase 6: Flutter Mobile App - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

The complete Flutter customer app covering the full journey: splash + onboarding → NIDA
registration + PENDING verification (auto-polling) → login with persisted session → dashboard
(balance, recent campaigns, quick-send) → bundle purchase via Azampay (STK push + 2-min
countdown) → flat contact list (manual add) → immediate-send campaign composer → campaign
history + detail → submitted to Google Play + Apple App Store with full metadata. Single
codebase (iOS + Android). Second/last frontend phase (UI hint: yes → UI-SPEC required).

**Requirements:** MOBL-01..09.

**Not in this phase (deferred — MOBL-V2):** contact groups + group management, CSV import,
campaign scheduling, full analytics dashboard. Also deferred: real FCM push (fast-follow),
real Azampay/NIDA production credentials (external Phase-0 dependency — app builds against the
existing backend stubs/live APIs as available).
</domain>

<decisions>
## Implementation Decisions

### Push notifications
- **D-01:** MVP ships **in-app notification-feed polling, NOT real FCM**. The app consumes the
  existing `GET /api/v1/notifications` feed (notification list + unread badge) and polls for NIDA
  status + balance updates (which the success criteria require regardless). **Real FCM (device
  tokens + RealPushChannel + Firebase/APNs) is deferred to a post-launch fast-follow** — the
  Phase 5 `StubPushChannel` seam stays ready. Keeps Firebase/APNs cert setup off the launch path.

### Store publishing (MOBL-09)
- **D-02:** Deliverable = **signed release builds (Android AAB + iOS IPA) + full store metadata
  (icon, screenshots, privacy policy, store descriptions) + submission to both stores.** Actual
  "live/approved" is gated by external review queues and Apple/Google developer accounts — treated
  as an **external prerequisite (Phase-0-style), not a blocker for phase completion.** Phase is
  "done" when builds are submitted and submission-ready; track approval separately.
- **D-03 (prerequisite flag):** Apple Developer Program + Google Play Console accounts must exist
  (and signing keys/certs provisioned) before submission — surface as an external dependency, like
  NIDA/Azampay merchant onboarding.

### Localization
- **D-04:** **Bilingual English + Swahili from day one.** Flutter l10n (ARB files), device-locale
  aware with an in-app language toggle. Swahili is core to the Tanzania positioning + trust moat;
  every screen sources copy from l10n (no hardcoded UI strings) so this is structural, not a
  retrofit. English is the development/source locale; Swahili is a full translation.

### Offline / connectivity + navigation
- **D-05:** **Cache-read + online-write.** Cache last-known dashboard/balance/contacts/campaign-
  history in Hive for read so the app opens usefully on poor/no connection; ALL writes (purchase,
  campaign send, add contact) are **online-only with explicit retry + error states** — no silent
  offline queue (avoids sending campaigns the user thought were drafts).
- **D-06:** Navigation via **go_router** (declarative routes + auth redirect guards: unauth →
  login/onboarding; PENDING → walled state).

### Locked stack (PROJECT.md / roadmap)
- **D-07:** Flutter (single codebase), **Riverpod** state management, **Dio** HTTP client,
  **Hive + shared_preferences** storage. JWT persisted in Hive (MOBL-03); session survives restart.
- **D-08:** Flat contact list with manual add only (no groups/CSV — MOBL-06); immediate-send only
  (no scheduling — MOBL-07). STK push 2-minute countdown UI (MOBL-05).

### NIDA PENDING / walled state (carry-forward from Phase 2 D-01)
- **D-09:** Honor "logged in, but walled": a PENDING-verification user can log in and see the
  dashboard but **cannot send campaigns until VERIFIED**. The PENDING screen **auto-polls** the
  identity status endpoint and transitions to the full dashboard on VERIFIED (then the 50 free
  credits appear via the wallet balance). go_router redirect enforces the wall.

### Resolved from research — verified stack corrections (2026-06-22)
- **D-10:** Stack version specifics (verified on this machine, Flutter 3.41.5 / Dart 3.11.3):
  use **`hive_ce_flutter`** (maintained community fork) — NOT the abandoned `hive_flutter`;
  **go_router 17.x**, **Riverpod 3.x with mandatory `@riverpod` code-gen**. Store **JWT access +
  refresh tokens in `flutter_secure_storage`** (Keychain/Keystore), NOT a plain Hive box; Hive is
  for the read-cache (D-05) only. The Dio refresh-on-401 handler MUST use **`QueuedInterceptor`**
  (serializes concurrent 401s so only one refresh fires against Phase 2's rotating refresh tokens).

### Resolved from research — BACKEND GAPS this phase must close (confirmed against code, 2026-06-22)
This is a client phase, but the app needs four small backend additions that do NOT exist yet —
each is in Phase 6 scope and the planner MUST create explicit backend tasks for them:
- **D-11:** **`GET /api/v1/payments/{id}`** — payment-service has only the list endpoint; the
  2-minute STK countdown screen needs single-payment status polling (MOBL-05). Add it (ADMIN/owner
  JWT-scoped).
- **D-12:** **Campaign targeting by `contactIds`** — `CreateCampaignRequest` accepts only
  `groupIds[]`, but the Flutter MVP has flat contacts/no groups (D-08). Add a `contactIds[]` path
  to campaign creation (or an implicit single-group expansion) so a flat-contact campaign can send
  (MOBL-07). Planner picks the cleaner of the two; prefer extending the request contract.
- **D-13:** **Lightweight verification-status read** — identity has no `GET /auth/me|status`; the
  NIDA PENDING auto-poll (D-09) would otherwise hit `/auth/refresh` and rotate the refresh token
  on every poll. Add a thin authenticated `GET /auth/me` (or `/auth/status`) returning current
  `verification_status` + balance summary, so polling is cheap and non-rotating (MOBL-02).
- **D-14 (optional/nice-to-have):** notification mark-as-read (`PATCH /api/v1/notifications/{id}/read`)
  for the in-app feed badge — include only if it fits; the feed read API already exists.

### Claude's Discretion
- Exact Riverpod provider structure, Dio interceptor design (JWT attach + QueuedInterceptor
  refresh-on-401), Hive read-cache box layout, go_router route tree, polling intervals
  (NIDA status, balance, feed), l10n key naming, and theming/design-token mapping from the UI-SPEC.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend APIs the app consumes (built in Phases 2–5)
- `.planning/phases/02-identity-auth/*-SUMMARY.md` — registration, async NIDA verify + PENDING
  status endpoint (auto-poll), login, **rotating refresh tokens** (Dio refresh-on-401), logout.
- `.planning/phases/03-wallet-payments/*-SUMMARY.md` — bundle catalog read, Azampay purchase
  initiation + 2-min EXPIRED state, wallet balance + transaction history (MOBL-04/05).
- `.planning/phases/04-contacts-messaging/*-SUMMARY.md` — contact CRUD (flat add), campaign
  send (reserve→QUEUED), campaign history + per-message status (MOBL-06/07/08).
- `.planning/phases/05-notifications-admin-analytics/05-06-SUMMARY.md` — `GET /api/v1/notifications`
  feed API (D-01 polling); StubPushChannel seam for the deferred FCM fast-follow.

### Stack & product
- `.planning/PROJECT.md` §"Customer frontend: Flutter" — locked stack (Riverpod/Dio/Hive),
  Swahili-aware UX, MVP scope; §"Out of Scope" — MOBL-V2 deferrals.
- `.planning/ROADMAP.md` §"Phase 6" — goal, 5 success criteria, MOBL-01..09.
- `.planning/REQUIREMENTS.md` — MOBL-01..09 acceptance criteria + MOBL-V2-01..04 (deferred).
- `CLAUDE.md` — Tanzania context (TZS, Swahili UTF-8), locked tech stack line.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **All backend APIs already exist** (Phases 2–5) — this phase is a pure client. No backend code
  except possibly a thin device-token endpoint IF FCM were built (it is NOT — D-01).
- **Auth contract**: 15-min access JWT + 7-day rotating refresh (Phase 2) — the Dio interceptor
  must rotate on 401 and persist the new refresh token to Hive.
- **Azampay STK contract**: Phase 3 purchase initiation + EXPIRED-after-2-min state — the
  countdown UI mirrors that timer; balance refresh after PaymentConfirmed.

### Established Patterns
- NEW stack — no Flutter code exists yet. Wave 0 establishes the Flutter project, l10n harness,
  Riverpod/Dio/Hive wiring, go_router, and the test harness (flutter_test widget tests +
  integration_test; mock Dio for API tests). This is the project's first mobile app.

### Integration Points
- REST (Dio) to all backend services via the gateway; JWT in Authorization header from Hive.
- Polling: NIDA status (PENDING screen), wallet balance (post-purchase + dashboard), notification
  feed. No websockets/push at MVP (D-01).

</code_context>

<specifics>
## Specific Ideas

- Likely build order: Wave 0 Flutter scaffold + l10n + Riverpod/Dio/Hive + go_router + test harness
  → auth (onboarding/register/NIDA-pending-poll/login/session) → dashboard + balance →
  purchase (Azampay + countdown) → contacts + campaign composer + history → store metadata/build/
  submit. The NIDA register→PENDING-poll→VERIFIED flow is the spine (proves the whole auth + walled
  state + polling stack end-to-end).
- l10n and the Dio auth interceptor are cross-cutting — establish both in Wave 0 so every later
  screen builds on them.

</specifics>

<deferred>
## Deferred Ideas

- **Real FCM push** (device tokens + RealPushChannel + Firebase/APNs) — post-launch fast-follow (D-01).
- **Contact groups + group management** (MOBL-V2-01), **CSV import** (MOBL-V2-02), **campaign
  scheduling** (MOBL-V2-03), **full analytics dashboard** (MOBL-V2-04) — all V2 per REQUIREMENTS.md.
- **Offline-write sync / queue** — explicitly out (D-05 is cache-read + online-write only).

</deferred>

---

*Phase: 6-Flutter Mobile App*
*Context gathered: 2026-06-22*
