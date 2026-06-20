---
phase: 4
slug: contacts-messaging
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-21
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + Redis 7 + RabbitMQ via `@ServiceConnection`), Spring Boot Test |
| **Config file** | `services/contact-service/.../AbstractContactIntegrationTest.java` + `services/messaging-service/.../AbstractMessagingIntegrationTest.java` (Wave 0 installs, mirroring Phase 3 bases) |
| **Quick run command** | `./gradlew :services:contact-service:test :services:messaging-service:test --tests "*UnitTest"` |
| **Full suite command** | `./gradlew :services:contact-service:test :services:messaging-service:test :services:wallet-service:test` |
| **Estimated runtime** | ~90–180 seconds (Testcontainers + RabbitMQ DLX TTL ladders) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (unit tests for touched module)
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

> Populated by the planner / Wave 0 (one row per task). Every requirement ID below MUST map to
> at least one automated row. Note DLX retry-timing tests should use a shortened TTL ladder via
> test config to keep feedback latency bounded.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _TBD by planner_ | | | CONT-01..09, MESG-01..10, SNDR-02/03/04 | | | | | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `contact-service` + `messaging-service` Gradle modules + `AbstractIntegrationTest` bases (PG16 + Redis + RabbitMQ Testcontainers), mirroring Phase 3
- [ ] Placeholder failing IT per requirement ID (CONT-01..09, MESG-01..10, SNDR-02/03/04) so the validation map is non-empty
- [ ] Shared AMQP test fixture for the send pipeline (quorum + DLX TTL ladder, shortened TTLs in test) and the consume/release/refund events consumed by wallet-service

*Detailed Wave 0 task list produced by the planner.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real upstream SMS send + real delivery receipt | MESG-07, MESG-10 | Requires a contracted upstream SMS provider (Phase 0 dependency); stub simulates DLRs + hard/transient fails in CI | When provider is signed: switch to `@Profile("prod")`, send a small campaign, confirm real delivery receipts update per-message status and permanent failures refund credits |

*All other phase behaviors have automated verification via the stub provider + Testcontainers.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
