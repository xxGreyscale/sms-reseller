---
phase: 5
slug: notifications-admin-analytics
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-21
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + RabbitMQ via `@ServiceConnection`), Spring Boot Test |
| **Frontend framework** | Vitest (unit — Server Actions, utils, sync components) + Playwright (E2E — admin auth + key flows) for admin-web (Next.js 14) |
| **Backend bases** | `services/notification-service/.../AbstractNotificationIntegrationTest.java` + admin/analytics ITs in owning services (Wave 0) |
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

> Populated by the planner / Wave 0 (one row per task). Every requirement ID below MUST map to
> at least one automated row. Frontend (admin-web) requirements use Vitest (component/Server Action)
> or Playwright (E2E auth/flow) rather than gradle.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _TBD by planner_ | | | NOTF-01..06, ADMN-01..07, ANLX-01..03 | | | | | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `notification-service` Gradle module + `AbstractNotificationIntegrationTest` base (PG16 + RabbitMQ Testcontainers)
- [ ] admin-web Next.js 14 app scaffold (App Router, Tailwind 3, shadcn 3.5 init) + Vitest + Playwright config
- [ ] Placeholder failing test per requirement ID (backend ITs + frontend Vitest/Playwright stubs) so the validation map is non-empty
- [ ] Shared AMQP test fixture covering all four upstream exchanges (identity/wallet/payment/messaging.events)

*Detailed Wave 0 task list produced by the planner.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real push (FCM) delivery to a device | NOTF-02 (push) | No client app to register device tokens until Phase 6; StubPushChannel covers the channel contract in CI | Phase 6: wire FCM, register a device token from the Flutter app, confirm push arrives on payment-confirmed |

*All other phase behaviors (in-app notification log, admin screens, analytics APIs) have automated verification via Testcontainers + Vitest/Playwright.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (incl. admin-web scaffold + frontend test harness)
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
