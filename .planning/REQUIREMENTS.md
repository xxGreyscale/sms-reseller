# Requirements: SMS Reseller Platform (open-desk)

**Defined:** 2026-06-18
**Core Value:** Small organizations can send bulk SMS to their members in minutes — verified, trusted, and without needing any technical knowledge.

---

## v1 Requirements

### Identity & Authentication

- [ ] **IDEN-01**: User can register with phone number and email
- [ ] **IDEN-02**: User can verify identity via NIDA National ID — flow returns PENDING state immediately (async, never blocks on NIDA latency)
- [ ] **IDEN-03**: User receives 50 free SMS credits on successful NIDA verification
- [ ] **IDEN-04**: User can log in with email and password
- [ ] **IDEN-05**: User session persists across app restarts (JWT + refresh token)
- [ ] **IDEN-06**: User can log out and revoke session
- [ ] **IDEN-07**: User can reset password via email link
- [ ] **IDEN-08**: System gracefully degrades when NIDA API is unavailable — user stays in PENDING_VERIFICATION state, retried in background

### Wallet & Credits

- [ ] **WLET-01**: User can view current SMS credit balance on every screen
- [ ] **WLET-02**: User can view full credit transaction history (append-only ledger)
- [ ] **WLET-03**: System prevents balance from going below zero using credit reservation before send (pessimistic lock — `SELECT FOR UPDATE`)
- [ ] **WLET-04**: User receives low-credit alert when balance drops below configurable threshold
- [ ] **WLET-05**: User receives 7-day warning before purchased credits expire
- [ ] **WLET-06**: Purchased credits expire after 12 months from purchase date
- [ ] **WLET-07**: Bonus credits (NIDA grant, promotions) expire after 30 days

### Payments

- [ ] **PYMT-01**: User can view available SMS bundles with prices (Taster FREE / Starter / Growth / Pro / Scale)
- [ ] **PYMT-02**: User can purchase a bundle via Azampay mobile money (M-Pesa, Tigo Pesa, Airtel Money, Halo Pesa, AzamPesa)
- [ ] **PYMT-03**: Payment flow shows 2-minute countdown during Azampay STK push — explicit timeout state (not infinite spinner)
- [ ] **PYMT-04**: User receives confirmation screen and notification after successful payment with credits credited
- [ ] **PYMT-05**: User can view full payment history with statuses
- [ ] **PYMT-06**: System processes Azampay callbacks idempotently — duplicate callbacks never double-credit wallet
- [ ] **PYMT-07**: System marks unresolved STK push payments as EXPIRED after timeout and surfaces this to user
- [ ] **PYMT-08**: System issues refunds for payments linked to failed campaigns

### Contact Management

- [ ] **CONT-01**: User can add individual contacts manually (name + phone number)
- [ ] **CONT-02**: User can edit existing contacts
- [ ] **CONT-03**: User can delete contacts
- [ ] **CONT-04**: User can organize contacts into named groups
- [ ] **CONT-05**: User can import contacts from CSV file
- [ ] **CONT-06**: System automatically deduplicates phone numbers on import and within groups
- [ ] **CONT-07**: System normalizes Tanzanian phone numbers to E.164 format (07xx / 06xx → +255XXXXXXXXX)
- [ ] **CONT-08**: User can add phone numbers to a suppression list — suppressed numbers are excluded from all future campaigns
- [ ] **CONT-09**: System shows import summary screen (X contacts imported, Y duplicates skipped, Z invalid)

### Messaging & Campaigns

- [ ] **MESG-01**: User can create a bulk SMS campaign targeting one or more contact groups
- [ ] **MESG-02**: Campaign composer shows real-time character counter with SMS part count and UCS-2 encoding warning for non-GSM characters
- [ ] **MESG-03**: System reserves SMS credits before campaign is queued — campaign cannot transition to QUEUED if credits are insufficient
- [ ] **MESG-04**: User can schedule a campaign for a specific future date and time
- [ ] **MESG-05**: User can cancel a scheduled campaign before it dispatches
- [ ] **MESG-06**: User can view campaign history with aggregate status (sent / delivered / failed counts)
- [ ] **MESG-07**: User can view per-message delivery status within a campaign
- [ ] **MESG-08**: User sees a post-send confirmation screen showing credits deducted and messages queued
- [ ] **MESG-09**: System automatically excludes suppressed numbers from campaign recipients
- [ ] **MESG-10**: System retries failed messages via dead letter queue; credits are refunded for permanently undeliverable messages

### Sender ID Management

- [ ] **SNDR-01**: User is assigned a default numeric sender ID (shortcode) at registration
- [ ] **SNDR-02**: User can request a custom alphanumeric sender ID (subject to admin and TCRA approval)
- [ ] **SNDR-03**: Admin can approve or reject sender ID requests from the admin panel
- [ ] **SNDR-04**: User receives notification when their sender ID request is approved or rejected

