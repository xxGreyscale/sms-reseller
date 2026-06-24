# Phase 7: Clean Architecture & Local Dev Tooling - Context

**Gathered:** 2026-06-24
**Status:** Ready for planning
**Source:** Direct scope lock with user (decisions 1b / 2a / 3a)

<domain>
## Phase Boundary

A post-v1.0 developer-experience phase delivering three things:

1. **Clean-architecture reference refactor** of `payment-service` ONLY — reorganize its source into explicit clean-architecture layers as the canonical, documented pattern for later rollout to the other 7 services. Behavior-neutral.
2. **One-command local startup** — Docker Compose for infra (Postgres 16 / Redis 7 / RabbitMQ 3) + a start script that boots infra then launches all 8 Spring Boot services + admin-web.
3. **`.env`-based local secrets** — gitignored `.env` sourced by the start script, derived from a committed `.env.example`, supplying the already-wired `AZAMPAY_*` vars plus infra connection vars.

**Out of scope (deferred):** refactoring the other 7 services; building/running production images in Compose; k8s/Terraform infra; Flutter (`customer-app`) in the start script.
</domain>

<decisions>
## Implementation Decisions (LOCKED)

### Clean architecture (decision 1b — reference service first)
- Refactor **`payment-service` only**. It becomes the canonical reference; the other 7 services are explicitly deferred to later phases.
- Target layering: `domain` (entities, value objects, domain exceptions — depends on nothing) → `application` (use-case/service orchestration + output ports/interfaces — depends only on domain) → `infrastructure` (JPA repositories, Azampay gateway/RestClient, RabbitMQ outbox relay, Resilience4j, scheduled jobs — implements ports, depends inward) → `presentation` (REST controllers + DTOs — depends inward).
- **Dependency rule enforced inward.** Domain must not import Spring/Jakarta/JPA. Prefer a build-enforced check (e.g. ArchUnit test or Gradle module/package rule) so the boundary cannot silently rot.
- **Behavior-neutral, MANDATORY:** no change to REST contracts, DB schema/Flyway migrations, emitted events/outbox payloads, or test outcomes. The existing payment-service unit + Testcontainers suite must remain green throughout — it is the regression oracle.
- Map the current package-by-feature layout (`payment/`, `bundle/`, `callback/`, `gateway/`, `outbox/`, `reconciliation/`, `timeout/`, `config/`) onto the new layers; the gateway/callback/outbox/reconciliation/timeout code is infrastructure, controllers are presentation, entities are domain, the `*Service` orchestration is application.
- Produce a **`CLEAN-ARCHITECTURE.md`** documenting layer boundaries, the dependency rule, the package mapping, and a step-by-step rollout playbook for applying the same pattern to the remaining 7 services.

### Start script (decision 2a — Compose infra + Gradle bootRun)
- Create a committed `docker-compose.yml` (or `compose.yaml`) at repo root running **Postgres 16, Redis 7, RabbitMQ 3** with healthchecks and named volumes. NOTE: this file does not exist yet — Phase 1 was supposed to deliver it but no compose/k8s/Terraform exists in the repo, so this phase must create it.
- Create a committed start script (bash; macOS/zsh dev host) that: (a) sources `.env`, (b) `docker compose up -d` the infra, (c) waits for infra healthy, (d) launches all 8 Spring Boot services via `./gradlew :services:<svc>:bootRun` (background, with per-service logs), (e) launches `admin-web` (`apps:admin-web`). Provide a matching stop/teardown path (`compose down` + kill bootRun processes).
- **Services in scope (8):** identity, catalog, wallet, payment, contact, messaging, notification, admin. Plus **admin-web**. Flutter `customer-app` is NOT started by the script.
- Services run under the `dev`/`stub` Spring profile (stub gateways) so no real external credentials are required for a local boot.

### Env mechanism (decision 3a — .env + .env.example)
- Commit a `.env.example` template; the real `.env` stays gitignored (`.env` is already in `.gitignore`).
- Variables include the already-wired Azampay placeholders: `AZAMPAY_BASE_URL`, `AZAMPAY_APP_NAME`, `AZAMPAY_CLIENT_ID`, `AZAMPAY_CLIENT_SECRET` (resolved in `services/payment-service/src/main/resources/application.yml` as `${AZAMPAY_*:placeholder}`), plus local infra connection vars (Postgres/Redis/RabbitMQ host/port/user/pass) the Compose stack and services share.
- The start script sources `.env`; no real secret is committed. `.env.example` carries safe sandbox/placeholder defaults only.

### Claude's Discretion
- Exact package names within each layer, ArchUnit vs Gradle enforcement choice, precise healthcheck/wait mechanism, log-file layout, and script ergonomics (flags, colors) — implementer's call, consistent with existing repo conventions.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Service under refactor
- `services/payment-service/src/main/java/com/smsreseller/payment/` — current package-by-feature layout to be re-layered
- `services/payment-service/src/main/resources/application.yml` — Azampay env wiring + profiles (`dev`/`staging` → `stub`)
- `services/payment-service/src/test/` — the regression oracle; must stay green

### Build / multi-module
- `settings.gradle.kts` — the 8 services + libs + admin-web module coordinates (used by the start script's `bootRun` targets)
- `build.gradle.kts`, `buildSrc/` — build conventions; ArchUnit dep would be added here

### Project rules
- `CLAUDE.md` — Spring Boot 3.5.x / Java 21 stack lock, `RestClient` not `RestTemplate`, jakarta.* imports, service-ownership rules, Tanzania/Azampay integration guidance
</canonical_refs>

<specifics>
## Specific Ideas
- Consider ArchUnit (`com.tngtech.archunit:archunit-junit5`) for the dependency-rule test — it runs inside the existing JUnit 5 + Testcontainers harness with no runtime cost.
- The start script should fail fast with a clear message if `.env` is missing (point the user to copy `.env.example`).
- Keep infra service names/ports in Compose aligned with the `.env` defaults so a fresh clone boots with zero edits.
</specifics>

<deferred>
## Deferred Ideas
- Clean-architecture rollout to identity / catalog / wallet / contact / messaging / notification / admin services (future phases, guided by CLEAN-ARCHITECTURE.md).
- Production-image Compose / k8s manifests / Terraform (the real Phase 1 infra gap — track separately).
- Including the Flutter customer-app in the start script.
</deferred>

---

*Phase: 07-clean-architecture-local-dev-tooling*
*Context gathered: 2026-06-24 via direct scope lock (1b / 2a / 3a)*
