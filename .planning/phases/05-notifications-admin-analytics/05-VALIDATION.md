---
phase: 5
slug: notifications-admin-analytics
status: in-progress
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-21
updated: 2026-06-21
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + RabbitMQ via `@ServiceConnection`), Spring Boot Test |
| **Frontend framework** | Vitest (unit — Server Actions, utils, sync components) + Playwright (E2E — admin auth + key flows) for admin-web (Next.js 14) |
| **Backend bases** | `services/notification-service/.../AbstractNotificationIntegrationTest.java` + `services/admin-service/.../AbstractAdminIntegrationTest.java` + existing bases in identity/wallet/messaging/payment |
| **Quick run command** | `./gradlew :services:notification-service:test` (+ owning-service admin/analytics unit tests) |
| **Full suite command** | `./gradlew test` (all backend services) ; `cd apps/admin-web && npm run test -- --run` (Vitest) |
| **Estimated runtime** | ~120–240 seconds backend + ~30–60s frontend unit |

---

## Sampling Rate

- **After every task commit:** Run the touched module's quick test (backend gradle test or `npm run test -- --run` for admin-web)
- **After every plan wave:** Run the full backend suite + admin-web Vitest (`npm run test -- --run`)
- **Before `/gsd:verify-work`:** Full backend suite green + admin-web Vitest (`npm run test -- --run`) green + Playwright auth E2E green
- **Max feedback latency:** 240 seconds

---

## Per-Task Verification Map

> Populated by Wave 0 (plan 05-01). One row per requirement ID.
> ADMN-04 and ADMN-05 reference existing tests from prior phases.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-T2 | 05-01 | W0 | NOTF-01 | T-05-SC | UserVerified event → NIDA_VERIFIED notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*UserVerifiedConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-02 | T-05-SC | PaymentConfirmed event → PAYMENT_CONFIRMED notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*PaymentConfirmedConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-03 | T-05-SC | LowCreditAlert event → LOW_CREDIT_ALERT notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*LowCreditAlertConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-04 | T-05-SC | ExpiryWarning event → EXPIRY_WARNING notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*ExpiryWarningConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-05 | T-05-SC | DeliveryReceiptService emits CampaignCompleted outbox event (upstream gap fix) | Integration | `./gradlew :services:messaging-service:test --tests "*CampaignCompletedIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-05 | T-05-SC | CampaignCompleted event → CAMPAIGN_COMPLETED notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*CampaignCompletedConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-06 | T-05-SC | SenderIdDecided event → SENDER_ID_DECIDED notification (idempotent) | Integration | `./gradlew :services:notification-service:test --tests "*SenderIdDecidedConsumerIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | NOTF-01..06 | T-05-SC | Notification feed API returns user's notifications JWT-scoped (paginated) | Integration | `./gradlew :services:notification-service:test --tests "*NotificationFeedIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-01 | — | Admin login returns JWT with ROLE_ADMIN | Integration | `./gradlew :services:identity-service:test --tests "*AdminLoginIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-01 | — | No admin_token cookie → middleware redirects to /login | Vitest unit | `cd apps/admin-web && npm run test -- --run` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-02 | — | Admin user search returns paginated matching users | Integration | `./gradlew :services:identity-service:test --tests "*AdminUserSearchIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-03 | — | Admin ledger inspection returns user's credit transactions | Integration | `./gradlew :services:wallet-service:test --tests "*AdminLedgerIT*"` | ✅ | ❌ RED |
| — | 04-08 | — | ADMN-04 | — | Sender-ID approve/reject (existing SenderIdIT) | Integration | `./gradlew :services:messaging-service:test --tests "*SenderIdIT*"` | ✅ | ✅ GREEN |
| — | 03-06 | — | ADMN-05 | — | Manual refund (existing RefundIT) | Integration | `./gradlew :services:wallet-service:test --tests "*RefundIT*"` | ✅ | ✅ GREEN |
| 05-01-T2 | 05-01 | W0 | ADMN-06 | — | Admin mutations create audit entries | Integration | `./gradlew :services:identity-service:test --tests "*AuditLogIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-06 | — | Domain events consumed by admin-service create audit entries (idempotent) | Integration | `./gradlew :services:admin-service:test --tests "*AuditLogIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ADMN-07 | — | Bundle catalog CRUD (admin-only, @Positive validation on priceTzs/smsCount) | Integration | `./gradlew :services:payment-service:test --tests "*AdminBundleCatalogIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ANLX-01 | — | Campaign delivery stats returned for owner (userId-scoped, no IDOR) | Integration | `./gradlew :services:messaging-service:test --tests "*CampaignAnalyticsIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ANLX-02 | — | Credit usage trend returns daily aggregates (last 90 days, userId-scoped) | Integration | `./gradlew :services:wallet-service:test --tests "*CreditUsageAnalyticsIT*"` | ✅ | ❌ RED |
| 05-01-T2 | 05-01 | W0 | ANLX-03 | — | Operator delivery rates grouped by provider (userId-scoped) | Integration | `./gradlew :services:messaging-service:test --tests "*OperatorRateAnalyticsIT*"` | ✅ | ❌ RED |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `notification-service` Gradle module + `AbstractNotificationIntegrationTest` base (PG16 + RabbitMQ Testcontainers)
- [x] `admin-service` Gradle module + `AbstractAdminIntegrationTest` base (PG16 + RabbitMQ Testcontainers)
- [x] admin-web Next.js 14 app scaffold (App Router, Tailwind 3, shadcn 3.5 init) + Vitest + Playwright config
- [x] Placeholder failing test per requirement ID (backend ITs + frontend Vitest/Playwright stubs) so the validation map is non-empty
- [x] 15 components in src/components/ui/ (button, input, table, badge, dialog, textarea, form, label, separator, sonner, card, skeleton, scroll-area, select, pagination)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real push (FCM) delivery to a device | NOTF-02 (push) | No client app to register device tokens until Phase 6; StubPushChannel covers the channel contract in CI | Phase 6: wire FCM, register a device token from the Flutter app, confirm push arrives on payment-confirmed |

*All other phase behaviors (in-app notification log, admin screens, analytics APIs) have automated verification via Testcontainers + Vitest/Playwright.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (incl. admin-web scaffold + frontend test harness)
- [x] No watch-mode flags
- [x] Feedback latency < 240s
- [x] `nyquist_compliant: true` set in frontmatter
- [x] `wave_0_complete: true` set in frontmatter

**Approval:** Wave 0 complete — 05-01 execution
