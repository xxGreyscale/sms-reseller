# Roadmap: SMS Reseller Platform (open-desk)

## Overview

The platform is built in six implementation phases (plus one parallel procurement phase) that follow the strict module dependency chain of the modular monolith. Foundation and shared infrastructure come first, then identity and auth, then the financial layer (catalog + wallet + payments), then contacts and messaging, then the read-aggregate layer, and finally the two frontends.

**Mock-first development:** All three external integrations (NIDA, Azampay, upstream SMS provider) are built behind interfaces from day one. Stub implementations auto-simulate behaviour in dev/staging. Real implementations are wired in via Spring profiles when credentials arrive — no application logic changes required. This means procurement never blocks coding and the full platform can be demoed before a single external credential exists.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 0: Pre-Implementation Procurement** - External procurement runs as a parallel background track; never blocks coding phases
- [x] **Phase 1: Foundation** - Monorepo skeleton, shared libraries, Docker Compose local dev, CI pipeline, infrastructure manifests (completed 2026-06-19)
- [x] **Phase 2: Identity & Auth** - User registration, NIDA async verification, JWT auth, sessions, password reset, default sender ID (completed 2026-06-19)
- [x] **Phase 3: Wallet & Payments** - Append-only credit ledger with pessimistic reservation, Azampay STK push with outbox and idempotent callbacks (completed 2026-06-20)
- [x] **Phase 4: Contacts & Messaging** - Contact CRUD/CSV import/dedup, bulk SMS campaigns, credit reservation, DLX retry, sender ID lifecycle (completed 2026-06-21)
- [x] **Phase 5: Notifications, Admin & Analytics** - RabbitMQ event fan-out to notification log, Next.js admin panel, cross-module read views, analytics queries (completed 2026-06-21)
- [ ] **Phase 6: Flutter Mobile App** - Full Flutter customer app from onboarding through campaign history, published to both stores

## Phase Details

### Phase 0: Pre-Implementation Procurement
**Goal**: External procurement runs as a background track in parallel with all coding phases — none of phases 1–6 wait for it
**Depends on**: Nothing — runs concurrently with all other phases
**Requirements**: None (all external procurement actions)
**Note**: Coding is never blocked by this phase. Stub implementations cover NIDA, Azampay, and the SMS provider until real credentials arrive. When they do, swap one Spring `@Profile` bean — no application logic changes.
**Success Criteria** (what must be TRUE when complete):
  1. NIDA API access confirmed with endpoint contract, auth method, rate limits, and sandbox/test NINs
  2. Azampay merchant account live with sandbox credentials, webhook delivery mechanism confirmed, and HMAC signature scheme documented
  3. Upstream SMS provider selected with signed wholesale rate (~9–11 TZS/SMS), numeric shortcode allocated, DLR webhook contract and `clientRef` idempotency support confirmed
  4. DOKS cluster, DO Managed PostgreSQL, DO Managed Redis, and CloudAMQP provisioned with connection strings ready for production
  5. Terraform scripts for cluster/DB/DNS provisioning are written and validated
**Plans**: TBD

### Phase 1: Foundation
**Goal**: A single `docker compose up` starts the full local dev stack and a passing CI pipeline builds and pushes images on every PR
**Depends on**: Nothing — this is the unblocked starting point for coding
**Requirements**: INFR-01, INFR-02, INFR-03, INFR-04, INFR-05
**Success Criteria** (what must be TRUE):
  1. `docker compose up` starts Postgres (8 schemas auto-created via Flyway), Redis, and RabbitMQ with no manual steps
  2. GitHub Actions CI pipeline runs on every PR: compiles, runs unit tests, builds Docker image, pushes to GHCR
  3. Kustomize base + dev/staging/prod overlays exist for api, worker, and admin-web Deployments with correct startup/liveness/readiness probes and `-XX:MaxRAMPercentage=75`
  4. All secrets are referenced via Kubernetes Secret manifests (Sealed Secrets or ESO); no credential appears in Git
  5. OpenTelemetry collector, Prometheus scrape config, Loki shipper, and Sentry DSN are wired into the shared-observability lib and emit data locally
**Plans**: TBD
**UI hint**: no

