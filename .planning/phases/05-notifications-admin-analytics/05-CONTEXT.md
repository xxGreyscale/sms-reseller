# Phase 5: Notifications, Admin & Analytics - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Three deliverables: (1) a `notification-service` that consumes domain events from all prior
services and writes an idempotent in-app notification log + feed API (NOTF-01..06); (2) the
Next.js `admin-web` panel + supporting `admin` backend giving operators full platform visibility
and control (ADMN-01..07); (3) user-facing delivery/spend analytics as backend read APIs
(ANLX-01..03). First frontend phase (UI hint: yes → UI-SPEC required).

**Requirements:** NOTF-01..06, ADMN-01..07, ANLX-01..03.

**Not in this phase:** Real push delivery / FCM (Phase 6, when the Flutter app can receive it);
the customer-facing Flutter UI that renders analytics + notifications (Phase 6). No new customer
features — this phase observes, administers, and reports on what Phases 2–4 built.
</domain>

<decisions>
## Implementation Decisions

### Admin authentication & accounts
- **D-01:** **identity-service issues admin JWTs** — reuse the Phase 2 RSA JWT machinery (same
  key shared-security already validates). Add an admin login path that mints a token carrying
  `roles:[ADMIN]`. No separate admin-service token issuer. This satisfies Phase 4's existing
  `/api/v1/internal/**` `hasRole("ADMIN")` guards with no change.
- **D-02:** Admin users are **seeded** (Flyway/config-driven), NOT self-registered and NOT
  NIDA-verified — admin is a privileged operator account distinct from customer accounts. No
  admin self-signup endpoint.