### Notifications

- [ ] **NOTF-01**: User receives in-app notification on successful NIDA verification
- [ ] **NOTF-02**: User receives in-app and push notification on successful payment and credit top-up
- [ ] **NOTF-03**: User receives low-credit alert notification when balance drops below threshold
- [ ] **NOTF-04**: User receives 7-day credit expiry warning notification
- [ ] **NOTF-05**: User receives campaign completion summary notification (delivered / failed counts)
- [ ] **NOTF-06**: User receives sender ID approval or rejection notification

### Admin Panel

- [ ] **ADMN-01**: Admin can log in with separate admin credentials (distinct JWT, ROLE_ADMIN)
- [ ] **ADMN-02**: Admin can search and view user accounts
- [ ] **ADMN-03**: Admin can inspect any user's credit ledger history
- [ ] **ADMN-04**: Admin can view and process the sender ID approval queue (approve / reject with reason)
- [ ] **ADMN-05**: Admin can execute manual refunds for users
- [ ] **ADMN-06**: Admin can view a full audit log of platform actions
- [ ] **ADMN-07**: Admin can view and update SMS bundle catalog (pricing, SMS count)

### Analytics

- [ ] **ANLX-01**: User can view campaign delivery statistics (sent, delivered, failed rates per campaign)
- [ ] **ANLX-02**: User can view credit usage history over time with spend trend
- [ ] **ANLX-03**: User can view operator-level delivery rates (M-Pesa vs Tigo vs Airtel etc.)

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
| IDEN-01 | Phase 2 | Pending |
| IDEN-02 | Phase 2 | Pending |
| IDEN-03 | Phase 2 | Pending |
| IDEN-04 | Phase 2 | Pending |
| IDEN-05 | Phase 2 | Pending |
| IDEN-06 | Phase 2 | Pending |
| IDEN-07 | Phase 2 | Pending |
| IDEN-08 | Phase 2 | Pending |
| WLET-01 | Phase 3 | Pending |
| WLET-02 | Phase 3 | Pending |
| WLET-03 | Phase 3 | Pending |
| WLET-04 | Phase 3 | Pending |
| WLET-05 | Phase 3 | Pending |
| WLET-06 | Phase 3 | Pending |
| WLET-07 | Phase 3 | Pending |
| PYMT-01 | Phase 3 | Pending |
| PYMT-02 | Phase 3 | Pending |
| PYMT-03 | Phase 3 | Pending |
| PYMT-04 | Phase 3 | Pending |
| PYMT-05 | Phase 3 | Pending |
| PYMT-06 | Phase 3 | Pending |
| PYMT-07 | Phase 3 | Pending |
| PYMT-08 | Phase 3 | Pending |
| CONT-01 | Phase 4 | Pending |
| CONT-02 | Phase 4 | Pending |
| CONT-03 | Phase 4 | Pending |
| CONT-04 | Phase 4 | Pending |
| CONT-05 | Phase 4 | Pending |
| CONT-06 | Phase 4 | Pending |
| CONT-07 | Phase 4 | Pending |
| CONT-08 | Phase 4 | Pending |
| CONT-09 | Phase 4 | Pending |
| MESG-01 | Phase 4 | Pending |
| MESG-02 | Phase 4 | Pending |
| MESG-03 | Phase 4 | Pending |
| MESG-04 | Phase 4 | Pending |
| MESG-05 | Phase 4 | Pending |
| MESG-06 | Phase 4 | Pending |
| MESG-07 | Phase 4 | Pending |
| MESG-08 | Phase 4 | Pending |
| MESG-09 | Phase 4 | Pending |
| MESG-10 | Phase 4 | Pending |
| SNDR-01 | Phase 2 | Pending |
| SNDR-02 | Phase 4 | Pending |
| SNDR-03 | Phase 4 | Pending |
| SNDR-04 | Phase 4 | Pending |
| NOTF-01 | Phase 5 | Pending |
| NOTF-02 | Phase 5 | Pending |
| NOTF-03 | Phase 5 | Pending |
| NOTF-04 | Phase 5 | Pending |
| NOTF-05 | Phase 5 | Pending |
| NOTF-06 | Phase 5 | Pending |
| ADMN-01 | Phase 5 | Pending |
| ADMN-02 | Phase 5 | Pending |
| ADMN-03 | Phase 5 | Pending |
| ADMN-04 | Phase 5 | Pending |
| ADMN-05 | Phase 5 | Pending |
| ADMN-06 | Phase 5 | Pending |
| ADMN-07 | Phase 5 | Pending |
| ANLX-01 | Phase 5 | Pending |
| ANLX-02 | Phase 5 | Pending |
| ANLX-03 | Phase 5 | Pending |
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