### Phase 2: Identity & Auth
**Goal**: A verified user can register, complete async NIDA verification, log in, manage their session, reset a forgotten password, and be assigned a sender ID — and every other module can trust the JWTs this module issues
**Scope note**: The bundle catalog (definitions + read API) is owned by Phase 3 via PYMT-01, not this phase. Phase 2 is identity, auth, and sender-ID assignment only.
**Depends on**: Phase 1
**Mock-first**: `NidaVerificationService` interface built with two implementations — `StubNidaVerificationService` (auto-verifies after 3s delay, active in dev/staging via `@Profile("stub")`) and `RealNidaVerificationService` (wired via `@Profile("prod")` when access arrives). Full NIDA flow is testable and demoable from day one.
**Requirements**: IDEN-01, IDEN-02, IDEN-03, IDEN-04, IDEN-05, IDEN-06, IDEN-07, IDEN-08, SNDR-01
**Success Criteria** (what must be TRUE):
  1. User can register with phone + email, submit their NIN, and immediately receive a PENDING_VERIFICATION status without the request blocking on NIDA latency (async + circuit breaker enforced)
  2. When NIDA verifies the NIN (or the background retry succeeds), the user's status transitions to VERIFIED and they are assigned a default numeric sender ID shortcode
  3. User can log in, receive a 15-minute access JWT + 7-day refresh token, and stay logged in across app restarts without re-authenticating
  4. User can log out (session revoked) and reset a forgotten password via email link
  5. All 8 downstream modules can validate the JWT issued by this module using the shared-security library — no service needs to call identity at runtime for token validation
**Plans**: 6 plans (5 waves)
- [x] 02-01-PLAN.md — Wave 0: test infra (Testcontainers PG16+Redis7), build deps, RSA fixture, one plain-stub test per requirement + cross-module JWT contract
- [x] 02-02-PLAN.md — Wave 1: asymmetric JWT core (issuer + shared-security validator), User aggregate + V1 migration, security/redis/async config
- [x] 02-03-PLAN.md — Wave 2: registration (PENDING immediately), async NIDA stub/real + degraded retry, VerificationFinalizer seam (IDEN-01/02/08)
- [x] 02-04-PLAN.md — Wave 2: email+password login, opaque refresh tokens in Redis (rotation + reuse detection), lockout, logout revoke-current (IDEN-04/05/06)
- [x] 02-06-PLAN.md — Wave 3: verification finalize TX (VERIFIED flip + numeric sender ID + UserVerified(50) outbox + RabbitMQ relay) (IDEN-03, SNDR-01)
- [x] 02-05-PLAN.md — Wave 4: password reset via email link (single-use TTL token, mock-first email), revoke-all sessions (IDEN-07)
**UI hint**: no

### Phase 3: Wallet & Payments
**Goal**: Users can purchase SMS bundles via Azampay mobile money and their credit balance is atomically updated through the append-only ledger, with zero possibility of double-crediting or negative balance
**Depends on**: Phase 2
**Mock-first**: `PaymentGateway` interface with `StubPaymentGateway` (simulates STK push with configurable success/failure/timeout outcomes, active via `@Profile("stub")`) and `AzampayPaymentGateway` (wired via `@Profile("prod")` when merchant account arrives). Full payment flow including countdown UI and EXPIRED state is demoable before Azampay credentials exist.
**Requirements**: WLET-01, WLET-02, WLET-03, WLET-04, WLET-05, WLET-06, WLET-07, PYMT-01, PYMT-02, PYMT-03, PYMT-04, PYMT-05, PYMT-06, PYMT-07, PYMT-08
**Success Criteria** (what must be TRUE):
  1. User can view available bundle catalog (Taster FREE / Starter / Growth / Pro / Scale) and initiate a purchase — the STK push appears on their phone within 5 seconds and a 2-minute countdown is shown
  2. Successful payment credits the wallet exactly once regardless of how many times the Azampay callback is delivered (idempotent handler + transactional outbox confirmed)
  3. If the STK push times out or the user declines, the payment is marked EXPIRED/FAILED and the user sees a clear error — no infinite spinner
  4. User can view their full credit balance, transaction history (append-only ledger), and payment history at any time
  5. System refuses any credit reservation that would take available balance below zero (`SELECT FOR UPDATE` pessimistic lock verified), and credits expire correctly at 12 months (purchased) and 30 days (bonus)
**Plans**: 6 plans (4 waves)
- [x] 03-01-PLAN.md — Wave 0: both-service build deps + Testcontainers bases (PG16/Redis/RabbitMQ) + 14 placeholder ITs (one per requirement)
- [x] 03-02-PLAN.md — Wave 1: wallet ledger core — lot-based append-only credit_lots/credit_transactions, derived balance, expiry-soonest-first SELECT FOR UPDATE reservation (WLET-02/03/06/07)
- [x] 03-03-PLAN.md — Wave 1: payment foundation — Flyway-seeded bundle catalog + read API, Payment entity/state-machine + single-pending index, PaymentGateway stub, security/AMQP config (PYMT-01)
- [x] 03-04-PLAN.md — Wave 2: wallet UserVerified consumer (idempotent 50-credit bonus grant) + balance/transaction-history API (WLET-01/02)
- [x] 03-05-PLAN.md — Wave 2: payment flow — purchase initiation (single-pending), idempotent callback (PENDING+EXPIRED→SUCCESS), 2-min EXPIRED sweep, PaymentConfirmed outbox, history (PYMT-02/03/04/05/06/07)
- [x] 03-06-PLAN.md — Wave 3: wallet PaymentConfirmed consumer (12-mo purchased grant) + idempotent refund mechanism + low-credit/expiry-warning sweeps + Azampay reconciliation + prod gateway (PYMT-08, WLET-04/05)
**UI hint**: no

