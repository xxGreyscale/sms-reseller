# Requirements: SMS Reseller Platform (open-desk)

**Defined:** 2026-06-18
**Core Value:** Small organizations can send bulk SMS to their members in minutes — verified, trusted, and without needing any technical knowledge.

---

## v1 Requirements

### Identity & Authentication

- [x] **IDEN-01**: User can register with phone number and email
- [x] **IDEN-02**: User can verify identity via NIDA National ID — flow returns PENDING state immediately (async, never blocks on NIDA latency)
- [x] **IDEN-03**: User receives 50 free SMS credits on successful NIDA verification
- [x] **IDEN-04**: User can log in with email and password
- [x] **IDEN-05**: User session persists across app restarts (JWT + refresh token)
- [x] **IDEN-06**: User can log out and revoke session
- [x] **IDEN-07**: User can reset password via email link
- [x] **IDEN-08**: System gracefully degrades when NIDA API is unavailable — user stays in PENDING_VERIFICATION state, retried in background

### Wallet & Credits

- [x] **WLET-01**: User can view current SMS credit balance on every screen
- [x] **WLET-02**: User can view full credit transaction history (append-only ledger)
- [x] **WLET-03**: System prevents balance from going below zero using credit reservation before send (pessimistic lock — `SELECT FOR UPDATE`)
- [x] **WLET-04**: User receives low-credit alert when balance drops below configurable threshold
- [x] **WLET-05**: User receives 7-day warning before purchased credits expire
- [x] **WLET-06**: Purchased credits expire after 12 months from purchase date
- [x] **WLET-07**: Bonus credits (NIDA grant, promotions) expire after 30 days

### Payments

- [x] **PYMT-01**: User can view available SMS bundles with prices (Taster FREE / Starter / Growth / Pro / Scale)
- [x] **PYMT-02**: User can purchase a bundle via Azampay mobile money (M-Pesa, Tigo Pesa, Airtel Money, Halo Pesa, AzamPesa)
- [x] **PYMT-03**: Payment flow shows 2-minute countdown during Azampay STK push — explicit timeout state (not infinite spinner)
- [x] **PYMT-04**: User receives confirmation screen and notification after successful payment with credits credited
- [x] **PYMT-05**: User can view full payment history with statuses
- [x] **PYMT-06**: System processes Azampay callbacks idempotently — duplicate callbacks never double-credit wallet
- [x] **PYMT-07**: System marks unresolved STK push payments as EXPIRED after timeout and surfaces this to user
- [x] **PYMT-08**: System issues refunds for payments linked to failed campaigns

### Contact Management

- [x] **CONT-01**: User can add individual contacts manually (name + phone number)
- [x] **CONT-02**: User can edit existing contacts
- [x] **CONT-03**: User can delete contacts
- [x] **CONT-04**: User can organize contacts into named groups
- [x] **CONT-05**: User can import contacts from CSV file
- [x] **CONT-06**: System automatically deduplicates phone numbers on import and within groups
- [x] **CONT-07**: System normalizes Tanzanian phone numbers to E.164 format (07xx / 06xx → +255XXXXXXXXX)
- [x] **CONT-08**: User can add phone numbers to a suppression list — suppressed numbers are excluded from all future campaigns
- [x] **CONT-09**: System shows import summary screen (X contacts imported, Y duplicates skipped, Z invalid)

### Messaging & Campaigns

- [x] **MESG-01**: User can create a bulk SMS campaign targeting one or more contact groups
- [x] **MESG-02**: Campaign composer shows real-time character counter with SMS part count and UCS-2 encoding warning for non-GSM characters
- [x] **MESG-03**: System reserves SMS credits before campaign is queued — campaign cannot transition to QUEUED if credits are insufficient
- [x] **MESG-04**: User can schedule a campaign for a specific future date and time
- [x] **MESG-05**: User can cancel a scheduled campaign before it dispatches
- [x] **MESG-06**: User can view campaign history with aggregate status (sent / delivered / failed counts)
- [x] **MESG-07**: User can view per-message delivery status within a campaign
- [x] **MESG-08**: User sees a post-send confirmation screen showing credits deducted and messages queued
- [x] **MESG-09**: System automatically excludes suppressed numbers from campaign recipients
- [x] **MESG-10**: System retries failed messages via dead letter queue; credits are refunded for permanently undeliverable messages

### Sender ID Management

- [x] **SNDR-01**: User is assigned a default numeric sender ID (shortcode) at registration
- [x] **SNDR-02**: User can request a custom alphanumeric sender ID (subject to admin and TCRA approval)
- [x] **SNDR-03**: Admin can approve or reject sender ID requests from the admin panel
- [x] **SNDR-04**: User receives notification when their sender ID request is approved or rejected

### Notifications

