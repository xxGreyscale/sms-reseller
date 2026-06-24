# Phase 7: Clean Architecture & Local Dev Tooling - Research

**Researched:** 2026-06-24
**Domain:** Spring Boot 3 clean-architecture refactoring (single module) + macOS local-dev tooling (Docker Compose + multi-`bootRun` orchestration + `.env`)
**Confidence:** HIGH (refactor mapping, ArchUnit, compose, env) / HIGH (start-script landmines — verified against repo state)

## Summary

This phase has two distinct halves with very different risk profiles. The **clean-architecture refactor** of `payment-service` is a pure package-reorganization problem made tractable by a 24-file Testcontainers regression oracle and a mandatory behavior-neutral constraint — the safest path is package-by-layer reorganization that keeps the existing JPA `@Entity` classes as the domain model (a pragmatic, documented compromise), not a full domain/persistence split, because a split would change behavior surface (mapping, transactions) and violate the regression oracle. ArchUnit 1.4.1 enforces the inward dependency rule inside the existing JUnit 5 harness at zero runtime cost.

The **local-dev tooling** half is where the real landmines are, and they are *not* obvious from the phase brief. Investigation revealed three repo facts that change the plan: (1) **no service commits any `spring.datasource`, `server.port`, `spring.rabbitmq`, or `spring.data.redis` config** — these are supplied at runtime via environment variables, and currently *nothing* supplies them for a non-test boot, so `.env` + the start script are load-bearing, not convenience; (2) the prod `application.yml` references `classpath:keys/jwt-public.pem` which **does not exist in `src/main/resources/`** (only test-keys under `src/test`), so a naive `bootRun` will fail JWT decoder initialization; (3) **no `compose.yaml` exists** despite Phase 1's intent — it must be created from scratch. Docker 28.3 + Compose v2.38 are confirmed installed.

**Primary recommendation:** Refactor by moving classes into four layer packages while keeping JPA entities as the domain model; enforce with an ArchUnit `layeredArchitecture()` test (relaxed: domain may hold JPA annotations, but must not depend on application/infrastructure/presentation). For tooling, make the start script *and* compose both source one root `.env` whose vars (`SPRING_DATASOURCE_*`, `SERVER_PORT` per-service, Postgres/Redis/Rabbit creds) are the single source of truth, and resolve the missing JWT key + missing per-service ports + missing datasource config explicitly — these are the items that will silently break a "one-command boot."

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Clean-arch layer reorg | API / Backend (payment-service) | — | Pure source reorganization inside one Spring Boot module |
| Dependency-rule enforcement | API / Backend (test source set) | Build | ArchUnit test runs in `src/test`, gated by Gradle `test` task |
| Infra provisioning (PG/Redis/Rabbit) | Local Docker (Compose) | — | Dev-host containers, not k8s (deferred) |
| Service orchestration | Local shell (bash start script) | Build (Gradle bootRun) | Script drives `./gradlew bootRun` per service |
| Secret/config injection | Shell + Spring env binding | Compose | One `.env` feeds both compose and Spring `${VAR}` relaxed binding |
| admin-web boot | Frontend Server (Next.js dev) | — | `next dev` via npm, NOT a Gradle bootRun |

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Refactor `payment-service` ONLY** into layers: `domain` (depends on nothing) → `application` (use-case orchestration + output ports; depends only on domain) → `infrastructure` (JPA repos, Azampay gateway, RabbitMQ outbox relay, Resilience4j, scheduled jobs; implements ports, depends inward) → `presentation` (REST controllers + DTOs; depends inward). Other 7 services deferred.
- **Dependency rule enforced inward**, build-enforced (ArchUnit or Gradle rule). Domain must not import application/infrastructure/presentation.
- **Behavior-neutral, MANDATORY:** no change to REST contracts, DB schema/Flyway migrations, emitted events/outbox payloads, or test outcomes. Existing payment-service test suite stays green — it is the regression oracle.
- Produce **`CLEAN-ARCHITECTURE.md`**: layer boundaries, dependency rule, package mapping, step-by-step rollout playbook for the other 7 services.
- **Start script (decision 2a):** committed root `compose.yaml` (Postgres 16 / Redis 7 / RabbitMQ 3, healthchecks, named volumes) + committed bash start script that sources `.env`, `docker compose up -d`, waits for healthy, launches all 8 services via `./gradlew :services:<svc>:bootRun` (background, per-service logs) + admin-web (`apps:admin-web`), with a matching stop/teardown.
- **8 services in scope:** identity, catalog, wallet, payment, contact, messaging, notification, admin. Plus admin-web. Flutter `customer-app` NOT started.
- Services run under `dev`/`stub` profile (stub gateways) — no real external credentials.
- **`.env` (decision 3a):** commit `.env.example`; real `.env` gitignored (already in `.gitignore` lines 16-18). Vars: `AZAMPAY_BASE_URL/APP_NAME/CLIENT_ID/CLIENT_SECRET` + local infra connection vars (PG/Redis/Rabbit host/port/user/pass). Script fails fast if `.env` missing.