### Phase 4: Contacts & Messaging
**Goal**: Users can manage their contact lists and send verified bulk SMS campaigns with guaranteed credit reservation before dispatch, DLX retry for failed sends, and accurate per-message delivery tracking
**Depends on**: Phase 3
**Mock-first**: `SmsProvider` interface with `StubSmsProvider` (records sends in-memory, simulates delivery receipts after configurable delay, active via `@Profile("stub")`) and `RealSmsProvider` (wired via `@Profile("prod")` when upstream provider is contracted). Full campaign send + delivery tracking is demoable with realistic data before any SMS provider is signed.
**Requirements**: CONT-01, CONT-02, CONT-03, CONT-04, CONT-05, CONT-06, CONT-07, CONT-08, CONT-09, MESG-01, MESG-02, MESG-03, MESG-04, MESG-05, MESG-06, MESG-07, MESG-08, MESG-09, MESG-10, SNDR-02, SNDR-03, SNDR-04
**Success Criteria** (what must be TRUE):
  1. User can add, edit, and delete individual contacts, import from CSV with automatic E.164 normalization, deduplication, and an import summary screen showing counts imported / duplicates skipped / invalid
  2. User can organize contacts into named groups, add numbers to a suppression list, and suppressed numbers are silently excluded from all future campaigns
  3. User can create and send an immediate bulk SMS campaign — credits are reserved (not deducted) before the campaign reaches QUEUED state, the campaign is refused with a clear error if credits are insufficient
  4. User can schedule a campaign for a future time, cancel it before dispatch, and view per-campaign and per-message delivery status (sent / delivered / failed counts)
  5. Failed messages are retried via dead letter queue with exponential backoff; permanently undeliverable messages result in credit refund back to the wallet and the user sees the correct final counts
  6. User can request a custom alphanumeric sender ID; admin can approve or reject it from the admin panel; user is notified of the outcome
**Plans**: 8 plans (5 waves)
- [x] 04-01-PLAN.md — Wave 0: contact + messaging module deps (commons-csv, libphonenumber), Testcontainers IT bases, one placeholder failing test per requirement
- [x] 04-02-PLAN.md — Wave 1: contact-service core — Contact/Group/Suppression entities + migrations + CRUD API + IDOR guard (CONT-01/02/03/04/08)
- [x] 04-04-PLAN.md — Wave 1: messaging foundation — campaign/message/sender-id entities + migrations, SmsProvider stub, GSM-7/UCS-2 encoder, quorum+DLX queue topology, outbox (MESG-01/02)
- [x] 04-07-PLAN.md — Wave 1: wallet additions — per-lot allocation in ReservationResult, consumeFromLot/releaseFromLot, idempotent MessagingEventConsumer (CONSUME/RELEASE/REFUND) (MESG-10 wallet side)
- [x] 04-03-PLAN.md — Wave 2: CSV import — libphonenumber E.164 normalization + dedup + import summary (CONT-05/06/07/09)
- [x] 04-05-PLAN.md — Wave 2: send pipeline — recipient expansion + suppression, sync reserve-before-QUEUED, per-message lot snapshot, publish, SendMessageConsumer accept→CONSUME/release→RELEASE (MESG-03/08/09)
- [x] 04-06-PLAN.md — Wave 3: DLX retry ladder + DeadLetterConsumer refund + delivery-receipt ingestion + per-message/aggregate status (MESG-06/07/10)
- [x] 04-08-PLAN.md — Wave 4: scheduled dispatch + cancel + sender-ID request/approve/reject state machine + SenderIdDecided event (MESG-04/05, SNDR-02/03/04)
**UI hint**: no

