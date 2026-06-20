---
phase: 3
slug: wallet-payments
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-20
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + Redis 7 + RabbitMQ via `@ServiceConnection`), Spring Boot Test |
| **Config file** | `services/wallet-service/src/test/.../AbstractIntegrationTest.java` + `services/payment-service/...` (Wave 0 installs, mirroring Phase 2 `identity-service` base) |
| **Quick run command** | `./gradlew :services:wallet-service:test :services:payment-service:test --tests "*UnitTest"` |
| **Full suite command** | `./gradlew :services:wallet-service:test :services:payment-service:test` |
| **Estimated runtime** | ~60–120 seconds (Testcontainers spin-up dominated) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (unit tests for touched module)
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

> Populated by the planner (one row per task) and updated during Wave 0 execution.
> Every requirement ID below MUST map to at least one automated row.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _TBD by planner_ | | | WLET-01..07, PYMT-01..08 | | | | | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `wallet-service` + `payment-service` Gradle modules + `AbstractIntegrationTest` bases (PG16 + Redis + RabbitMQ Testcontainers), mirroring Phase 2 `02-01`
- [ ] Placeholder failing IT per requirement ID (WLET-01..07, PYMT-01..08) so the validation map is non-empty
- [ ] Shared AMQP test fixture for the inbound `UserVerified` event + outbound payment/credit events

*Detailed Wave 0 task list produced by the planner.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real Azampay STK push on a physical handset | PYMT-02 | Requires live merchant credentials + a real phone (Phase 0 dependency); stub covers success/fail/timeout in CI | When merchant account arrives: switch to `@Profile("prod")`, initiate a Starter purchase, confirm STK prompt + credit on success |

*All other phase behaviors have automated verification via the stub gateway + Testcontainers.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