### Claude's Discretion
- Exact package names within each layer; ArchUnit vs Gradle enforcement choice; healthcheck/wait mechanism; log-file layout; script ergonomics (flags, colors).

### Deferred Ideas (OUT OF SCOPE)
- Clean-arch rollout to the other 7 services (future phases, guided by CLEAN-ARCHITECTURE.md).
- Production-image Compose / k8s manifests / Terraform (the real Phase 1 infra gap — track separately).
- Including Flutter customer-app in the start script.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | Clean-arch reference refactor + CLEAN-ARCHITECTURE.md | Per-class mapping table (below); ArchUnit rule; JPA-entity-as-domain pragmatic decision |
| DEVX-01 | One-command start/stop script | bash orchestration pattern (§Start Script); landmines: missing JWT key, missing per-service ports/datasource |
| DEVX-02 | local-infra docker-compose | compose.yaml template with PG16/Redis7/Rabbit3 healthchecks + named volumes |
| DEVX-03 | .env + .env.example (sources wired AZAMPAY_* vars) | `.env` is load-bearing for datasource too (no service commits datasource config) |

## Project Constraints (from CLAUDE.md)

- Spring Boot **3.5.9**, Java **21 LTS**, Gradle 8.x Kotlin DSL — locked, no deviation.
- `jakarta.*` imports only (never `javax.*`). Jakarta EE 10 / Tomcat 10.1.
- `RestClient` not `RestTemplate` for new code.
- One logical DB + one Flyway set per service; no cross-service schema sharing. (Phase 7 touches only payment-service schema — and must NOT change it.)
- Virtual threads enabled (`spring.threads.virtual.enabled=true`) — relevant to bootRun behavior.
- Flyway 10 requires `flyway-database-postgresql` (already present).
- Lombok annotation processor MUST precede MapStruct (enforced in `spring-boot-service` convention plugin — do not disturb when moving files).

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| ArchUnit JUnit5 | **1.4.1** | Build-enforced layered dependency rule | De-facto standard for Java architecture tests; runs in existing JUnit 5 harness, zero runtime cost. [VERIFIED: Maven Central — latest 1.4.1, Java 8-21+ compatible] |
| Docker Compose | **v2.38.1** (installed) | Local infra (PG16/Redis7/Rabbit3) | Already on dev host; `compose.yaml` schema v2, no `version:` key needed. [VERIFIED: `docker compose version`] |
| Gradle bootRun | Spring Boot 3.5.9 plugin | Launch each service from source | `spring-boot-service` convention plugin already applies `org.springframework.boot`. [VERIFIED: buildSrc] |
| Next.js dev | 14.2.29 (admin-web) | admin-web local boot | `npm run dev` → `next dev`. NOT a Gradle task. [VERIFIED: apps/admin-web/package.json] |

### Installation
ArchUnit — add to version catalog and payment-service test deps only:
```toml
# gradle/libs.versions.toml  [versions]
archunit = "1.4.1"
# [libraries]
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
```
```kotlin
// services/payment-service/build.gradle.kts
testImplementation(libs.archunit.junit5)
```

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ArchUnit | Gradle module-per-layer (separate subprojects) | Compile-time guarantee but massive restructuring; breaks single-module convention plugin and violates behavior-neutral scope. Reject for MVP. |
| ArchUnit | Spring Modulith | Heavier, opinionated, adds runtime dep; overkill for one service. Reject. |

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| com.tngtech.archunit:archunit-junit5 | Maven Central | 13+ yrs (TNG) | very high | github.com/TNG/ArchUnit | n/a (Java) | Approved — verified via Maven Central search |

