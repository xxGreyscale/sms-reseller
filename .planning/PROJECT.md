# SMS Reseller Platform (sms-reseller)

## What This Is

A NIDA-verified bulk SMS reseller platform built for small organizations in Tanzania. Users register with their National ID (NIDA), purchase prepaid SMS bundles via mobile money (Azampay), manage their contacts, and dispatch bulk messages — all through a simple, mobile-first web interface. The platform acts as a reseller between an upstream SMS provider and small-batch buyers who need simplicity and trust over raw API access.

## Core Value

Small organizations can send bulk SMS to their members in minutes — verified, trusted, and without needing any technical knowledge.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can register and verify identity via NIDA (National ID)
- [ ] User can purchase prepaid SMS bundles via Azampay mobile money
- [ ] User receives 50 free SMS on successful NIDA verification
- [ ] User can manage contacts and contact groups
- [ ] User can import contacts via CSV
- [ ] User can create and send bulk SMS campaigns
- [ ] User can schedule campaigns for future delivery
- [ ] User can track delivery status of campaigns
- [ ] User receives low-credit alerts
- [ ] User can register a custom sender ID (subject to approval)
- [ ] Admin can manage users, sender IDs, bundles, and audit logs
- [ ] System handles duplicate contact removal automatically
- [ ] System notifies user of payment confirmation and credit top-up

### Out of Scope

- Public REST API — post-MVP; target customer is non-technical, API is their edge not ours
- Credit sharing between users — low value for target segment
- Two-way SMS — v2 plan
- Real-time chat — not in domain
- OAuth login — NIDA is the identity layer; email/password suffices for v1
- Contact groups + CSV import in mobile app — deferred to post-launch; flat contact list only in MVP Flutter app
- Campaign scheduling in mobile app — deferred; immediate send only at MVP Flutter app

## Context

### Architecture (MVP — Modular Monolith, LOCKED)

**1 Spring Boot 3 + Java 21 monolith** with 8 strict in-process modules:
- **identity** — NIDA verification, users, sessions, JWT issuance
- **wallet** — SMS credit balances, append-only ledger, reservation pattern
- **catalog** — Bundle definitions (Taster/Starter/Growth/Pro/Scale)
- **payment** — Azampay integration, polling recovery, reconciliation, refunds
- **contact** — Contacts, contact groups, suppressions, CSV import
- **messaging** — Campaigns, messages, sender IDs, templates, upstream SMS provider
- **notification** — Notification log; listens via RabbitMQ / ApplicationEventPublisher
- **admin** — Admin users, audit log, cross-module read views

Module boundary rules (critical for future extraction):
- No cross-module direct DB access — each module owns its own PostgreSQL schema
- Cross-module calls go through `*ModuleService` interfaces (Spring DI), never SQL joins
- Async reactions use RabbitMQ or Spring `ApplicationEventPublisher`
- No cross-schema foreign keys

**3 deployables on DOKS:**
- `sms-platform-api` — the monolith REST API (1 replica MVP, HPA later)
- `sms-platform-worker` — RabbitMQ consumer process (1 replica)
- `admin-web` — minimal Next.js admin panel (1 replica)

**Customer frontend: Flutter mobile app**
- Framework: Flutter (single codebase iOS + Android)
- State: Riverpod | HTTP: Dio | Storage: Hive + shared_preferences
- MVP scope: register+NIDA, login, dashboard, bundle purchase, contacts (flat list), campaign composer (immediate send), campaign history, settings

**Admin frontend: Next.js (minimal)**
- Sender ID approval queue, user search, ledger inspection, refund execution, audit log

Communication: In-process method calls (sync) + RabbitMQ events (async, CloudAMQP)
Auth: JWT issued by identity module, validated via shared security component
Database: PostgreSQL 16 (DO Managed), **1 cluster with 8 schemas** (identity, wallet, catalog, payment, contact, messaging, notification, admin)

**Future state (post-MVP):** Extract to 8 microservices when team grows or a module hits scaling limits. Boundary discipline now makes this a 2-week job per module, not a rewrite.

