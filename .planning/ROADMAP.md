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
- [ ] **Phase 2: Identity & Auth** - User registration, NIDA async verification, JWT auth, sessions, password reset, default sender ID
- [ ] **Phase 3: Wallet & Payments** - Append-only credit ledger with pessimistic reservation, Azampay STK push with outbox and idempotent callbacks
- [ ] **Phase 4: Contacts & Messaging** - Contact CRUD/CSV import/dedup, bulk SMS campaigns, credit reservation, DLX retry, sender ID lifecycle
- [ ] **Phase 5: Notifications, Admin & Analytics** - RabbitMQ event fan-out to notification log, Next.js admin panel, cross-module read views, analytics queries
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
**Plans**: TBD
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
**Plans**: TBD
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
**Plans**: TBD
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
**Plans**: TBD
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
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phase 0 runs as a parallel background track. Coding phases execute: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. Pre-Implementation Blockers | 0/TBD | Not started | - |
| 1. Foundation | 1/1 | Complete   | 2026-06-19 |
| 2. Identity & Auth | 0/TBD | Not started | - |
| 3. Wallet & Payments | 0/TBD | Not started | - |
| 4. Contacts & Messaging | 0/TBD | Not started | - |
| 5. Notifications, Admin & Analytics | 0/TBD | Not started | - |
| 6. Flutter Mobile App | 0/TBD | Not started | - |

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