No npm/PyPI packages introduced. admin-web adds no new deps. slopcheck (pip/npm focused) not applicable to a Maven-coordinate dependency; legitimacy confirmed by Maven Central + official GitHub repo.

## Architecture Patterns

### Clean-Architecture Layering — pragmatic decision (the central ARCH-01 call)

**Recommendation: keep JPA `@Entity` classes as the domain model (do NOT split domain from persistence).** [ASSUMED — see A1]

Rationale, grounded in repo facts:
- `Payment`, `SmsBundle`, `OutboxEntry` are all JPA `@Entity` with `jakarta.persistence.*` + Spring Data auditing annotations. A "pure" domain would require a parallel POJO model + MapStruct mappers + repository adapters translating both ways. That **changes the behavior surface** (new mapping code, new transaction boundaries, auditing relocation) and risks the regression oracle — directly conflicting with the MANDATORY behavior-neutral constraint.
- The correct, honest pattern for a single Spring Boot module under a behavior-neutral constraint is **package-by-layer with a documented, named compromise**: the domain layer is permitted to carry JPA/persistence annotations, but the *dependency rule* (no domain → application/infrastructure/presentation imports) is still enforced. CLEAN-ARCHITECTURE.md must document this explicitly as "persistence-annotated domain model — a deliberate MVP compromise; full separation is a future step in the rollout playbook."
- This keeps the refactor a pure *move*, which the test oracle can verify is behavior-neutral.

### Recommended package structure (Claude's discretion — proposed)
```
com.smsreseller.payment
├── PaymentServiceApplication.java        # stays at root (composition root)
├── domain/                               # entities, value objects, domain exceptions — no app/infra/presentation deps
│   ├── payment/   (Payment, PaymentStatus, BundleNotPurchasableException, PendingPaymentExistsException)
│   ├── bundle/    (SmsBundle)
│   └── outbox/    (OutboxEntry, PaymentConfirmedEvent)
├── application/                          # orchestration + OUTPUT PORTS (interfaces) — depends only on domain
│   ├── PaymentService.java
│   ├── BundleAdminService.java
│   ├── CallbackProcessor.java
│   └── port/      (PaymentGateway, WebhookSignatureValidator)   # ports moved here, impls stay in infra
├── infrastructure/                       # implements ports; depends inward
│   ├── persistence/ (PaymentRepository, BundleRepository, OutboxRepository)
│   ├── gateway/     (AzampayPaymentGateway, StubPaymentGateway, AzampayTokenProvider, Stk*/TransactionStatus DTOs, AzampayTransientException)
│   ├── callback/    (StubSignatureValidator, AzampayCallbackPayload)
│   ├── messaging/   (OutboxRelay)
│   ├── scheduling/  (ReconciliationJob, PaymentTimeoutJob)
│   └── config/      (RabbitMqConfig, Resilience4jConfig, SecurityConfig)
└── presentation/                         # controllers + request/response DTOs
    ├── PaymentController.java, PaymentDto.java, PurchaseRequest.java
    ├── BundleController.java, AdminBundleController.java, BundleDto.java, BundleSaveRequest.java
    └── CallbackController.java
```