### Phase 5: Notifications, Admin & Analytics
**Goal**: All user-facing notifications fire reliably from domain events, the Next.js admin panel gives operators full platform visibility and control, and users can view delivery and spend analytics
**Depends on**: Phase 4
**Requirements**: NOTF-01, NOTF-02, NOTF-03, NOTF-04, NOTF-05, NOTF-06, ADMN-01, ADMN-02, ADMN-03, ADMN-04, ADMN-05, ADMN-06, ADMN-07, ANLX-01, ANLX-02, ANLX-03
**Success Criteria** (what must be TRUE):
  1. Users receive in-app notifications for all key events: NIDA verified, payment confirmed, low-credit alert, credit expiry warning (7-day), campaign completion summary, and sender ID decision — each fires exactly once regardless of event replay
  2. Admin can log in with ROLE_ADMIN credentials (separate JWT), search users, inspect any user's ledger, view the sender ID approval queue, approve/reject with reason, and execute manual refunds
  3. Admin can view a full audit log of all platform actions and manage the bundle catalog (pricing, SMS count per bundle)
  4. User can view per-campaign delivery statistics (sent / delivered / failed rates), credit usage over time with spend trend, and operator-level delivery rates (M-Pesa vs Tigo vs Airtel etc.)
**Plans**: 9 plans (5 waves)
- [x] 05-01-PLAN.md — Wave 0: notification + admin Spring modules + Testcontainers bases, 15 RED placeholder ITs (all 16 reqs), admin-web Next.js 14 scaffold + Tailwind 3 + shadcn 3.5 + Vitest/Playwright harness + Dockerfile
- [x] 05-02-PLAN.md — Wave 1: messaging-service — D-12 CampaignCompleted outbox emit + D-13 operator column/CarrierResolver, ANLX-01/03 analytics (NOTF-05 prereq)
- [x] 05-03-PLAN.md — Wave 1: identity-service — issueAdminToken (60-min ROLE_ADMIN), admin login + seeded admin (Flyway placeholder), user search (ADMN-01/02)
- [x] 05-04-PLAN.md — Wave 1: wallet-service — admin ledger inspection (ADMN-03), credit-usage trend (ANLX-02), admin-reachable refund (ADMN-05)
- [x] 05-05-PLAN.md — Wave 1: payment-service — ADMIN bundle catalog CRUD + validation (ADMN-07)
- [x] 05-06-PLAN.md — Wave 2: notification-service — 4 passive idempotent consumers (6 events), notification log + feed API, NotificationChannel/StubPushChannel (NOTF-01..06)
- [x] 05-07-PLAN.md — Wave 2: admin-service — dual-source append-only audit log (mutations + domain-event consumer) + ADMIN viewer (ADMN-06)
- [x] 05-08-PLAN.md — Wave 3: admin-web — httpOnly cookie login + middleware + shell/sidebar + user search + ledger screens (ADMN-01/02/03 UI)
- [x] 05-09-PLAN.md — Wave 4: admin-web — sender-ID queue, manual refund, bundle catalog, audit-log screens (ADMN-04/05/06/07 UI)
**UI hint**: yes

### Phase 6: Flutter Mobile App
**Goal**: The complete Flutter customer app is published to Google Play and Apple App Store, covering the full user journey from onboarding through NIDA verification, bundle purchase, contact management, campaign sending, and history
**Depends on**: Phase 5
**Requirements**: MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-05, MOBL-06, MOBL-07, MOBL-08, MOBL-09
**Success Criteria** (what must be TRUE):
  1. User can install the app, complete the onboarding flow, register with phone + email, submit their NIN, and see a clear PENDING verification status screen with automatic status polling
  2. User can log in, view the dashboard (SMS balance, recent campaigns, quick-send shortcut) and the session persists across app restarts via Hive-stored JWT
  3. User can purchase an SMS bundle via Azampay — the STK push countdown (2-minute timer) displays correctly and the balance updates automatically after confirmation
  4. User can manage a flat contact list (manual add), compose an immediate-send bulk SMS campaign, and view campaign history with per-campaign detail
  5. App is live on Google Play and Apple App Store with all required metadata (icon, screenshots, privacy policy, store description)