### Notifications
- **D-03:** `notification-service` **consumes domain events from all four topic exchanges**
  (`identity.events`, `wallet.events`, `payment.events`, `messaging.events`) via durable queues
  bound passively (do NOT redeclare other services' exchanges — CLAUDE.md ownership boundary).
  Each handler is **idempotent** via `processed_events` + `INSERT ... ON CONFLICT DO NOTHING`
  (the Phase 3/4 pattern) so replayed events fire exactly once (SC-1).
- **D-04:** Phase 5 builds the **in-app notification log + feed API now** for all 6 event types
  (NOTF-01 NIDA verified, NOTF-02 payment confirmed, NOTF-03 low-credit, NOTF-04 7-day expiry
  warning, NOTF-05 campaign completion, NOTF-06 sender-ID decision). **Push delivery goes behind
  a `NotificationChannel` interface with a `StubPushChannel`** (mock-first, `@Profile`); real FCM
  is wired in Phase 6 when the Flutter app exists to register device tokens. Nothing can receive
  push before Phase 6.
- **D-05:** Research/planner must **confirm each of the 6 events is actually emitted** by an
  upstream service and map event→notification. Known emitted: UserVerified (identity), LowCreditAlert
  + ExpiryWarning (wallet), PaymentConfirmed (payment), SenderIdDecided + campaign-completion
  (messaging). If a campaign-completion event is missing, adding it is in scope (small upstream
  emit), but flag it rather than assume.

### Analytics
- **D-06:** Analytics ship as **backend read APIs this phase** (the Flutter app renders them in
  Phase 6). Each metric is served by the **service that owns the data**, exposed as authenticated
  per-user query endpoints — campaign delivery stats + operator-level delivery rates (M-Pesa/Tigo/
  Airtel/etc.) from **messaging-service** (ANLX-01/03); credit-usage-over-time + spend trend from
  **wallet-service** (ANLX-02). **No cross-service DB joins** (CLAUDE.md); compose at the API/edge
  if a combined view is ever needed. No dedicated analytics service at MVP.
- **D-07:** Operator-level delivery rates (ANLX-03) are derived from `outbound_messages`
  (provider/operator + delivery status) within messaging-service — an aggregate query, not a new
  data pipeline.

### Admin panel scope & audit log
- **D-08:** `admin-web` ships **all 6 operator screens** at MVP: sender-ID approval queue
  (approve/reject with reason → calls Phase 4's internal API), user search, ledger inspection,
  manual refund (calls Phase 3 refund credit-back API), bundle catalog management (Phase 3 catalog
  table CRUD), and an audit-log viewer (ADMN-02..07).
- **D-09:** The **audit log is populated two ways**: (a) every admin mutation writes an audit row
  in the admin/audit module, and (b) the admin/notification module **consumes key domain events**
  from the stream to record platform activity — giving a "full platform actions" log (ADMN-06)
  WITHOUT each service writing into a shared audit table (no cross-service coupling).
- **D-10:** admin-web is operator-facing/internal — functional shadcn/ui over polish. It
  authenticates via the admin JWT (D-01) and calls backend admin/read APIs; it does NOT access
  any service DB directly. **User analytics dashboards are NOT built in admin-web** (those are
  customer-facing → Phase 6 Flutter); operator-level delivery-rate views for admins MAY appear in
  admin-web.

### Resolved from research open questions (2026-06-21)
- **D-12:** **`CampaignCompleted` event is a confirmed GAP.** `DeliveryReceiptService.checkCampaignCompletion()`
  (Phase 4, 04-06) flips campaign status→COMPLETED in the DB but emits NO outbox event. Phase 5 MUST
  add a `CampaignCompleted` outbox emit to messaging-service (on `messaging.events`, with delivered/
  failed counts) BEFORE the NOTF-05 notification consumer can be wired. The other 5 events are
  confirmed emitted. This small upstream emit is in Phase 5 scope.
- **D-13:** **ANLX-03 (operator-level delivery rates) has no source column.** `outbound_messages`
  stores `external_id` (provider reference) but NOT the recipient's carrier/operator. Phase 5 must
  derive the carrier — preferred: compute/store the TZ carrier (Vodacom/Tigo/Airtel/Halotel) from the
  E.164 prefix (libphonenumber carrier mapping, already a dependency) at message creation or at
  query time. Planner decides store-vs-compute; either way ANLX-03 needs this derivation added.
- **D-14:** Admin module is **colocated in the owning service per domain** (admin read/mutation
  endpoints live in the service that owns the data — e.g. ledger inspection in wallet-service, user
  search in identity-service), exposed under ADMIN-guarded routes; there is no monolithic admin
  backend service. admin-web composes across these. The `admin`/audit schema + audit consumer is its
  own small module. Seeded admin password: BCrypt hash injected via env/secret (NOT hardcoded in
  Flyway) — Flyway seeds the admin row referencing the env-provided hash.

### Frontend stack (locked by CLAUDE.md)
- **D-11:** Next.js 14 (App Router, `app/` dir), TypeScript 5, Tailwind 3, shadcn/ui 3.5
  (components copied into repo, not npm). Prefer Server Actions for mutations. 1 replica at MVP.
  Do NOT upgrade to Tailwind 4.

### Claude's Discretion
- Notification log schema + feed API shape, the exact queue/binding names per consumed exchange,
  audit entry schema, analytics query/response DTOs, admin-web routing/layout + how Server Actions
  call backend APIs, and the admin login token TTL/refresh approach — all left to research/planning
  within the decisions above.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Upstream event contracts (consumed by notification + audit)
- `.planning/phases/02-identity-auth/02-06-SUMMARY.md` — `identity.events` topic exchange, routing
  key `identity.<EventType>`, UserVerified payload.
- `.planning/phases/03-wallet-payments/03-06-SUMMARY.md` — `wallet.events` LowCreditAlert +
  ExpiryWarning events; refund credit-back API for admin manual refunds (ADMN-05).
- `.planning/phases/03-wallet-payments/03-03-SUMMARY.md` / `03-05-SUMMARY.md` — bundle catalog
  table (ADMN-07 manages it); `payment.events` PaymentConfirmed (NOTF-02).
- `.planning/phases/04-contacts-messaging/04-05-SUMMARY.md` + `04-06-SUMMARY.md` +
  `04-08-SUMMARY.md` — `messaging.events`, campaign completion + status (NOTF-05, ANLX-01),
  `outbound_messages` operator/status (ANLX-03), SenderIdDecided + the internal sender-ID
  approve/reject API admin-web calls (ADMN-04, NOTF-06).

### Auth & ledger (admin actions)
- `.planning/phases/02-identity-auth/02-02-SUMMARY.md` — JWT issuance/RSA + shared-security
  validation contract (D-01 admin token reuses this); `roles` claim already in the token.
- `.planning/phases/04-contacts-messaging/04-08-SUMMARY.md` — JwtAuthenticationConverter mapping
  `roles` → authorities for `hasRole("ADMIN")` (admin-web depends on this working end-to-end).

### Stack & constraints
- `CLAUDE.md` §Frontend — Next.js 14 + App Router + Tailwind 3 + shadcn 3.5 (locked, D-11);
  §Patterns — RabbitMQ topic exchanges, idempotency, no cross-service joins, Traefik JWT edge.
- `.planning/PROJECT.md` §schemas — `notification` + `admin` are 2 of the 8 schemas; admin-web is
  the 3rd Deployment (1 replica).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Idempotent AMQP consumer**: wallet/messaging `processed_events` + `ProcessedEventRepository.tryInsert`
  + `@RabbitListener @QueueBinding` (passive bind) — exact template for notification + audit consumers (D-03/D-09).
- **JWT issuance + roles claim**: identity-service `JwtIssuer` + Phase 4 `JwtAuthenticationConverter`
  — admin login (D-01) reuses both; ROLE_ADMIN already flows end-to-end.
- **Resource-server SecurityConfig + IDOR guard**: every backend service — analytics endpoints
  (D-06) scope to `auth.getSubject()`.
- **Refund credit-back API + catalog table**: Phase 3 — admin manual refund (ADMN-05) and bundle
  management (ADMN-07) call/CRUD these.
- **Internal sender-ID approve/reject API**: Phase 4 `/api/v1/internal/**` ADMIN-guarded — admin-web
  sender-ID queue (ADMN-04) calls it.
- **Testcontainers bases + placeholder-IT-per-requirement (Wave 0)**: mirror for notification/admin services.

### Established Patterns
- One schema + one Flyway set per service; topic exchange per service; jakarta.*; TDD RED→GREEN
  (tdd_mode on); virtual threads. NEW: first Next.js app — establish admin-web build/test (Vitest/
  Playwright TBD by research) + shadcn setup.

### Integration Points
- **Inbound (AMQP):** notification + audit consume identity/wallet/payment/messaging events.
- **Outbound (REST, admin-web → backend):** admin login, user search, ledger read, refund, sender-ID
  decision, bundle CRUD, audit read, operator analytics.
- **Outbound (REST, future Flutter → backend):** per-user analytics + notification feed APIs (built
  now, consumed Phase 6).

</code_context>

<specifics>
## Specific Ideas

- Likely build order: Wave 0 test infra (notification + admin services + admin-web scaffold) →
  admin JWT/login + notification-service consumers + analytics read APIs (parallel, disjoint) →
  admin backend read/mutation APIs + audit → admin-web screens last (depends on the backend APIs).
- The notification-service event-fan-out + idempotency is the same proven pattern; the genuinely new
  surface is admin-web (first Next.js app) — research must establish its toolchain/testing.
</specifics>

<deferred>
## Deferred Ideas

- **Real push / FCM delivery** — Phase 6 (needs the Flutter client to register device tokens).
- **Customer-facing analytics + notification UI** — Phase 6 (Flutter renders the Phase 5 APIs).
- **Dedicated analytics/query service** — not at MVP; per-owner query endpoints suffice (D-06).
- **Email/SMS notification channels** — only in-app + (mock) push at MVP; other channels future.

</deferred>

---

*Phase: 5-Notifications, Admin & Analytics*
*Context gathered: 2026-06-21*