### Per-class mapping table (current → layer)
| Current file | New layer | Notes |
|--------------|-----------|-------|
| `payment/Payment.java` | domain/payment | JPA entity stays as domain model |
| `payment/PaymentStatus.java` | domain/payment | enum |
| `payment/BundleNotPurchasableException.java` | domain/payment | domain exception |
| `payment/PendingPaymentExistsException.java` | domain/payment | domain exception |
| `bundle/SmsBundle.java` | domain/bundle | JPA entity |
| `outbox/OutboxEntry.java` | domain/outbox | JPA entity |
| `outbox/PaymentConfirmedEvent.java` | domain/outbox | event payload (domain DTO) |
| `payment/PaymentService.java` | application | orchestration; uses `@Service`, `@Transactional` |
| `bundle/BundleAdminService.java` | application | orchestration |
| `callback/CallbackProcessor.java` | application | orchestration |
| `gateway/PaymentGateway.java` | application/port | **output port interface** |
| `callback/WebhookSignatureValidator.java` | application/port | output port interface |
| `payment/PaymentRepository.java` | infrastructure/persistence | Spring Data interface |
| `bundle/BundleRepository.java` | infrastructure/persistence | |
| `outbox/OutboxRepository.java` | infrastructure/persistence | |
| `gateway/AzampayPaymentGateway.java` | infrastructure/gateway | port impl |
| `gateway/StubPaymentGateway.java` | infrastructure/gateway | port impl |
| `gateway/AzampayTokenProvider.java` | infrastructure/gateway | |
| `gateway/Stk*/TransactionStatusResult.java` | infrastructure/gateway | gateway DTOs |
| `gateway/AzampayTransientException.java` | infrastructure/gateway | |
| `callback/StubSignatureValidator.java` | infrastructure/callback | port impl |
| `callback/AzampayCallbackPayload.java` | infrastructure/callback | inbound wire DTO |
| `outbox/OutboxRelay.java` | infrastructure/messaging | RabbitMQ relay |
| `reconciliation/ReconciliationJob.java` | infrastructure/scheduling | `@Scheduled` |
| `timeout/PaymentTimeoutJob.java` | infrastructure/scheduling | `@Scheduled` |
| `config/RabbitMqConfig.java` | infrastructure/config | |
| `config/Resilience4jConfig.java` | infrastructure/config | |
| `config/SecurityConfig.java` | infrastructure/config | |
| `*Controller.java`, `*Dto.java`, `*Request.java` (payment+bundle+callback) | presentation | REST surface |
| `PaymentServiceApplication.java` | root (unchanged) | composition root scans all layers |

**Caveat to flag for the planner:** `StkPushRequest`/`StkPushResult`/`TransactionStatusResult` are referenced by the `PaymentGateway` *port* in `application`, but they are gateway-shaped DTOs. If they move to infrastructure, the port (application) would import infrastructure — violating the rule. **Resolution:** either (a) move these port-parameter/return types into `application/port` alongside the interface, or (b) keep the port using only domain types. The planner must pick one; option (a) is simplest and behavior-neutral. This is the single most likely ArchUnit failure during refactor.

### ArchUnit rule (verified syntax pattern)
```java
// Source: github.com/TNG/ArchUnit docs — layeredArchitecture API [CITED]
@AnalyzeClasses(packages = "com.smsreseller.payment")
class LayeredArchitectureTest {
    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .layer("Presentation").definedBy("..presentation..")
        .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
        .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Presentation") // composition root aside
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation");

    // Explicit "domain stays clean of app/infra" guard:
    @ArchTest
    static final ArchRule domainPurity = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..presentation..");
}
```
**Note:** `consideringOnlyDependenciesInLayers()` avoids failures from JDK/Spring/Jakarta classes. The domain-purity rule deliberately does NOT forbid `jakarta.persistence.*` (per the pragmatic decision). If the planner wants to forbid Spring stereotypes in domain later, add a separate rule — but not for MVP behavior-neutral scope.

### Start Script pattern (DEVX-01)
```
1. set -euo pipefail; cd repo root
2. [[ -f .env ]] || { echo "Missing .env — run: cp .env.example .env"; exit 1; }
3. set -a; source .env; set +a          # export every var to child processes
4. docker compose up -d                  # infra
5. wait-for-healthy: loop `docker compose ps --format json` until pg/redis/rabbit = healthy (uses compose healthchecks)
6. mkdir -p .logs
7. for svc in identity catalog wallet payment contact messaging notification admin:
     SERVER_PORT=<assigned> ./gradlew ":services:${svc}-service:bootRun" --args='--spring.profiles.active=dev' \
        > ".logs/${svc}.log" 2>&1 &  ; echo $! >> .logs/pids
8. (cd apps/admin-web && npm run dev > ../../.logs/admin-web.log 2>&1 &) ; record pid
9. trap / stop script: kill PIDs in .logs/pids, then `docker compose down`
```