- [ ] **NOTF-01**: User receives in-app notification on successful NIDA verification
- [ ] **NOTF-02**: User receives in-app and push notification on successful payment and credit top-up
- [ ] **NOTF-03**: User receives low-credit alert notification when balance drops below threshold
- [ ] **NOTF-04**: User receives 7-day credit expiry warning notification
- [x] **NOTF-05**: User receives campaign completion summary notification (delivered / failed counts)
- [ ] **NOTF-06**: User receives sender ID approval or rejection notification

### Admin Panel

- [x] **ADMN-01**: Admin can log in with separate admin credentials (distinct JWT, ROLE_ADMIN)
- [x] **ADMN-02**: Admin can search and view user accounts
- [x] **ADMN-03**: Admin can inspect any user's credit ledger history
- [ ] **ADMN-04**: Admin can view and process the sender ID approval queue (approve / reject with reason)
- [x] **ADMN-05**: Admin can execute manual refunds for users
- [ ] **ADMN-06**: Admin can view a full audit log of platform actions
- [ ] **ADMN-07**: Admin can view and update SMS bundle catalog (pricing, SMS count)

### Analytics

- [x] **ANLX-01**: User can view campaign delivery statistics (sent, delivered, failed rates per campaign)
- [x] **ANLX-02**: User can view credit usage history over time with spend trend
- [x] **ANLX-03**: User can view operator-level delivery rates (M-Pesa vs Tigo vs Airtel etc.)

### Infrastructure & Developer Experience

- [ ] **INFR-01**: System deploys to DOKS with 3 Kubernetes Deployments (api, worker, admin-web) via Kustomize
- [ ] **INFR-02**: Local development works with a single `docker compose up` (Postgres, Redis, RabbitMQ)
- [ ] **INFR-03**: CI pipeline (GitHub Actions) runs tests and builds images on every PR; deploys to dev on merge to main
- [ ] **INFR-04**: System emits structured JSON logs (Loki), metrics (Prometheus → Grafana), and distributed traces (OpenTelemetry + Sentry)
- [ ] **INFR-05**: All secrets managed via Kubernetes Secrets (Sealed Secrets or ESO) — never committed to Git

### Flutter Mobile App

- [ ] **MOBL-01**: Flutter app has splash screen and onboarding flow
- [ ] **MOBL-02**: Flutter app supports NIDA registration + PENDING verification state with clear status screen
- [ ] **MOBL-03**: Flutter app supports login with JWT session persistence (Hive storage)
- [ ] **MOBL-04**: Flutter app shows dashboard: SMS balance, recent campaigns, quick-send shortcut
- [ ] **MOBL-05**: Flutter app supports bundle purchase with Azampay STK push and 2-minute countdown UI
- [ ] **MOBL-06**: Flutter app supports flat contact list with manual add (no groups or CSV import at MVP)
- [ ] **MOBL-07**: Flutter app supports campaign composer for immediate send (no scheduling at MVP)
- [ ] **MOBL-08**: Flutter app shows campaign history list and campaign detail screen
- [ ] **MOBL-09**: Flutter app is published to Google Play and Apple App Store with required metadata

---

## v2 Requirements

### API & Integrations

