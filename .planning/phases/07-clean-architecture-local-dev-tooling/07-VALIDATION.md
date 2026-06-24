---
phase: 7
slug: clean-architecture-local-dev-tooling
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-24
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers (payment-service); ArchUnit 1.4.x (new dependency-rule test); shell smoke-check for start script/compose |
| **Config file** | `services/payment-service/build.gradle.kts` (existing); version catalog `gradle/libs.versions.toml` for archunit entry |
| **Quick run command** | `./gradlew :services:payment-service:test --tests "*ArchitectureTest"` |
| **Full suite command** | `./gradlew :services:payment-service:test` (the 24-class regression oracle — must stay green) |
| **Estimated runtime** | ~90–180 seconds (Testcontainers PG16 + RabbitMQ + Redis spin-up) |

---

## Sampling Rate

- **After every task commit:** Run the quick command (ArchUnit rule) once it exists; before then, compile the moved package.
- **After every refactor wave:** Run the full payment-service suite — it is the behavior-neutral regression oracle.
- **Before `/gsd:verify-work`:** Full payment-service suite green AND ArchUnit rule green AND start script boots the stack to healthy.
- **Max feedback latency:** ~180 seconds (full Testcontainers suite).

---

## Per-Task Verification Map

> Concrete task IDs are assigned by the planner. This maps each requirement to its validation invariant and proof command.

| Requirement | Validation Invariant | Test Type | Automated Command | File Exists | Status |
|-------------|----------------------|-----------|-------------------|-------------|--------|
| ARCH-01 | payment-service test suite (24 classes) passes unchanged after the package re-layer — behavior-neutral proof | integration | `./gradlew :services:payment-service:test` | ✅ existing | ⬜ pending |
| ARCH-01 | Inward dependency rule holds: domain depends on nothing; application depends only on domain; infra/presentation depend inward — proven by an ArchUnit layered-architecture test | unit (ArchUnit) | `./gradlew :services:payment-service:test --tests "*ArchitectureTest"` | ❌ W0 | ⬜ pending |
| ARCH-01 | `CLEAN-ARCHITECTURE.md` exists documenting layer boundaries, per-class mapping, and the remaining-7-services rollout playbook | doc assertion | `test -f services/payment-service/CLEAN-ARCHITECTURE.md && grep -q "Rollout" services/payment-service/CLEAN-ARCHITECTURE.md` | ❌ W0 | ⬜ pending |
| DEVX-02 | `docker compose up -d` brings Postgres 16 + Redis 7 + RabbitMQ 3 to healthy; per-service logical DBs created | infra smoke | `docker compose up -d && docker compose ps --format '{{.Health}}' \| grep -vq unhealthy` | ❌ W0 | ⬜ pending |
| DEVX-01 | Start script boots Compose infra, waits healthy, launches all 8 services + admin-web; each service `/actuator/health` returns UP; stop script tears everything down | e2e smoke (manual-assisted) | `./scripts/start.sh` then poll `/actuator/health`; `./scripts/stop.sh` | ❌ W0 | ⬜ pending |
| DEVX-03 | `.env.example` committed with AZAMPAY_* + infra vars; `.env` gitignored; start script fails fast with a clear message if `.env` absent; no real secret committed | config assertion | `test -f .env.example && grep -q AZAMPAY_CLIENT_ID .env.example && git check-ignore .env` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Add `archunit-junit5` to `gradle/libs.versions.toml` + payment-service `testImplementation`
- [ ] `PaymentArchitectureTest.java` — ArchUnit layered-architecture rule (initially may be RED until packages are moved)
- [ ] Audit task: confirm all 8 services' `bootRun` prerequisites (committed `server.port`, `spring.datasource`, `spring.rabbitmq`, `spring.data.redis`, and the missing `classpath:keys/jwt-public.pem`) — per research landmines A1–A3; the start script (DEVX-01) cannot boot a service that lacks these.

*The payment-service Testcontainers suite already exists and covers the behavior-neutral invariant for ARCH-01 — no new integration test scaffolding needed for the refactor itself.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Full-stack one-command boot to all-healthy | DEVX-01 | Requires Docker daemon + 8 JVM bootRun processes + Next.js dev server on the dev host — not run in CI at this phase | Run `./scripts/start.sh`; confirm `docker compose ps` all healthy and each service `curl -s localhost:<port>/actuator/health` returns `{"status":"UP"}`; then `./scripts/stop.sh` and confirm all processes/containers stopped |

*The ArchUnit rule, the payment-service suite, and the `.env`/compose file assertions are all automated.*

---

## Validation Sign-Off

- [ ] All tasks have an automated verify OR a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (ArchUnit dep, arch test, bootRun-prereq audit)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