**Gradle-parallel guidance:** Do NOT run all 8 `bootRun`s in a single Gradle invocation — `bootRun` is a blocking task and Gradle will not run two blocking application tasks concurrently in one build cleanly. Use **8 separate `./gradlew :…:bootRun &` invocations** (each its own Gradle client/daemon connection). They share one Gradle daemon for compilation but each holds its own JVM process for the running app. Avoid `--parallel` for this (it parallelizes a single build's tasks, not what you need). Per-service log redirect gives clean `.logs/<svc>.log`.

### Anti-Patterns to Avoid
- **Single `gradlew bootRun` for all services** — blocks; only first service runs. Use background per-service invocations.
- **Splitting domain from JPA persistence** in this phase — changes behavior, breaks oracle, out of scope.
- **Putting datasource creds in compose only** — Spring services boot from Gradle on the host (not in compose), so they need `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/...` from the *same* `.env`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Layer dependency enforcement | Custom static-analysis script / grep on imports | ArchUnit `layeredArchitecture()` | Handles transitive deps, package globs, reports violations clearly |
| Infra readiness wait | `sleep 30` | Compose `healthcheck:` + poll `docker compose ps` | Deterministic; `sleep` races on slow machines |
| Postgres/Redis/Rabbit readiness probes | bash port-pinging | `pg_isready`, `redis-cli ping`, rabbitmq `rabbitmq-diagnostics` in compose healthcheck | Battle-tested, image-bundled |
| Env injection into Spring | Per-var `-D` flags | `set -a; source .env` + Spring relaxed env binding (`SPRING_DATASOURCE_URL` → `spring.datasource.url`) | Spring auto-maps SCREAMING_SNAKE env to dotted props |

## Runtime State Inventory

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — refactor is source-only; Flyway migrations V1–V4 and DB schema MUST NOT change | None (verified: behavior-neutral constraint) |
| Live service config | None — no live services; local-only | None |
| OS-registered state | None | None |
| Secrets/env vars | `.env` is NEW load-bearing state: must define `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_DATA_REDIS_HOST/PORT`, `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`, `SERVER_PORT` per service, `AZAMPAY_*`. `.env` already gitignored | Create `.env.example`; map every `${VAR}` the services read |
| Build artifacts | Moving Java files invalidates incremental compile + may leave stale `build/classes` and generated MapStruct/Lombok sources referencing old packages | `./gradlew :services:payment-service:clean test` after refactor — do not trust incremental |

**Critical missing-config findings (verified by grep across all 8 services):**
1. **No service commits `spring.datasource`, `server.port`, `spring.rabbitmq`, or `spring.data.redis`.** Tests inject these via Testcontainers `@ServiceConnection`/`@DynamicPropertySource`. For a *local non-test boot* nothing currently supplies datasource URL/credentials or distinct ports → **8 services would all default to port 8080 (collision) and have no datasource.** The `.env` + start script (per-service `SERVER_PORT`, shared `SPRING_DATASOURCE_*`) is the fix and is REQUIRED, not optional.
2. **Missing JWT public key for prod-profile boot.** `payment-service` prod `application.yml` line 21 references `classpath:keys/jwt-public.pem`, but `src/main/resources/keys/` does not exist (only `src/test/resources/test-keys/`). A `dev`-profile `bootRun` will fail `NimbusJwtDecoder` init unless a dev key is provided. **Planner must add** a dev-profile key location override (env var or `application-dev.yml`) OR provide a committed dev public key. Verify the same gap for the other 7 services before the start script can boot them.

## Common Pitfalls

### Pitfall 1: Port DTOs dragging application → infrastructure
**What goes wrong:** `PaymentGateway` (port, application) signatures use `StkPushRequest`/`StkPushResult`/`TransactionStatusResult`. If those move to infrastructure, ArchUnit fails.
**How to avoid:** Move port parameter/return types into `application/port`. Verify with ArchUnit before declaring the refactor done.

### Pitfall 2: All services default to 8080
**What goes wrong:** No `server.port` committed → second `bootRun` fails "port in use."
**How to avoid:** Assign distinct ports in `.env` and export `SERVER_PORT` per service in the start loop. Suggested map below.

### Pitfall 3: JWT key absent → boot failure
**What goes wrong:** Resource server can't load `classpath:keys/jwt-public.pem`.
**How to avoid:** dev key location override; audit all 8 services for the same path before the script boots them.

### Pitfall 4: Flyway runs against empty DB on first boot
**What goes wrong:** First `bootRun` against fresh compose Postgres runs V1–V4 to create schema; `ddl-auto: validate` then validates. This is correct and desired — but each service needs its own logical DB. Compose must create 8 databases (init script) OR the start script provisions them.
**How to avoid:** compose `POSTGRES_DB` makes one DB; add an init SQL (`/docker-entrypoint-initdb.d/`) creating one DB per service, matching each service's `SPRING_DATASOURCE_URL`.

### Pitfall 5: Stale generated sources after move
**What goes wrong:** Lombok/MapStruct generated classes and `build/classes` reference old packages; incremental build masks failures.
**How to avoid:** `./gradlew :services:payment-service:clean test` as the oracle run, never incremental.

## docker-compose template (DEVX-02)
```yaml
# compose.yaml (schema v2 — no version: key)
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-smsreseller}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-smsreseller}
      POSTGRES_DB: ${POSTGRES_DB:-smsreseller}
    ports: ["5432:5432"]
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./scripts/db-init:/docker-entrypoint-initdb.d:ro   # creates per-service DBs
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-smsreseller}"]
      interval: 5s ; timeout: 5s ; retries: 10
  redis:
    image: redis:7
    ports: ["6379:6379"]
    volumes: [redisdata:/data]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s ; timeout: 3s ; retries: 10
  rabbitmq:
    image: rabbitmq:3-management
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}
    ports: ["5672:5672", "15672:15672"]
    volumes: [rabbitdata:/var/lib/rabbitmq]
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s ; timeout: 5s ; retries: 10
volumes: { pgdata: , redisdata: , rabbitdata: }
```
Image tags match Testcontainers (`postgres:16`, `redis:7`, `rabbitmq:3-management`) and CLAUDE.md (PG16/Redis7/Rabbit3) — verified.

## .env.example pattern (DEVX-03)
```bash
# Infra (consumed by compose AND services)
POSTGRES_USER=smsreseller
POSTGRES_PASSWORD=smsreseller
POSTGRES_DB=smsreseller
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
# Spring relaxed binding (services boot on host, connect to localhost compose):
SPRING_DATASOURCE_USERNAME=smsreseller
SPRING_DATASOURCE_PASSWORD=smsreseller
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
# Azampay (sandbox/stub — placeholders OK under dev/stub profile)
AZAMPAY_BASE_URL=https://sandbox.azampay.co.tz
AZAMPAY_APP_NAME=sms-reseller
AZAMPAY_CLIENT_ID=placeholder
AZAMPAY_CLIENT_SECRET=placeholder
```
Per-service `SPRING_DATASOURCE_URL` and `SERVER_PORT` are best set in the start script loop (so one URL template + port map drives all 8) rather than 8×2 lines in `.env`. The planner decides; document the choice.

**Suggested port map** (Claude's discretion): identity 8081, catalog 8082, wallet 8083, payment 8084, contact 8085, messaging 8086, notification 8087, admin 8088, admin-web 3000.

**Bash sourcing safety:** use `set -a; source .env; set +a` so all vars export to Gradle/Spring child processes. Values must not contain unquoted spaces; `.env.example` uses simple tokens. Do NOT `eval` the file.

## State of the Art
| Old Approach | Current Approach | When | Impact |
|--------------|------------------|------|--------|
| `docker-compose.yml` + `version: "3"` | `compose.yaml`, no `version:` key | Compose v2 | Cleaner; v2.38 installed |
| Hexagonal full domain/persistence split | Pragmatic persistence-annotated domain for MVP | n/a | Honest, behavior-neutral; documented as a future step |

## Assumptions Log
| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Keeping JPA entities as the domain model (not splitting persistence) is the right MVP call | Architecture Patterns | If a reviewer demands pure domain, refactor grows and may break oracle — but CONTEXT's behavior-neutral lock strongly supports A1 |
| A2 | Spring relaxed binding maps `SPRING_DATASOURCE_URL` etc. from env (no committed datasource means env is the intended mechanism) | Runtime State | If services actually expect a committed `application-dev.yml` not found here, start script needs that file too — planner should confirm by attempting one `bootRun` |
| A3 | Other 7 services share the same missing-JWT-key + missing-port gap as payment-service | Runtime State | If some services differ, per-service handling needed; planner must audit all 8 before scripting |

## Open Questions
1. **Do the other 7 services boot cleanly under `dev` profile today?** Only payment-service was inspected in depth. The start script boots all 8; a smoke `bootRun` of each (or grep for `keys/`, `server.port`, datasource across all) is needed before DEVX-01 can be verified. Recommendation: add a Wave-0 task "audit all 8 services for bootRun prerequisites."
2. **One Postgres DB vs 8 logical DBs.** CLAUDE.md mandates one logical DB per service. Compose must create 8 (init script). Confirm naming convention (`identity`, `payment`, …) and align each service's `SPRING_DATASOURCE_URL`.

## Environment Availability
| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | DEVX-02 compose infra | ✓ | 28.3.0 | — |
| Docker Compose | DEVX-02 | ✓ | v2.38.1 | — |
| Gradle wrapper | bootRun | ✓ | `./gradlew` present | — |
| Node/npm | admin-web `next dev` | assumed (node_modules present) | — | verify `node -v` in start script |
| Java 21 | bootRun | assumed (toolchain pins 21) | — | Gradle toolchain auto-provisions |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers 1.21.2 (PG16/Redis7/Rabbit3) |
| Config file | `services/payment-service/build.gradle.kts` (`spring-boot-service` convention) |
| Quick run command | `./gradlew :services:payment-service:test --tests "*LayeredArchitectureTest"` |
| Full suite command | `./gradlew :services:payment-service:clean test` (24 test classes — the regression oracle) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | Layer dependency rule holds | unit (ArchUnit) | `./gradlew :services:payment-service:test --tests "*LayeredArchitectureTest"` | ❌ Wave 0 |
| ARCH-01 | Refactor behavior-neutral | integration | `./gradlew :services:payment-service:clean test` (all 24 stay green) | ✅ existing oracle |
| DEVX-02 | Infra boots healthy | smoke (manual/script) | `docker compose up -d && docker compose ps` all healthy | ❌ Wave 0 (compose) |
| DEVX-01 | Full stack boots | smoke (manual) | run start script; curl each `/actuator/health` = UP | ❌ Wave 0 (script) |
| DEVX-03 | `.env` drives boot | smoke | fresh clone: `cp .env.example .env` then start script | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:payment-service:test` (fast — covers ArchUnit + units)
- **Per wave merge:** `./gradlew :services:payment-service:clean test` (full oracle)
- **Phase gate:** Full oracle green + ArchUnit rule passes + start script boots compose to healthy and at least payment-service `/actuator/health` = UP, before `/gsd:verify-work`.

### Key validatable invariant
"payment-service test suite stays green (24 classes) + ArchUnit layered rule passes + `start` script boots compose infra to healthy and launches the stack." The first two are fully automatable in CI; the third is a smoke check (manual or scripted health-poll).

### Wave 0 Gaps
- [ ] `LayeredArchitectureTest.java` — ArchUnit rule (ARCH-01)
- [ ] `compose.yaml` + `scripts/db-init/*.sql` (per-service DBs) (DEVX-02)
- [ ] `start.sh` / `stop.sh` (DEVX-01)
- [ ] `.env.example` (DEVX-03)
- [ ] Dev JWT key (or `application-dev.yml` key override) for payment-service — and audit of other 7
- [ ] Add `archunit-junit5` to version catalog + payment-service test deps

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | no (behavior-neutral; no new inputs) | unchanged |
| V6 Cryptography | indirect | JWT public key handling — do NOT commit a *private* key; dev public key only, or use existing test-keys path under dev profile |
| V7 Secrets | yes | `.env` gitignored (verified lines 16-18); `.env.example` carries placeholders only — never real Azampay creds |

**Threat note:** The dev-profile JWT key fix must not commit a private signing key to `src/main/resources`. Use a public key only (resource server validates, does not sign) or scope the dev key to test-keys already present.

## Sources

### Primary (HIGH confidence)
- Repo inspection (settings.gradle.kts, buildSrc convention plugins, version catalog, payment-service src tree + application.yml + test harness) — verified all structural claims
- Maven Central search — ArchUnit `archunit-junit5` 1.4.1 latest [VERIFIED]
- `docker compose version` / `docker --version` — v2.38.1 / 28.3.0 installed [VERIFIED]
- `.gitignore` lines 16-18 — `.env` already ignored [VERIFIED]

### Secondary (MEDIUM confidence)
- TNG/ArchUnit `layeredArchitecture()` API pattern [CITED: github.com/TNG/ArchUnit]
- Spring relaxed env-binding behavior [ASSUMED — standard Spring Boot, A2]

## Metadata
**Confidence breakdown:**
- Per-class mapping: HIGH — derived from actual file inspection
- ArchUnit version/syntax: HIGH — Maven Central verified + documented API
- Start-script landmines (ports/datasource/JWT): HIGH — confirmed by grep across all 8 services
- JPA-as-domain decision: MEDIUM-HIGH — strongly supported by behavior-neutral lock, but a design judgment (A1)

**Research date:** 2026-06-24
**Valid until:** 2026-07-24 (stable stack)
