---
phase: 2
slug: identity-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-19
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Populated from 02-RESEARCH.md "Validation Architecture". The planner refines
> the Per-Task Verification Map once plan/task IDs exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers (Postgres 16, Redis 7) |
| **Config file** | `services/identity-service/build.gradle.kts` (test deps); Wave 0 adds Testcontainers wiring |
| **Quick run command** | `./gradlew :services:identity-service:test --no-daemon` |
| **Full suite command** | `./gradlew build --no-daemon` |
| **Estimated runtime** | ~60–120 seconds (Testcontainers spin-up dominates) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:identity-service:test --no-daemon`
- **After every plan wave:** Run `./gradlew build --no-daemon`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~120 seconds

---

## Per-Task Verification Map

> Placeholder — concrete Task IDs are assigned by the planner. The rows below are
> requirement-level validation targets the planner must map onto real tasks.

| Req | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-----|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| IDEN-01 | TBD | Register with phone + email | T-2-01 | Duplicate email/phone rejected; password BCrypt-hashed, never returned | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-02 | TBD | NIDA verify returns PENDING immediately (async) | T-2-02 | Registration response never blocks on NIDA; status = PENDING_VERIFICATION | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-03 | TBD | 50 free credits on verification | T-2-03 | UserVerified outbox row written in same TX as status flip; idempotent | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-04 | TBD | Login with email + password | T-2-04 | Valid creds → access+refresh tokens; invalid → 401, no user enumeration | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-05 | TBD | Session persists (JWT + refresh) | T-2-05 | Refresh rotates on use; reused token detected → session revoked | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-06 | TBD | Logout revokes session | T-2-06 | Logout deletes current device refresh key; other devices unaffected | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-07 | TBD | Password reset via email link | T-2-07 | Single-use, time-limited token; reset revokes ALL sessions | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| IDEN-08 | TBD | Graceful degrade when NIDA down | T-2-08 | Circuit-breaker open → stay PENDING; background retry → eventual VERIFIED | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| SNDR-01 | TBD | Default numeric sender ID at registration/verify | T-2-09 | Unique numeric shortcode assigned exactly once on VERIFIED | integration | `./gradlew :services:identity-service:test` | ❌ W0 | ⬜ pending |
| (xref) | TBD | shared-security validates issued JWT | T-2-10 | Public-key validation succeeds; tampered/forged token rejected | unit | `./gradlew :libs:shared-security:test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Testcontainers wiring for Postgres 16 + Redis 7 in identity-service test source set (`@ServiceConnection` pattern per CLAUDE.md)
- [ ] Add `spring-boot-testcontainers` + a Redis Testcontainers module to the version catalog
- [ ] Shared test fixtures: RSA keypair for JWT sign/verify, base `@SpringBootTest` config with `@Profile("stub")`
- [ ] A failing/placeholder test per requirement ID above so the sampling map is non-empty before implementation

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Reset email actually renders/sends in prod | IDEN-07 | Real SMTP provider is `@Profile("prod")`; dev uses stub that records the link | Wire real provider in staging, trigger reset, confirm email receipt |
| Real NIDA verification | IDEN-02/08 | Real NIDA API access not yet available (Phase 0 blocker) | Swap to `@Profile("prod")` when access arrives; verify against live API |

*The stub NIDA + stub email paths ARE automated; only the real-provider wiring is manual.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