**Cost at MVP:** ~$40–50/month (vs ~$100–150/month for microservices)

### Infrastructure (Locked)

- DOKS (DigitalOcean Kubernetes), 3 Deployments at MVP (api, worker, admin-web)
- Redis (DO Managed) — cache, locks, OTPs
- Kustomize manifests: base + overlays (dev/staging/prod)
- GitHub Actions CI/CD → ArgoCD post-MVP
- GHCR container registry
- Observability: Loki + Prometheus + Grafana + OpenTelemetry + Sentry
- Terraform for cluster/DB/DNS provisioning
- Local dev: Docker Compose (Postgres, Redis, RabbitMQ)

### Pricing Model

| Bundle  | SMS    | Price (TZS)        | Effective Rate |
|---------|--------|---------------------|----------------|
| Taster  | 50     | FREE (on NIDA verify) | —            |
| Starter | 200    | 3,200               | 16 TZS/SMS    |
| Growth  | 1,000  | 14,500              | 14.5 TZS/SMS  |
| Pro     | 5,000  | 65,000              | 13 TZS/SMS    |
| Scale   | 20,000 | 240,000             | 12 TZS/SMS    |

Credits expire after 12 months; bonus credits expire after 30 days.

### Competitive Position

Only NIDA-verified bulk SMS platform in Tanzania. Primary competitor is NextSMS (no free tier, no KYC, API-first). Our moat: trust (NIDA), simplicity (UX), mobile-first, analytics.

### Open Items (Pre-Implementation Blockers)

- [ ] Confirm wholesale SMS cost from upstream provider (~9–11 TZS assumed)
- [ ] Validate NIDA API access (timeline, cost, eligibility)
- [ ] Sign up for Azampay merchant account
- [ ] Allocate numeric sender ID shortcode with upstream provider
- [ ] Provision DOKS cluster, DO Managed Postgres, DO Managed Redis, CloudAMQP
- [ ] Bootstrap monorepo with root Gradle + GitHub Actions
- [ ] Decide team shape (solo / pair / small team)
- [ ] Identify 5 friendly customers for validation cohort

## Constraints

- **Tech Stack**: Spring Boot 3 + Java 21 (monolith backend), Flutter (customer mobile app), Next.js (admin web) — locked, no deviation
- **Database**: PostgreSQL 16 — 1 cluster, 8 schemas. ACID requirement for wallet/payment
- **Hosting**: DigitalOcean Kubernetes (DOKS) — locked for MVP
- **Payments**: Azampay only at MVP — only aggregator covering M-Pesa/Tigo/Airtel/Halo/AzamPesa in Tanzania
- **Identity**: NIDA only for KYC — core differentiator
- **Replicas**: 1 per Deployment at MVP (api, worker, admin-web), HPA configured for later
- **Target market**: Tanzania only (TZS pricing, NIDA, Azampay, Swahili-aware UX)
- **No code written yet**: Design phase complete, implementation not started

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Modular monolith for MVP | Solo dev, 15 hrs/week — one deployable, lower ops cost (~$40–50/month), faster dev cycle; extract to microservices when justified | — Pending |
| PostgreSQL for all services | ACID for Wallet/Payment reservation pattern; one technology to learn deeply | — Pending |
| RabbitMQ for async events | Decoupled inter-service communication; managed via CloudAMQP | — Pending |
| NIDA verification as acquisition hook | Only KYC-verified competitor; free 50 SMS on verify drives signups | — Pending |
| Prepaid bundles (no subscriptions) | Matches target customer's event-driven, unpredictable send patterns | — Pending |
| Kustomize for manifests | Structured overlay system for dev/staging/prod without Helm complexity | — Pending |
| Defer public API to post-MVP | Target customer is non-technical; API is NextSMS's edge, not ours | — Pending |
| Undercut NextSMS at Growth tier | Beachhead users (mid-volume small biz/NGO) land at Growth; 14.5 vs 16 TZS | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-18 after initialization*