**Plans**: 12 plans (6 waves)
- [x] 06-01-PLAN.md — Wave 1: Flutter scaffold + l10n (EN+SW) + Dio QueuedInterceptor + go_router guards + Hive/secure-storage + Riverpod auth state + test harness (MOBL placeholder map)
- [x] 06-02-PLAN.md — Wave 1: backend D-11 — GET /api/v1/payments/{id} owner-scoped status endpoint (payment-service)
- [x] 06-03-PLAN.md — Wave 1: backend D-12 — campaign contactIds[] targeting (messaging + contact-service recipients-by-ids)
- [x] 06-04-PLAN.md — Wave 1: backend D-13 GET /auth/me + D-14 PATCH notifications/{id}/read (identity + notification services)
- [x] 06-05-PLAN.md — Wave 2: splash + onboarding (MOBL-01) + register/NIDA + login session persistence (MOBL-03)
- [x] 06-06-PLAN.md — Wave 2: NIDA PENDING walled state + 10s /auth/me auto-poll → VERIFIED (MOBL-02, D-09)
- [ ] 06-07-PLAN.md — Wave 3: dashboard + balance cache-read/online-write + recent campaigns + shared widget kit + NavigationBar (MOBL-04)
- [ ] 06-08-PLAN.md — Wave 4: bundle catalog + STK 2-min countdown + payment status polling (MOBL-05)
- [ ] 06-09-PLAN.md — Wave 4: flat contact list (cache) + online-only add + delete (MOBL-06)
- [ ] 06-11-PLAN.md — Wave 4: notification feed 30s polling + client-derived unread badge (D-01)
- [ ] 06-10-PLAN.md — Wave 5: campaign composer (contactIds send) + GSM-7/UCS-2 counter + history + detail (MOBL-07/08)
- [ ] 06-12-PLAN.md — Wave 6: e2e integration test + signing + store metadata + CI + submission checkpoint (MOBL-09, autonomous:false)
**UI hint**: yes

## Progress

**Execution Order:**
Phase 0 runs as a parallel background track. Coding phases execute: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. Pre-Implementation Blockers | 0/TBD | Not started | - |
| 1. Foundation | 1/1 | Complete   | 2026-06-19 |
| 2. Identity & Auth | 6/6 | Complete   | 2026-06-19 |
| 3. Wallet & Payments | 6/6 | Complete   | 2026-06-20 |
| 4. Contacts & Messaging | 8/8 | Complete   | 2026-06-21 |
| 5. Notifications, Admin & Analytics | 9/9 | Complete   | 2026-06-21 |
| 6. Flutter Mobile App | 6/12 | In Progress|  |

---

## Coverage

**All 76 v1 requirements mapped (note: REQUIREMENTS.md header states 68 but the traceability table enumerates 76 distinct IDs — this roadmap maps all 76):**

| Phase | Requirements |
|-------|-------------|
| Phase 0 | (none — external actions) |
| Phase 1 | INFR-01, INFR-02, INFR-03, INFR-04, INFR-05 |
| Phase 2 | IDEN-01, IDEN-02, IDEN-03, IDEN-04, IDEN-05, IDEN-06, IDEN-07, IDEN-08, SNDR-01 |
| Phase 3 | WLET-01, WLET-02, WLET-03, WLET-04, WLET-05, WLET-06, WLET-07, PYMT-01, PYMT-02, PYMT-03, PYMT-04, PYMT-05, PYMT-06, PYMT-07, PYMT-08 |
| Phase 4 | CONT-01, CONT-02, CONT-03, CONT-04, CONT-05, CONT-06, CONT-07, CONT-08, CONT-09, MESG-01, MESG-02, MESG-03, MESG-04, MESG-05, MESG-06, MESG-07, MESG-08, MESG-09, MESG-10, SNDR-02, SNDR-03, SNDR-04 |
| Phase 5 | NOTF-01, NOTF-02, NOTF-03, NOTF-04, NOTF-05, NOTF-06, ADMN-01, ADMN-02, ADMN-03, ADMN-04, ADMN-05, ADMN-06, ADMN-07, ANLX-01, ANLX-02, ANLX-03 |
| Phase 6 | MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-05, MOBL-06, MOBL-07, MOBL-08, MOBL-09 |

**Total mapped: 76 / 76**

---

## Critical Patterns by Phase

| Phase | Pattern | Risk if Skipped |
|-------|---------|-----------------|
| Phase 2 | Async NIDA verification (PENDING state + Resilience4j circuit breaker + background retry) | Registration timeouts; NIDA thread-pool exhaustion; free credits granted before verification |
| Phase 3 | Pessimistic lock `SELECT FOR UPDATE` on wallet_view; append-only ledger (never mutable balance); `processed_events` idempotency table in wallet consumer | Credit double-spend; negative balance; duplicate credit top-ups |
| Phase 3 | Transactional outbox pattern in payment service; idempotent Azampay callback handler; STK push with 2-minute timeout and EXPIRED state | Silent credit loss on pod crash; double-crediting on retried webhooks |
| Phase 4 | Credit reservation before QUEUED state transition; quorum RabbitMQ queues; DLX + retry with `default-requeue-rejected: false`; idempotent delivery webhook handler | Campaigns sent without credits; message loss on broker restart; poison-message infinite loop |

---
*Roadmap created: 2026-06-18*
*Last updated: 2026-06-18 after initial creation*