- **APIV2-01**: Public REST API for programmatic bulk SMS (post-MVP — not our target customer's need at MVP)
- **APIV2-02**: Webhook delivery for message status updates (for API users)

### Mobile App Expansion

- **MOBL-V2-01**: Contact groups and group management in Flutter app
- **MOBL-V2-02**: CSV contact import in Flutter app
- **MOBL-V2-03**: Campaign scheduling in Flutter app
- **MOBL-V2-04**: Full analytics dashboard in Flutter app

### Advanced Features

- **ADV-V2-01**: Two-way SMS (inbound message handling)
- **ADV-V2-02**: Message templates saved as drafts
- **ADV-V2-03**: Delivery report CSV export
- **ADV-V2-04**: Swahili UI localization
- **ADV-V2-05**: PWA manifest + service worker for admin web app

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| OAuth / social login | NIDA KYC is the identity layer; social login undermines the verified-identity moat |
| Credit sharing between users | Low value for the target segment (small organizations); adds ledger complexity |
| Real-time live delivery push | 30-second polling is sufficient for non-technical users; WebSocket adds infra complexity |
| White-label reseller accounts | Not the product we're building at MVP |
| Subscription / recurring billing | Target users have event-driven, unpredictable send volumes — prepaid bundles fit better |
| WhatsApp / RCS channel | Separate product surface; out of scope until SMS proves the model |
| Native iOS-only or Android-only app | Flutter gives both; native-only is unnecessary cost |
| Multi-currency pricing | TZS only at MVP; Tanzania-market focus |

---

## Traceability

_Populated by roadmapper on 2026-06-18._

| Requirement | Phase | Status |
|-------------|-------|--------|
| IDEN-01 | Phase 2 | Complete |
| IDEN-02 | Phase 2 | Complete |
| IDEN-03 | Phase 2 | Complete |
| IDEN-04 | Phase 2 | Complete |
| IDEN-05 | Phase 2 | Complete |
| IDEN-06 | Phase 2 | Complete |
| IDEN-07 | Phase 2 | Complete |
| IDEN-08 | Phase 2 | Complete |
| WLET-01 | Phase 3 | Complete |
| WLET-02 | Phase 3 | Complete |
| WLET-03 | Phase 3 | Complete |
| WLET-04 | Phase 3 | Complete |
| WLET-05 | Phase 3 | Complete |
| WLET-06 | Phase 3 | Complete |
| WLET-07 | Phase 3 | Complete |
| PYMT-01 | Phase 3 | Complete |
| PYMT-02 | Phase 3 | Complete |
| PYMT-03 | Phase 3 | Complete |
| PYMT-04 | Phase 3 | Complete |
| PYMT-05 | Phase 3 | Complete |
| PYMT-06 | Phase 3 | Complete |
| PYMT-07 | Phase 3 | Complete |
| PYMT-08 | Phase 3 | Complete |
| CONT-01 | Phase 4 | Complete |
| CONT-02 | Phase 4 | Complete |
| CONT-03 | Phase 4 | Complete |
| CONT-04 | Phase 4 | Complete |
| CONT-05 | Phase 4 | Complete |
| CONT-06 | Phase 4 | Complete |
| CONT-07 | Phase 4 | Complete |
| CONT-08 | Phase 4 | Complete |
| CONT-09 | Phase 4 | Complete |
| MESG-01 | Phase 4 | Complete |
| MESG-02 | Phase 4 | Complete |
| MESG-03 | Phase 4 | Complete |
| MESG-04 | Phase 4 | Complete |
| MESG-05 | Phase 4 | Complete |
| MESG-06 | Phase 4 | Complete |
| MESG-07 | Phase 4 | Complete |
| MESG-08 | Phase 4 | Complete |
| MESG-09 | Phase 4 | Complete |
| MESG-10 | Phase 4 | Complete |
| SNDR-01 | Phase 2 | Complete |
| SNDR-02 | Phase 4 | Complete |
| SNDR-03 | Phase 4 | Complete |
| SNDR-04 | Phase 4 | Complete |
| NOTF-01 | Phase 5 | Pending |
| NOTF-02 | Phase 5 | Pending |
| NOTF-03 | Phase 5 | Pending |
| NOTF-04 | Phase 5 | Pending |
| NOTF-05 | Phase 5 | Complete |
| NOTF-06 | Phase 5 | Pending |
| ADMN-01 | Phase 5 | Complete |
| ADMN-02 | Phase 5 | Complete |
| ADMN-03 | Phase 5 | Complete |
| ADMN-04 | Phase 5 | Pending |
| ADMN-05 | Phase 5 | Complete |
| ADMN-06 | Phase 5 | Pending |
| ADMN-07 | Phase 5 | Pending |
| ANLX-01 | Phase 5 | Complete |
| ANLX-02 | Phase 5 | Complete |
| ANLX-03 | Phase 5 | Complete |
| INFR-01 | Phase 1 | Pending |
| INFR-02 | Phase 1 | Pending |
| INFR-03 | Phase 1 | Pending |
| INFR-04 | Phase 1 | Pending |
| INFR-05 | Phase 1 | Pending |
| MOBL-01 | Phase 6 | Pending |
| MOBL-02 | Phase 6 | Pending |
| MOBL-03 | Phase 6 | Pending |
| MOBL-04 | Phase 6 | Pending |
| MOBL-05 | Phase 6 | Pending |
| MOBL-06 | Phase 6 | Pending |
| MOBL-07 | Phase 6 | Pending |
| MOBL-08 | Phase 6 | Pending |
| MOBL-09 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 76 total (note: header previously stated 68 — actual count from traceability enumeration is 76)
- Mapped to phases: 76 / 76
- Unmapped: 0

| Phase | Count | Requirements |
|-------|-------|-------------|
| Phase 0 | 0 | (external procurement — no coded requirements) |
| Phase 1 | 5 | INFR-01 through INFR-05 |
| Phase 2 | 9 | IDEN-01 through IDEN-08, SNDR-01 |
| Phase 3 | 15 | WLET-01 through WLET-07, PYMT-01 through PYMT-08 |
| Phase 4 | 22 | CONT-01 through CONT-09, MESG-01 through MESG-10, SNDR-02 through SNDR-04 |
| Phase 5 | 16 | NOTF-01 through NOTF-06, ADMN-01 through ADMN-07, ANLX-01 through ANLX-03 |
| Phase 6 | 9 | MOBL-01 through MOBL-09 |

---
*Requirements defined: 2026-06-18*
*Last updated: 2026-06-18 — traceability populated by roadmapper; requirement count corrected to 76*
