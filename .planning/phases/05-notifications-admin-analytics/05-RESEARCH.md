# Phase 5: Notifications, Admin & Analytics — Research

**Researched:** 2026-06-21
**Domain:** Spring AMQP multi-exchange consumer · Next.js 14 App Router (admin-web) · Spring Boot admin & analytics APIs
**Confidence:** HIGH (backend — prior phases well-documented); MEDIUM (Next.js toolchain — verified via official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Identity-service issues admin JWTs — reuse Phase 2 RSA machinery. Add admin login path minting `roles:[ADMIN]`. No separate admin-service token issuer. Satisfies Phase 4's `/api/v1/internal/**` `hasRole("ADMIN")` guards with no change.
- **D-02:** Admin users are seeded via Flyway/config, NOT self-registered and NOT NIDA-verified.
- **D-03:** notification-service consumes domain events from all four topic exchanges (`identity.events`, `wallet.events`, `payment.events`, `messaging.events`) via durable queues bound passively. Each handler is idempotent via `processed_events` + `INSERT ... ON CONFLICT DO NOTHING`.
- **D-04:** Phase 5 builds in-app notification log + feed API for all 6 event types. Push delivery goes behind a `NotificationChannel` interface with a `StubPushChannel` (`@Profile`). Real FCM is Phase 6.
- **D-05:** Research/planner must confirm each of the 6 events is actually emitted upstream. (See Event-Emission Audit below.)
- **D-06:** Analytics ship as backend read APIs. Delivery stats from messaging-service (ANLX-01/03); credit-usage/spend from wallet-service (ANLX-02). No cross-service DB joins. No dedicated analytics service at MVP.
- **D-07:** ANLX-03 operator-level delivery rates derived from `outbound_messages` (provider/operator + delivery status) — aggregate query within messaging-service, not a new pipeline.
- **D-08:** admin-web ships all 6 operator screens: sender-ID approval queue, user search, ledger inspection, manual refund, bundle catalog CRUD, audit-log viewer.
- **D-09:** Audit log populated two ways: (a) admin mutation writes audit row; (b) admin/notification module consumes key domain events.
- **D-10:** admin-web is operator-facing/internal — functional shadcn/ui over polish. Does NOT access service DBs directly.
- **D-11:** Next.js 14 (App Router, `app/` dir), TypeScript 5, Tailwind 3, shadcn/ui 3.5 (components copied into repo). Prefer Server Actions for mutations. 1 replica at MVP. Do NOT upgrade to Tailwind 4.

### Claude's Discretion
Notification log schema + feed API shape, exact queue/binding names per consumed exchange, audit entry schema, analytics query/response DTOs, admin-web routing/layout + how Server Actions call backend APIs, admin login token TTL/refresh approach.

### Deferred Ideas (OUT OF SCOPE)
- Real push / FCM delivery — Phase 6
- Customer-facing analytics + notification UI — Phase 6 (Flutter renders Phase 5 APIs)
- Dedicated analytics/query service
- Email/SMS notification channels
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NOTF-01 | In-app notification: NIDA verification success | UserVerified event confirmed emitted from identity-service (02-06 summary) |
| NOTF-02 | In-app + push notification: payment confirmed | PaymentConfirmed event confirmed emitted from payment-service (03-05/03-06) |
| NOTF-03 | Low-credit alert notification | LowCreditAlert outbox event confirmed emitted from wallet-service (03-06) |
| NOTF-04 | 7-day credit expiry warning notification | ExpiryWarning outbox event confirmed emitted from wallet-service (03-06) |
| NOTF-05 | Campaign completion summary notification | CampaignCompleted event — **GAP: NOT CONFIRMED EMITTED** (see audit) |
| NOTF-06 | Sender ID approval/rejection notification | SenderIdDecided outbox event confirmed emitted from messaging-service (04-08) |
| ADMN-01 | Admin login with ROLE_ADMIN credentials | D-01: identity-service adds admin login path; RSA JWT machinery reused |
| ADMN-02 | Admin search and view user accounts | Admin backend endpoint calling identity-service user repository (admin-only) |
| ADMN-03 | Admin inspect any user's credit ledger | Admin read endpoint in wallet-service (ADMIN-scoped) |
| ADMN-04 | Admin view and process sender-ID approval queue | Calls Phase 4 `/api/v1/internal/sender-ids/{id}/approve|reject` (already exists) |
| ADMN-05 | Admin execute manual refunds | Calls Phase 3 `POST /api/v1/wallet/refunds` (already exists, JWT-authenticated) |
| ADMN-06 | Admin view full audit log | Admin module with dual-source audit table (mutation writes + event consumption) |
| ADMN-07 | Admin view and update bundle catalog | CRUD on Phase 3 `sms_bundles` table — admin service layer wrapping existing BundleRepository |
| ANLX-01 | Campaign delivery statistics per campaign | Aggregate query on `outbound_messages` by campaignId within messaging-service (already partially built in MESG-06) |
| ANLX-02 | Credit usage over time with spend trend | Aggregate query on `credit_transactions` within wallet-service, grouped by date |
| ANLX-03 | Operator-level delivery rates | Aggregate query on `outbound_messages` by provider/operator field within messaging-service |
</phase_requirements>

---

## Summary

Phase 5 delivers three non-overlapping deliverables that build on the fully-complete Phases 2–4 backend: (1) a `notification-service` that fans out domain events to an idempotent in-app notification log and feed API, (2) a `Next.js 14` admin-web panel backed by admin-specific API endpoints added to existing services, and (3) per-user analytics read APIs in messaging-service and wallet-service.

The backend work (notification-service, admin APIs, analytics endpoints) follows established patterns that are already proven in Phases 2–4: transactional outbox, `processed_events` idempotency table, `@QueueBinding` passive consumer, Spring Security `hasRole("ADMIN")`, and resource-server JWT validation via `shared-security`. The genuinely new surface is `admin-web` — the first Next.js App Router application in this project. Research establishes its toolchain, auth approach (httpOnly cookie storing admin JWT), Server Action mutation pattern, and testing setup (Vitest + Playwright).

The critical finding from the event-emission audit: **campaign completion (NOTF-05) has no dedicated event published to `messaging.events`**. Phase 4 sets campaign status to `COMPLETED` (04-06 `DeliveryReceiptService.checkCampaignCompletion`) but does not emit a `CampaignCompleted` outbox event. Adding this emit is in scope for Phase 5 Wave 0/1 as a small upstream addition to messaging-service.

**Primary recommendation:** Mirror the proven wallet/messaging `processed_events` consumer pattern for all four exchanges in notification-service; add a `CampaignCompleted` outbox event to messaging-service before wiring the NOTF-05 consumer; build admin-web with httpOnly cookie JWT, middleware-based route protection, and Server Actions calling backend REST APIs.

---

## Event-Emission Audit (D-05 — CRITICAL)

This section maps each of the 6 required notification events to their upstream emit source.

| # | Event | Source Service | Exchange | Routing Key | Status | Evidence |
|---|-------|---------------|----------|-------------|--------|----------|
| 1 | `UserVerified` | identity-service | `identity.events` | `identity.UserVerified` | **CONFIRMED** | 02-06 SUMMARY: `OutboxRelay` publishes to `identity.events` with `routing_key = identity.<eventType>`, payload `{eventId, userId, freeCredits:50}` |
| 2 | `PaymentConfirmed` | payment-service | `payment.events` | `payment.PaymentConfirmed` | **CONFIRMED** | 03-06 SUMMARY: `PaymentConfirmedConsumer` in wallet-service binds to `payment.events / payment.PaymentConfirmed`; payment-service emits via outbox relay in 03-05 |
| 3 | `LowCreditAlert` | wallet-service | `wallet.events` | `wallet.LowCreditAlert` | **CONFIRMED** | 03-06 SUMMARY: `LowCreditAlertJob` emits `LowCreditAlert` outbox entry → `wallet.events` exchange via `OutboxRelay` |
| 4 | `ExpiryWarning` | wallet-service | `wallet.events` | `wallet.ExpiryWarning` | **CONFIRMED** | 03-06 SUMMARY: `ExpiryWarningJob` emits `ExpiryWarning` outbox entry `{lotId, userId, expiresAt, remainingCredits}` → `wallet.events` |
| 5 | `CampaignCompleted` | messaging-service | `messaging.events` | `messaging.CampaignCompleted` | **GAP — MUST ADD** | 04-06 SUMMARY: `DeliveryReceiptService.checkCampaignCompletion()` sets campaign to `COMPLETED` in DB but does NOT write any outbox row or publish any event. No `CampaignCompleted` event exists in Phase 4. |
| 6 | `SenderIdDecided` | messaging-service | `messaging.events` | `messaging.SenderIdDecided` | **CONFIRMED** | 04-08 SUMMARY: `SenderIdService.approve/reject()` writes `SenderIdDecided` outbox row; `OutboxRelay` publishes to `messaging.events` |

### Campaign Completion Gap — Resolution Plan

`DeliveryReceiptService.checkCampaignCompletion()` in messaging-service (04-06) must be extended to write a `CampaignCompleted` outbox row when all messages reach terminal state. This is a small upstream change (1 method + 1 outbox write + 1 migration-free addition to the outbox table enum). It must be planned as a Wave 0 or Wave 1 task in Phase 5 before the notification-service NOTF-05 consumer can be wired.

Payload shape: `{ eventId: UUID, campaignId: UUID, userId: UUID, totalCount: int, deliveredCount: int, failedCount: int }`

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Admin JWT issuance (ROLE_ADMIN) | API — identity-service | — | D-01: reuse RSA machinery already in JwtIssuer |
| Admin user seeding | Database — identity schema | — | D-02: Flyway V-migration in identity-service |
| Notification event consumption + idempotency | API — notification-service | — | D-03: passive consumer on all 4 exchanges |
| In-app notification log (write) | API — notification-service | — | Writes to `notification` schema |
| Notification feed API (read) | API — notification-service | — | Consumed by Flutter in Phase 6 |
| Push notification (stub) | API — notification-service | — | `StubPushChannel` @Profile mock |
| Audit log writes (admin mutations) | API — admin module (identity or separate service) | — | D-09a |
| Audit log writes (domain events) | API — admin module consumer | — | D-09b: consumes key events from all exchanges |
| User search (admin) | API — identity-service (admin endpoint) | — | Identity owns the user table |
| Ledger inspection (admin) | API — wallet-service (admin endpoint) | — | Wallet owns credit_transactions |
| Manual refund (admin) | API — wallet-service (existing RefundController) | — | `POST /api/v1/wallet/refunds` exists (03-06) |
| Sender-ID queue + approve/reject | API — messaging-service (existing internal API) | — | `SenderIdAdminController` exists (04-08) |
| Bundle catalog CRUD | API — payment-service (new admin endpoints) | — | BundleRepository exists (03-03) |
| Delivery statistics (ANLX-01) | API — messaging-service | — | `outbound_messages` table owned by messaging |
| Operator delivery rates (ANLX-03) | API — messaging-service | — | Aggregate on `outbound_messages.provider` field |
| Credit usage trend (ANLX-02) | API — wallet-service | — | `credit_transactions` owned by wallet |
| Admin-web UI (all 6 screens) | Frontend — admin-web Next.js | — | D-11, D-10 |
| Admin JWT storage in browser | Frontend — admin-web (httpOnly cookie) | — | XSS-safe; set by Route Handler on login |
| Admin route protection | Frontend — Next.js middleware | — | Edge verification of JWT claim before render |

---

## Standard Stack

### Backend (notification-service, admin endpoints, analytics endpoints)

All libraries managed by Spring Boot 3.5.9 BOM — no version overrides needed.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-amqp` | BOM-managed | `@RabbitListener` multi-exchange consumer | Already in messaging-service, wallet-service — exact same pattern |
| `spring-boot-starter-data-jpa` | BOM-managed | `processed_events` + notification log persistence | Established pattern in all prior services |
| `spring-boot-starter-oauth2-resource-server` | BOM-managed | JWT resource server on notification + admin service | All 8 services follow this pattern |
| `spring-boot-starter-web` | BOM-managed | Notification feed API + admin/analytics REST endpoints | Standard |
| `spring-boot-starter-validation` | BOM-managed | `@Valid` on admin request DTOs | All services |
| `flyway-core` + `flyway-database-postgresql` | BOM-managed (10.x) | `notification` + `admin` schema migrations | MUST add `flyway-database-postgresql` explicitly (CLAUDE.md pattern) |
| `mapstruct` | 1.6.3 | DTO ↔ entity mapping | Lombok before MapStruct in annotationProcessor order |
| `lombok` | BOM-managed | Boilerplate reduction | Established across all services |
| `libs/shared-security` | project lib | `NimbusJwtDecoder.withPublicKey` JWT validation | Already used by all 8 services |

### Frontend (admin-web — Next.js 14)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| Next.js | 14.x (locked) | App Router framework | CLAUDE.md (locked) |
| TypeScript | 5.x | Type safety | CLAUDE.md (bundled with Next.js 14) |
| Tailwind CSS | 3.x | Utility styling | CLAUDE.md (locked — do NOT upgrade to v4) |
| shadcn/ui | 3.5.x | Copied accessible components | CLAUDE.md (components copied into repo, not npm dep) |
| Vitest | latest stable | Unit tests (Server Actions, util functions, sync components) | Next.js official testing guide [CITED: nextjs.org/docs/app/guides/testing/vitest] |
| `@vitejs/plugin-react` | latest | React support in Vitest | Required for Vitest in Next.js [CITED: nextjs.org/docs/app/guides/testing/vitest] |
| `@testing-library/react` | latest | Component testing in Vitest | Standard with Vitest in Next.js |
| `jsdom` | latest | DOM environment for Vitest | Required for browser-like env |
| `vite-tsconfig-paths` | latest | Path alias resolution in Vitest | Needed for `@/` imports |
| Playwright | latest | E2E tests (auth flows, middleware, cookie-gated routes) | Next.js official Playwright guide [CITED: nextjs.org/docs/pages/guides/testing/playwright] |

**Installation (admin-web):**
```bash
cd apps/admin-web
npx shadcn@3.5 init   # copies component primitives into src/components/ui/
npm install -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/dom vite-tsconfig-paths
npm install -D @playwright/test
npx playwright install --with-deps chromium
```

> Note: shadcn components are NOT an npm dependency — they are copied into the repo under `src/components/ui/`. This is by design (CLAUDE.md).

### Package Legitimacy Audit

All frontend packages are established ecosystem tools with multi-year histories; backend packages are managed by Spring Boot BOM (no legitimacy concern). The packages specific to admin-web testing:

| Package | Registry | Age | Downloads | slopcheck | Disposition |
|---------|----------|-----|-----------|-----------|-------------|
| vitest | npm | 3+ yrs | ~20M/wk | [ASSUMED — slopcheck unavailable] | Approved (major build tool) |
| @vitejs/plugin-react | npm | 3+ yrs | ~10M/wk | [ASSUMED] | Approved (official Vite team) |
| @testing-library/react | npm | 6+ yrs | ~10M/wk | [ASSUMED] | Approved (canonical testing library) |
| @playwright/test | npm | 4+ yrs | ~5M/wk | [ASSUMED] | Approved (Microsoft-maintained) |
| jsdom | npm | 10+ yrs | ~30M/wk | [ASSUMED] | Approved (long-established) |

*slopcheck was unavailable at research time. All packages above are well-known with official backing; risk is minimal but the planner should add a `checkpoint:human-verify` before the `npm install -D` step in Wave 0 per protocol.*

---

## Architecture Patterns

### System Architecture Diagram

```
AMQP Topic Exchanges (owned by upstream services)
  identity.events ──────┐
  wallet.events ─────────┼──► notification-service
  payment.events ────────┤      ├── ProcessedEventRepository (idempotency)
  messaging.events ──────┘      ├── NotificationRepository (notification schema)
                                ├── GET /api/v1/notifications (feed, JWT-scoped)
                                └── NotificationChannel ──► StubPushChannel (@Profile)

  messaging.events ──────┐
  wallet.events ──────────┼──► admin-module (within admin-service)
  identity.events ────────┘      └── AuditRepository (event-driven writes)

Admin mutations ──► admin-service REST endpoints
  identity-service: admin login + user search
  wallet-service: admin ledger view + manual refund
  messaging-service: sender-ID queue (existing internal API)
  payment-service: bundle catalog CRUD
  admin module: audit log (combined view)

admin-web (Next.js 14)
  Browser ──► middleware.ts (edge JWT verify → redirect /login)
       ├── /login page → Server Action → POST identity-service /admin/login
       │                               → Set httpOnly cookie (access_token)
       ├── /users → Server Component → fetch identity-service /admin/users
       ├── /ledger → Server Component → fetch wallet-service /admin/ledger
       ├── /sender-ids → Server Action → POST messaging-service /internal/sender-ids/{id}/approve
       ├── /refunds → Server Action → POST wallet-service /api/v1/wallet/refunds
       ├── /bundles → Server Component + Server Action → CRUD payment-service /admin/bundles
       └── /audit → Server Component → fetch admin-service /admin/audit

Analytics read APIs (built now, consumed by Flutter in Phase 6)
  messaging-service:
    GET /api/v1/analytics/campaigns/{id}/stats  (ANLX-01 — userId-scoped, reuses outbound_messages counts from MESG-06)
    GET /api/v1/analytics/operator-rates         (ANLX-03 — userId-scoped, GROUP BY provider on outbound_messages)
  wallet-service:
    GET /api/v1/analytics/credit-usage           (ANLX-02 — userId-scoped, GROUP BY date on credit_transactions)
```

### Recommended Project Structure

**notification-service** (new Spring Boot module):
```
services/notification-service/
├── src/main/java/com/smsreseller/notification/
│   ├── config/
│   │   ├── RabbitMqConfig.java          # passive bindings on all 4 exchanges
│   │   └── SecurityConfig.java
│   ├── consumer/
│   │   ├── IdentityEventConsumer.java   # UserVerified
│   │   ├── WalletEventConsumer.java     # LowCreditAlert, ExpiryWarning
│   │   ├── PaymentEventConsumer.java    # PaymentConfirmed
│   │   └── MessagingEventConsumer.java  # CampaignCompleted, SenderIdDecided
│   ├── idempotency/
│   │   └── ProcessedEventRepository.java  # tryInsert pattern
│   ├── notification/
│   │   ├── Notification.java            # entity
│   │   ├── NotificationRepository.java
│   │   ├── NotificationType.java        # enum: NIDA_VERIFIED, PAYMENT_CONFIRMED, ...
│   │   ├── NotificationService.java
│   │   └── NotificationController.java  # GET /api/v1/notifications (feed)
│   └── push/
│       ├── NotificationChannel.java     # interface
│       └── StubPushChannel.java         # @Profile("!prod")
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_processed_events.sql
        └── V2__create_notifications.sql
```

**admin-web** (new Next.js app):
```
apps/admin-web/
├── app/
│   ├── layout.tsx
│   ├── (auth)/
│   │   └── login/page.tsx
│   └── (admin)/
│       ├── layout.tsx               # admin shell with nav
│       ├── users/page.tsx
│       ├── ledger/[userId]/page.tsx
│       ├── sender-ids/page.tsx
│       ├── refunds/page.tsx
│       ├── bundles/page.tsx
│       └── audit/page.tsx
├── middleware.ts                    # JWT cookie verify → redirect /login
├── lib/
│   ├── api.ts                       # typed fetch wrapper for backend REST calls
│   └── auth.ts                      # cookie read helper
├── components/ui/                   # shadcn copied components
├── vitest.config.mts
├── playwright.config.ts
└── Dockerfile
```

### Pattern 1: Multi-Exchange Passive Consumer (notification-service)

**What:** One `@RabbitListener` method per consumer class, each binding to its own durable queue on a pre-existing exchange using `ignoreDeclarationExceptions = "true"` to avoid exchange re-declaration.

**When to use:** Notification-service does NOT own any of the 4 exchanges. It must bind passively — declare only the queue, never the exchange topology.

```java
// Source: Spring AMQP docs — @QueueBinding with ignoreDeclarationExceptions
@RabbitListener(bindings = @QueueBinding(
    value = @Queue(value = "notification.identity.UserVerified", durable = "true"),
    exchange = @Exchange(
        value = "identity.events",
        type = ExchangeTypes.TOPIC,
        durable = "true",
        ignoreDeclarationExceptions = "true"   // ← passive: don't fail if exchange config differs
    ),
    key = "identity.UserVerified"
))
@Transactional
public void onUserVerified(UserVerifiedEvent event) {
    if (!processedEventRepo.tryInsert(event.eventId())) return; // idempotency gate
    notificationService.create(event.userId(), NotificationType.NIDA_VERIFIED, buildPayload(event));
    notificationChannel.push(event.userId(), "Identity verified", "Your NIDA verification is complete.");
}
```

Queue naming convention: `notification.<sourceExchange>.<EventType>` — unambiguous, no collisions.

**All 4 consumer classes in notification-service:**

| Consumer Class | Queue Name | Exchange | Routing Keys |
|----------------|-----------|----------|-------------|
| `IdentityEventConsumer` | `notification.identity.UserVerified` | `identity.events` | `identity.UserVerified` |
| `PaymentEventConsumer` | `notification.payment.PaymentConfirmed` | `payment.events` | `payment.PaymentConfirmed` |
| `WalletEventConsumer` | `notification.wallet.alerts` | `wallet.events` | `wallet.LowCreditAlert`, `wallet.ExpiryWarning` |
| `MessagingEventConsumer` | `notification.messaging.events` | `messaging.events` | `messaging.CampaignCompleted`, `messaging.SenderIdDecided` |

> `WalletEventConsumer` can use a single queue bound to 2 routing keys on the same exchange (multi-binding in one `@RabbitListener`). `MessagingEventConsumer` similarly handles 2 routing keys.

### Pattern 2: Admin JWT via httpOnly Cookie (admin-web)

**What:** Admin logs in via a Server Action → backend returns JWT → Next.js sets it as an httpOnly cookie → middleware reads + verifies cookie on every request → redirect to /login if invalid.

**When to use:** All admin-web routes. Prevents XSS token theft. Admin JWT TTL should be 60 minutes (longer than user 15min since admin is internal).

```typescript
// Source: Next.js official auth guide — nextjs.org/docs/app/guides/authentication
// app/(auth)/login/actions.ts — Server Action
'use server'
import { cookies } from 'next/headers'

export async function adminLogin(formData: FormData) {
  const res = await fetch(`${process.env.BACKEND_URL}/api/v1/auth/admin/login`, {
    method: 'POST',
    body: JSON.stringify({ email: formData.get('email'), password: formData.get('password') }),
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) return { error: 'Invalid credentials' }
  const { accessToken } = await res.json()
  cookies().set('admin_token', accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 60 * 60,  // 60 minutes — matches backend TTL
    path: '/',
  })
}
```

```typescript
// middleware.ts — edge route protection
import { NextRequest, NextResponse } from 'next/server'

export function middleware(request: NextRequest) {
  const token = request.cookies.get('admin_token')?.value
  if (!token) return NextResponse.redirect(new URL('/login', request.url))
  // For MVP: rely on backend 401 responses; full JWT decode at edge optional
  return NextResponse.next()
}

export const config = { matcher: ['/(admin)/:path*'] }
```

### Pattern 3: Server Component calling backend API

```typescript
// app/(admin)/users/page.tsx — React Server Component
import { cookies } from 'next/headers'

export default async function UsersPage({ searchParams }: { searchParams: { q?: string } }) {
  const token = cookies().get('admin_token')?.value
  const res = await fetch(
    `${process.env.BACKEND_URL}/api/v1/admin/users?search=${searchParams.q ?? ''}`,
    { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' }
  )
  const data = await res.json()
  return <UserTable users={data.content} />
}
```

### Pattern 4: Notification Log Schema

```sql
-- V2__create_notifications.sql
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    type            VARCHAR(50) NOT NULL,   -- NIDA_VERIFIED, PAYMENT_CONFIRMED, etc.
    title           VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    payload         JSONB,                  -- event-specific data (campaignId, credits, etc.)
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
```

Feed API: `GET /api/v1/notifications?page=0&size=20` — JWT-scoped to `auth.getSubject()`, sorted by `created_at DESC`. Unread count as separate endpoint or response field.

### Pattern 5: Analytics Aggregate Queries (no cross-service joins)

Each analytics endpoint lives in the service that owns the data:

```java
// messaging-service: ANLX-01 campaign stats (already partially present via MESG-06 counts)
// Extend CampaignService.toCampaignResponseWithCounts — same OutboundMessageRepository queries
// New endpoint: GET /api/v1/analytics/campaigns/{id}/stats (user-scoped)

// ANLX-03 operator delivery rates
@Query("""
    SELECT m.provider AS operator, m.status AS status, COUNT(m) AS count
    FROM OutboundMessage m
    WHERE m.userId = :userId
    GROUP BY m.provider, m.status
""")
List<OperatorRateRow> findOperatorRatesByUser(@Param("userId") UUID userId);
```

```java
// wallet-service: ANLX-02 credit usage trend
@Query("""
    SELECT CAST(ct.createdAt AS LocalDate) AS date, SUM(ABS(ct.delta)) AS consumed
    FROM CreditTransaction ct
    WHERE ct.userId = :userId AND ct.delta < 0
    GROUP BY CAST(ct.createdAt AS LocalDate)
    ORDER BY date DESC
    LIMIT 90
""")
List<CreditUsageRow> findDailyUsageByUser(@Param("userId") UUID userId);
```

> ANLX-01 delivery stats per campaign reuse the aggregate count query from MESG-06 — expose via new `/analytics/` path with identical DB query.

### Pattern 6: Admin Login — identity-service Extension

Add to identity-service (small, contained):
- `POST /api/v1/auth/admin/login` — email+password against admin user table (same users table, filter by `role = ADMIN`)
- `JwtIssuer.issueAdminToken(UUID adminId)` → RS256, `roles:["ROLE_ADMIN"]`, 60-min TTL, same RSA key
- No refresh token for admin MVP (operator can re-login if session expires)
- Admin seed: Flyway `V5__seed_admin_user.sql` — insert with `DelegatingPasswordEncoder` pre-hashed password (BCrypt prefix `{bcrypt}$2a$...`)

### Anti-Patterns to Avoid

- **Re-declaring other services' exchanges:** `ignoreDeclarationExceptions = "true"` is mandatory. Omitting it causes Spring AMQP to declare a fresh exchange which may fail if broker has different topology (e.g. `durable=false` vs `durable=true`). [VERIFIED: Spring AMQP docs]
- **Calling `cookies().set()` in a Server Component render:** Only works in Server Actions and Route Handlers. [CITED: nextjs.org/docs/app/guides/authentication]
- **Storing admin JWT in localStorage:** XSS risk. Use httpOnly cookie exclusively. [ASSUMED — security principle]
- **Cross-service DB joins for analytics:** CLAUDE.md explicitly forbids this. Use per-service aggregate queries, compose at the client if a combined view is needed.
- **Redeclaring shared-security keys:** notification-service and admin-service import `libs/shared-security` for JWT validation — do not create a duplicate decoder bean.
- **`javax.*` imports:** All new services use `jakarta.*` (Spring Boot 3 / Jakarta EE 10).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Idempotency for AMQP consumers | Custom "have I seen this?" logic | `processed_events` + `tryInsert` pattern from wallet/messaging services | Already proven, INSERT ON CONFLICT is atomic; race-condition-safe |
| JWT validation in notification/admin service | Custom decoder | `spring-boot-starter-oauth2-resource-server` + `shared-security` | Already works for all 8 services |
| Admin route protection in Next.js | Custom auth middleware | `middleware.ts` + `cookies()` from `next/headers` | Next.js built-in; runs at edge before React render |
| Exchange topology re-declaration | Manual `RabbitAdmin.declareExchange` | `@QueueBinding` with `ignoreDeclarationExceptions = "true"` | Passive bind — notification-service is a consumer, not an owner |
| Campaign completion detection | Background poll for campaign status | `DeliveryReceiptService.checkCampaignCompletion()` already exists (04-06) | Just add outbox emit, don't re-implement detection |
| Operator rate aggregation | Dedicated analytics pipeline | SQL GROUP BY on `outbound_messages` within messaging-service | D-07: aggregate query is sufficient at MVP scale |

**Key insight:** Phase 5 has almost no net-new patterns — it reuses every pattern from Phases 2–4. The main risk is misapplying Next.js SSR concepts (trying to set cookies in Server Components, not understanding when to use Server Actions vs Route Handlers).

---

## Common Pitfalls

### Pitfall 1: Exchange Re-Declaration Fails Silently or Hard-Fails
**What goes wrong:** notification-service tries to declare `identity.events` exchange with slightly different properties than identity-service declared it. Broker rejects with PRECONDITION_FAILED; consumer never starts.
**Why it happens:** Forgetting `ignoreDeclarationExceptions = "true"` on the `@Exchange` annotation.
**How to avoid:** Always set `ignoreDeclarationExceptions = "true"` on every `@Exchange` in notification-service and admin consumer. [CITED: Spring AMQP docs — annotation-driven listener endpoints]
**Warning signs:** `CHANNEL_ERROR expected replyCode=200 but got 406` in logs at startup.

### Pitfall 2: `cookies()` called in Server Component render phase
**What goes wrong:** `cookies().set(...)` throws "cookies() was called outside a Server Action or Route Handler" at runtime.
**Why it happens:** Next.js restricts cookie mutation to Server Actions and Route Handlers — RSC render is read-only for cookies.
**How to avoid:** Mutate cookies only inside `'use server'` functions (Server Actions) or `route.ts` handlers. Reading cookies in Server Components (`cookies().get(...)`) is always allowed.
**Warning signs:** Runtime error "cookies() was called outside a request scope."

### Pitfall 3: Admin JWT Not Scoped — Admin Sees Other Admins' Data
**What goes wrong:** Admin analytics or user search endpoints don't scope by userId, or worse, expose all users with no guard.
**Why it happens:** Analytics endpoints are user-scoped (`auth.getSubject()` as userId), but admin endpoints intentionally search across users — forgetting that admin endpoints need a separate `hasRole("ADMIN")` guard.
**How to avoid:** Analytics endpoints under `/api/v1/analytics/**` use JWT subject scoping (per-user). Admin endpoints under `/api/v1/admin/**` require `hasRole("ADMIN")` — no subject scoping. Map these separately in SecurityConfig.

### Pitfall 4: CampaignCompleted Event Missing → NOTF-05 Consumer Dead
**What goes wrong:** notification-service `MessagingEventConsumer` binds to routing key `messaging.CampaignCompleted` but no message ever arrives; consumers sits idle.
**Why it happens:** Phase 4's `DeliveryReceiptService.checkCampaignCompletion()` transitions the campaign to COMPLETED in DB but does NOT publish an outbox event (confirmed in 04-06 audit).
**How to avoid:** Phase 5 Wave 0 or Wave 1 must add `CampaignCompleted` outbox emit to `DeliveryReceiptService` in messaging-service BEFORE writing the NOTF-05 consumer. Plan this as an upstream task in messaging-service.

### Pitfall 5: Vitest Cannot Test Async Server Components
**What goes wrong:** Vitest test for a Server Component with `await fetch(...)` fails because Vitest does not support async RSC.
**Why it happens:** Vitest + jsdom cannot simulate the Next.js SSR runtime for async components.
**How to avoid:** Test async Server Components with Playwright E2E tests only. Use Vitest for: Server Actions (pure functions), utility functions, sync RSC, Client Components. [CITED: nextjs.org/docs/app/guides/testing/vitest]

### Pitfall 6: Admin Token Refresh Not Needed at MVP (but TTL Must Be Set)
**What goes wrong:** Admin JWT expires after 15 minutes (inheriting user TTL from JwtIssuer default), making the admin panel unusable.
**Why it happens:** `JwtIssuer.issueAccessToken()` defaults to 15-min TTL; admin token must be explicitly issued with a longer TTL (60 min) via a separate method.
**How to avoid:** Add `JwtIssuer.issueAdminToken(UUID)` with 60-min TTL. No refresh token for MVP admin — if session expires, admin re-logs in.

---

## Validation Architecture

nyquist_validation is enabled in config.json.

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Testcontainers 1.21.2 (Spring Boot BOM) |
| Frontend framework | Vitest (unit) + Playwright (E2E) |
| Backend quick run | `./gradlew :services:notification-service:test` |
| Backend full suite | `./gradlew :services:notification-service:test :services:identity-service:test :services:messaging-service:test :services:wallet-service:test :services:payment-service:test` |
| Frontend unit run | `cd apps/admin-web && npm run test` (vitest) |
| Frontend E2E run | `cd apps/admin-web && npx playwright test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NOTF-01 | UserVerified event → notification row created (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*UserVerifiedConsumerIT*"` | ❌ Wave 0 |
| NOTF-02 | PaymentConfirmed event → notification row created | Integration | `./gradlew :services:notification-service:test --tests "*PaymentConfirmedConsumerIT*"` | ❌ Wave 0 |
| NOTF-03 | LowCreditAlert event → notification row created | Integration | `./gradlew :services:notification-service:test --tests "*LowCreditAlertConsumerIT*"` | ❌ Wave 0 |
| NOTF-04 | ExpiryWarning event → notification row created | Integration | `./gradlew :services:notification-service:test --tests "*ExpiryWarningConsumerIT*"` | ❌ Wave 0 |
| NOTF-05 | CampaignCompleted event emitted by messaging-service (upstream fix) | Integration | `./gradlew :services:messaging-service:test --tests "*CampaignCompletedIT*"` | ❌ Wave 0 (upstream) |
| NOTF-05 | CampaignCompleted event → notification row created | Integration | `./gradlew :services:notification-service:test --tests "*CampaignCompletedConsumerIT*"` | ❌ Wave 0 |
| NOTF-06 | SenderIdDecided event → notification row created | Integration | `./gradlew :services:notification-service:test --tests "*SenderIdDecidedConsumerIT*"` | ❌ Wave 0 |
| NOTF-01..06 | Duplicate event replay → exactly one notification row | Integration | Covered by each consumer IT (second publish, assert count=1) | ❌ Wave 0 |
| NOTF-01..06 | Notification feed API returns user's notifications, JWT-scoped | Integration | `./gradlew :services:notification-service:test --tests "*NotificationFeedIT*"` | ❌ Wave 0 |
| ADMN-01 | Admin login returns JWT with ROLE_ADMIN | Integration | `./gradlew :services:identity-service:test --tests "*AdminLoginIT*"` | ❌ Wave 0 |
| ADMN-02 | Admin user search returns matching users | Integration | `./gradlew :services:identity-service:test --tests "*AdminUserSearchIT*"` | ❌ Wave 0 |
| ADMN-03 | Admin ledger inspection returns user's transactions | Integration | `./gradlew :services:wallet-service:test --tests "*AdminLedgerIT*"` | ❌ Wave 0 |
| ADMN-04 | Sender-ID approve/reject (already tested in 04-08 SenderIdIT) | Integration | `./gradlew :services:messaging-service:test --tests "*SenderIdIT*"` | ✅ Exists (04-08) |
| ADMN-05 | Manual refund (already tested in 03-06 RefundIT) | Integration | `./gradlew :services:wallet-service:test --tests "*RefundIT*"` | ✅ Exists (03-06) |
| ADMN-06 | Audit log contains admin mutations + domain events | Integration | `./gradlew :services:identity-service:test --tests "*AuditLogIT*"` | ❌ Wave 0 |
| ADMN-07 | Bundle catalog CRUD (admin-only) | Integration | `./gradlew :services:payment-service:test --tests "*AdminBundleCatalogIT*"` | ❌ Wave 0 |
| ANLX-01 | Campaign delivery stats returned for owner | Integration | `./gradlew :services:messaging-service:test --tests "*CampaignAnalyticsIT*"` | ❌ Wave 0 |
| ANLX-02 | Credit usage trend returns daily aggregates | Integration | `./gradlew :services:wallet-service:test --tests "*CreditUsageAnalyticsIT*"` | ❌ Wave 0 |
| ANLX-03 | Operator delivery rates grouped by provider | Integration | `./gradlew :services:messaging-service:test --tests "*OperatorRateAnalyticsIT*"` | ❌ Wave 0 |
| ADMN-01 | Admin login page → successful login → redirected to /users | E2E (Playwright) | `npx playwright test --grep "admin login"` | ❌ Wave 0 |
| ADMN-02 | Admin user search UI finds user by email | E2E (Playwright) | `npx playwright test --grep "user search"` | ❌ Wave 0 |
| ADMN-04 | Sender-ID approve flow visible in UI and calls backend | E2E (Playwright) | `npx playwright test --grep "sender-id approval"` | ❌ Wave 0 |
| ADMN-01 | Non-admin token → middleware redirects to /login | E2E or Vitest (middleware unit) | `vitest run middleware.test.ts` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit (backend):** `./gradlew :<service>:test -x integrationTest` (unit tests only, fast)
- **Per task commit (frontend):** `npm run test -- --run` (Vitest unit, no Playwright)
- **Per wave merge:** Full backend test suite for modified services + Playwright E2E
- **Phase gate:** All services green + Playwright E2E suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `notification-service/` — new Spring Boot module, full build setup + `AbstractIntegrationTest` (Testcontainers PG + RabbitMQ)
- [ ] `notification-service/src/test/…/UserVerifiedConsumerIT.java` — placeholder ITs for NOTF-01..06
- [ ] `notification-service/src/test/…/NotificationFeedIT.java` — placeholder IT for feed API
- [ ] `services/messaging-service/src/test/…/CampaignCompletedIT.java` — upstream emit test (NOTF-05 prerequisite)
- [ ] `services/identity-service/src/test/…/AdminLoginIT.java` — ADMN-01
- [ ] `services/wallet-service/src/test/…/AdminLedgerIT.java` — ADMN-03
- [ ] `services/wallet-service/src/test/…/CreditUsageAnalyticsIT.java` — ANLX-02
- [ ] `services/messaging-service/src/test/…/CampaignAnalyticsIT.java` — ANLX-01
- [ ] `services/messaging-service/src/test/…/OperatorRateAnalyticsIT.java` — ANLX-03
- [ ] `services/payment-service/src/test/…/AdminBundleCatalogIT.java` — ADMN-07
- [ ] `apps/admin-web/` — Next.js scaffold + `vitest.config.mts` + `playwright.config.ts`
- [ ] `apps/admin-web/middleware.test.ts` — Vitest unit for middleware redirect logic

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Admin login endpoint in identity-service; BCrypt via DelegatingPasswordEncoder (existing) |
| V3 Session Management | yes | httpOnly cookie (admin-web); no refresh token for MVP admin |
| V4 Access Control | yes | `hasRole("ADMIN")` on all admin endpoints; IDOR on analytics scoped to JWT subject |
| V5 Input Validation | yes | `@Valid` on all admin request DTOs; shadcn form validation on admin-web |
| V6 Cryptography | yes | RSA-2048 JWT (NimbusJwtEncoder) — reused from Phase 2, do not change |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Admin JWT stolen via XSS | Information Disclosure | httpOnly cookie — JS cannot access |
| Admin endpoint called with user JWT (ROLE_USER) | Elevation of Privilege | `hasRole("ADMIN")` on `/api/v1/admin/**` and `/api/v1/internal/**` |
| Duplicate AMQP event double-creates notification | Tampering | `processed_events.tryInsert(eventId)` idempotency gate |
| Analytics endpoint returns another user's data (IDOR) | Information Disclosure | All analytics scoped to `auth.getSubject()` from JWT |
| Audit log tampered (post-write) | Tampering | Append-only audit table (no UPDATE/DELETE); admin-only read endpoint |
| Bundle price set to 0 or negative | Tampering | `@Positive` / `@Min(1)` on `priceTzs` in admin bundle CRUD DTO |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Bean RabbitAdmin` manual queue declaration | `@QueueBinding` with `ignoreDeclarationExceptions` | Spring AMQP 2.0 | Declarative, no manual admin bean needed |
| Pages Router (`pages/api/`) | App Router (`app/` dir, Server Actions) | Next.js 13/14 | No `NextApiRequest`/`NextApiResponse` for mutations; use `'use server'` |
| `getServerSideProps` for data fetching | Async Server Components + `fetch()` | Next.js 13/14 | Direct `await fetch()` in RSC body |
| Separate analytics service | Per-service aggregate query endpoints | MVP pattern | No new infra; avoids cross-service join; sufficient at MVP scale |

---

## Open Questions

1. **Where does the `admin` module live (identity-service or separate admin-service)?**
   - What we know: The `admin` schema is one of the 8 schemas (PROJECT.md). Admin user search touches `identity` schema; audit log is its own `admin` schema.
   - What's unclear: Should the admin API endpoints (user search, audit) live in identity-service as an additional controller, or in a new `admin-service`?
   - Recommendation: For MVP, co-locate admin endpoints in the service that owns the underlying data (user search in identity-service, ledger in wallet-service, bundle CRUD in payment-service). The `audit-service` is the only truly new service beyond notification-service. This avoids creating a new JVM process just to proxy data.

2. **Does `outbound_messages` have a `provider`/`operator` column for ANLX-03?**
   - What we know: 04-05/04-06 SUMMARY references `outbound_messages` entity with `externalId`, `status`, `lotId`. ANLX-03 requires grouping by operator (M-Pesa, Tigo, etc.).
   - What's unclear: Whether the `provider` field (operator name) is stored on `OutboundMessage` or on `SendMessagePayload`. The Phase 4 summaries reference `provider` in the summary text but not explicitly as a DB column.
   - Recommendation: Planner must grep `OutboundMessage.java` to confirm the column exists. If missing, a small Flyway migration adding `provider VARCHAR(50)` to `outbound_messages` is in scope for Phase 5 Wave 1.

3. **Admin token refresh strategy for admin-web**
   - What we know: User JWT is 15-min with 7-day refresh (Phase 2). No admin refresh specified.
   - What's unclear: Whether 60-min TTL with forced re-login is acceptable, or if an admin refresh token should be added.
   - Recommendation: 60-min with re-login for MVP. If the operator finds this disruptive during Phase 5 testing, add a refresh token as a followup — the mechanism already exists in identity-service.

4. **Seeded admin credentials — how are they managed as secrets?**
   - What we know: D-02 says admin users are Flyway-seeded. CLAUDE.md says all secrets via K8s Secrets.
   - What's unclear: Whether the Flyway seed uses a hardcoded BCrypt hash (acceptable for MVP) or reads from environment variable.
   - Recommendation: Flyway V5 seed with a fixed pre-hashed password `{bcrypt}$2a$...` in the migration file for MVP. Document that the admin password must be changed post-deploy. A K8s Secret with a runtime-injected admin password via application config is the prod pattern.

5. **Does admin-web need a Dockerfile in Phase 5?**
   - What we know: INFR-01 (Kustomize overlays for api, worker, admin-web) is Phase 1 — but admin-web didn't exist in Phase 1.
   - What's unclear: Whether the Phase 1 admin-web deployment placeholder was wired with a real image.
   - Recommendation: Yes — Phase 5 must build and verify the admin-web Dockerfile (multi-stage: `node:20-alpine` build + `nginx:alpine` serve, or Next.js standalone output mode). Kustomize overlay update is in scope.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `outbound_messages` has a `provider` column for operator-level grouping (ANLX-03) | Architecture Patterns (analytics queries) | ANLX-03 cannot be implemented without a migration; delays Wave 2 |
| A2 | Vitest and Playwright packages are legitimate (slopcheck not available) | Standard Stack / Package Legitimacy Audit | Extremely low risk — all are well-known packages with official backing |
| A3 | Admin token 60-min TTL is acceptable for MVP operator UX | Common Pitfalls #6 / Open Questions | If admins frequently time out, re-login friction blocks operations |
| A4 | The `admin` schema is owned by a new thin admin/audit service (or collocated in identity-service), not by all 5 existing services | Architecture Patterns | Over-complicates service boundaries if wrong |

---

## Environment Availability

| Dependency | Required By | Available | Fallback |
|------------|------------|-----------|---------|
| Node.js ≥ 18 | admin-web build | Verify before Wave 0 (`node --version`) | — |
| npm / pnpm | admin-web deps | Verify | — |
| RabbitMQ (Testcontainers) | notification-service ITs | ✓ (already used in identity, wallet, messaging services) | — |
| PostgreSQL (Testcontainers) | notification-service ITs | ✓ (established pattern) | — |
| Playwright browsers | admin-web E2E | Must install (`npx playwright install --with-deps chromium`) | Skip E2E for Wave 0, add install step |

---

## Sources

### Primary (HIGH confidence)
- Phase 02 summaries (02-02, 02-06) — JWT RSA issuance, `roles` claim, `UserVerified` outbox event contract [VERIFIED: project source of truth]
- Phase 03 summaries (03-03, 03-06) — `payment.events / payment.PaymentConfirmed`, `wallet.events / LowCreditAlert + ExpiryWarning`, bundle catalog table, `RefundController` [VERIFIED: project source of truth]
- Phase 04 summaries (04-05, 04-06, 04-08) — `messaging.events / SenderIdDecided` confirmed; `CampaignCompleted` GAP identified; `JwtAuthenticationConverter` for ROLE_ADMIN; `outbound_messages` table [VERIFIED: project source of truth]
- CLAUDE.md — Next.js 14 + Tailwind 3 + shadcn 3.5 locked; RabbitMQ topic exchange pattern; no cross-service joins; `jakarta.*` required [VERIFIED: project constraints]

### Secondary (MEDIUM confidence)
- [Spring AMQP annotation-driven listener endpoints](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven.html) — `@QueueBinding` + `ignoreDeclarationExceptions` pattern [CITED]
- [Next.js official authentication guide](https://nextjs.org/docs/app/guides/authentication) — httpOnly cookies, Server Actions, middleware pattern [CITED]
- [Next.js Vitest testing guide](https://nextjs.org/docs/app/guides/testing/vitest) — setup, async RSC limitation [CITED]
- [Next.js Playwright testing guide](https://nextjs.org/docs/pages/guides/testing/playwright) — E2E setup [CITED]

### Tertiary (LOW confidence — not used for decisions)
- Various blog posts on Next.js JWT auth patterns [NOT used — official docs sufficient]

---

## Metadata

**Confidence breakdown:**
- Event emission audit: HIGH — derived from reading actual Phase 2–4 SUMMARY files (project source of truth)
- Standard stack (backend): HIGH — all BOM-managed, patterns proven in Phases 2–4
- Standard stack (frontend): MEDIUM — Next.js 14 patterns confirmed via official docs; specific versions not npm-verified in this session
- Architecture patterns: HIGH (backend), MEDIUM (admin-web — first Next.js app in project)
- Pitfalls: HIGH (backend), MEDIUM (Next.js)

**Research date:** 2026-06-21
**Valid until:** 2026-07-21 (stable stack, 30-day window)
