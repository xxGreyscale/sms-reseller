<!-- GSD:project-start source:PROJECT.md -->
## Project

**SMS Reseller Platform (sms-reseller)**

A NIDA-verified bulk SMS reseller platform built for small organizations in Tanzania. Users register with their National ID (NIDA), purchase prepaid SMS bundles via mobile money (Azampay), manage their contacts, and dispatch bulk messages — all through a simple, mobile-first web interface. The platform acts as a reseller between an upstream SMS provider and small-batch buyers who need simplicity and trust over raw API access.

**Core Value:** Small organizations can send bulk SMS to their members in minutes — verified, trusted, and without needing any technical knowledge.

### Constraints

- **Tech Stack**: Spring Boot 3 + Java 21 (monolith backend), Flutter (customer mobile app), Next.js (admin web) — locked, no deviation
- **Database**: PostgreSQL 16 — 1 cluster, 8 schemas. ACID requirement for wallet/payment
- **Hosting**: DigitalOcean Kubernetes (DOKS) — locked for MVP
- **Payments**: Azampay only at MVP — only aggregator covering M-Pesa/Tigo/Airtel/Halo/AzamPesa in Tanzania
- **Identity**: NIDA only for KYC — core differentiator
- **Replicas**: 1 per Deployment at MVP (api, worker, admin-web), HPA configured for later
- **Target market**: Tanzania only (TZS pricing, NIDA, Azampay, Swahili-aware UX)
- **No code written yet**: Design phase complete, implementation not started
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Core Technologies
### Backend — Spring Boot 3 + Java 21
| Technology | Confirmed Version | Purpose | Validation Notes |
|------------|-------------------|---------|-----------------|
| Spring Boot | **3.5.9** (current OSS patch) | Application framework, all 8 microservices | Context7 verified: 3.5.x ships with Spring Framework 6.2.15, Spring Security 6.5.x, Tomcat 10.1.50. Java 17 minimum, Java 26 maximum — Java 21 is fully supported. |
| Spring Framework | **6.2.15** (managed via Boot BOM) | Core DI, REST, messaging abstraction | Managed automatically by Spring Boot BOM. Do not override unless a specific CVE demands it. |
| Spring Security | **6.5.x** (managed via Boot BOM) | JWT resource server on every service, Traefik pre-auth, CSRF disabled for stateless APIs | Spring Security 6.5 reference docs confirm `NimbusJwtDecoder.withPublicKey()` / `withSecretKey()` builders work as expected. SecurityFilterChain must disable CSRF for stateless JWT microservices. |
| Java | **21 LTS** | Language + runtime | Virtual threads supported via `spring.threads.virtual.enabled=true`. Java 24+ recommended for best virtual thread experience but 21 LTS is the correct stable choice at MVP. Virtual threads auto-configure `SimpleAsyncTaskExecutor` and `SimpleAsyncTaskScheduler`. |
| Gradle | **8.x** (use latest 8 patch) | Build tool, multi-module monorepo | Spring Boot 3.5 Gradle plugin (`org.springframework.boot`) with `io.spring.dependency-management` BOM import manages all transitive versions. Use `JavaLanguageVersion.of(21)` in toolchain block. |
### Frontend — Next.js 14 + TypeScript
| Technology | Confirmed Version | Purpose | Validation Notes |
|------------|-------------------|---------|-----------------|
| Next.js | **14.x** (locked) | Customer web app + admin panel | App Router available. Context7 shows v14 canary and v15 both available; project is locked to 14. Use `app/` directory with App Router — Pages Router is legacy. |
| TypeScript | **5.x** (bundled with Next.js 14) | Type safety across both frontapps | `NextApiRequest`/`NextApiResponse` types for any API routes still on Pages pattern; prefer Server Actions with App Router for mutations. |
| Tailwind CSS | **3.x** | Utility-first styling | shadcn/ui 3.x requires Tailwind 3. Do not upgrade to Tailwind 4 until shadcn confirms support. |
| shadcn/ui | **3.5.x** (latest) | Accessible component library | Context7 shows shadcn 3.5.0 and shadcn@2.9.0 as current versions. Components are copied into the repo (`ui` shared package), not imported from npm — this is by design. |
### Data — PostgreSQL 16
| Technology | Confirmed Version | Purpose | Validation Notes |
|------------|-------------------|---------|-----------------|
| PostgreSQL | **16** (DO Managed) | Primary RDBMS for all 8 services | One logical database per service on the same DO Managed cluster is valid and cost-effective at MVP scale. ACID compliance is the requirement for wallet/payment. |
| Spring Data JPA | Managed by Boot BOM | ORM layer; repositories per service | `@EnableJpaAuditing` available for `createdAt`/`updatedAt` on entities. Do not use cross-service joins — each service owns its schema fully. |
| HikariCP | Managed by Boot BOM | JDBC connection pooling | Spring Boot autoconfigures HikariCP. Configure `spring.datasource.hikari.maximum-pool-size` per service based on pod count. At 1 replica/service MVP, keep pool small (5–10) to avoid exhausting DO Managed Postgres connections. |
| Flyway | **10.x** (managed by Boot BOM) | Schema migrations per service | Spring Boot autoconfigures Flyway. Place migrations in `src/main/resources/db/migration/` as `V{N}__{description}.sql`. `flyway-database-postgresql` must be added explicitly as of Flyway 10 — the core module no longer bundles all DB drivers. |
### Messaging — RabbitMQ via CloudAMQP
| Technology | Confirmed Version | Purpose | Validation Notes |
|------------|-------------------|---------|-----------------|
| RabbitMQ | **3.x** (CloudAMQP managed) | Async inter-service events | Spring Boot `spring-boot-starter-amqp` autoconfigures `RabbitTemplate` and `CachingConnectionFactory`. `@RabbitListener` creates consumer endpoints. CloudAMQP's `amqps://` URI auto-enables SSL — no additional config required. |
| Spring AMQP | Managed by Boot BOM | AMQP abstraction over RabbitMQ | Retry properties configurable at `spring.rabbitmq.template.retry.*` and `spring.rabbitmq.listener.retry.*` — no `@EnableRetry` annotation needed in Spring Boot 3.x. |
### Cache / Locks / OTPs — Redis
| Technology | Confirmed Version | Purpose | Validation Notes |
|------------|-------------------|---------|-----------------|
| Redis | **7.x** (DO Managed) | Caching, distributed locks, OTP storage | `spring-boot-starter-data-redis` autoconfigures `RedisCacheManager` when Redis is on classpath. Key prefix is added automatically to prevent cross-cache key collisions — keep this enabled. Configure TTL per cache via `spring.cache.redis.time-to-live`. OTPs: store as `SETEX key ttl value` via `RedisTemplate` — 5-minute TTL for NIDA verification codes. |
### Infrastructure
| Technology | Version | Purpose | Validation Notes |
|------------|---------|---------|-----------------|
| Traefik | **2.x / 3.x** | Ingress, JWT validation at edge, TLS termination | JWT auth middleware validates tokens before they reach services. Services still validate JWT independently (defense in depth) via Spring Security resource server. |
| DOKS | Current DOKS | Kubernetes cluster | Spring Boot Actuator exposes `/actuator/health/liveness` and `/actuator/health/readiness` automatically when running in a Kubernetes-detected environment. Set `management.endpoint.health.probes.enabled=true` explicitly to be safe. Expose probes on main port (not management port) to avoid false positives. |
| Kustomize | **5.x** | Manifest overlay: base + dev/staging/prod | Use `namePrefix`/`nameSuffix` per overlay; use `ConfigMapGenerator` for non-secret environment config; use Sealed Secrets or DO secret store for credentials. |
| GitHub Actions | N/A | CI/CD pipelines | Build → test → Docker image → push to GHCR → `kubectl apply -k` per environment overlay. |
| Terraform | **1.x** | Cluster/DB/DNS provisioning | Use for DOKS cluster, DO Managed Postgres, DO Managed Redis, DNS records. Keep state in DO Spaces backend. |
### Observability
| Technology | Version | Purpose | Validation Notes |
|------------|---------|---------|-----------------|
| Micrometer | Managed by Boot BOM | Metrics facade | `spring-boot-starter-actuator` includes Micrometer. Add `micrometer-registry-prometheus` for Prometheus scrape endpoint. |
| OpenTelemetry | Managed by Boot BOM | Distributed tracing | Spring Boot 3.x ships Micrometer Tracing with OpenTelemetry bridge. Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`. Configure at `management.opentelemetry.tracing.export.otlp.endpoint`. |
| Loki | N/A (Grafana managed) | Log aggregation | Enable structured JSON logging via `logging.structured.format.console=logstash` — produces Logstash-compatible JSON that Loki can ingest without additional parsing. |
| Sentry | **7.x Java SDK** | Error tracking | Add `sentry-spring-boot-starter-jakarta` (Jakarta EE variant required for Spring Boot 3). |
## Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-validation` | Managed by Boot BOM | Bean Validation (Jakarta EE) on request DTOs | All services — validate all inbound REST payloads with `@Valid` |
| `spring-boot-starter-oauth2-resource-server` | Managed by Boot BOM | JWT resource server validation | All 8 services except identity (which issues tokens, not just validates them) |
| `nimbus-jose-jwt` | Managed transitively | JWT parsing/verification | Pulled in automatically by `spring-security-oauth2-jose`; do not add directly |
| `mapstruct` | **1.6.3** | DTO ↔ domain entity mapping | Use in all services; configure annotation processor in Gradle alongside Lombok |
| `lombok` | Managed by Boot BOM | Boilerplate reduction (`@Data`, `@Builder`, etc.) | All services; place `lombok` before `mapstruct` in annotation processor order |
| `flyway-database-postgresql` | **10.x** (match flyway-core) | PostgreSQL-specific Flyway driver module | Required since Flyway 10 — must be added explicitly alongside `flyway-core` |
| `spring-retry` | Managed by Boot BOM | Retry logic for upstream HTTP calls (NIDA, Azampay, SMS provider) | Payment service for Azampay polling recovery; messaging service for upstream SMS provider calls |
| `resilience4j-spring-boot3` | **2.2.0** | Circuit breaker, rate limiter for external API calls | Wrap Azampay, NIDA, and upstream SMS provider calls; prevents cascade failures |
| `testcontainers-postgresql` | **1.21.2** | Real Postgres in integration tests | All services with database interaction |
| `testcontainers-rabbitmq` | **1.21.2** | Real RabbitMQ in integration tests | Services that produce or consume AMQP events |
| `spring-boot-testcontainers` | Managed by Boot BOM | `@ServiceConnection` annotation for zero-boilerplate container wiring | Use `@ServiceConnection` on `@Bean` containers in `@TestConfiguration` classes |
| `opencsv` or `commons-csv` | **5.x / 1.x** | CSV contact import parsing | Contact service only; `commons-csv` is simpler for streaming large files |
## Gradle Build Setup (Monorepo)
## Version Compatibility Matrix
| Component A | Version | Compatible With | Notes |
|-------------|---------|-----------------|-------|
| Spring Boot | 3.5.9 | Spring Framework 6.2.15 | Managed by BOM — do not override |
| Spring Boot | 3.5.9 | Spring Security 6.5.x | Managed by BOM — do not override |
| Spring Boot | 3.5.9 | Java 21 LTS | Verified — Java 17 minimum, Java 26 maximum |
| Spring Boot | 3.5.9 | Flyway 10.x | Managed by BOM; `flyway-database-postgresql` must be added separately |
| Spring Boot | 3.5.9 | Testcontainers 1.21.2 | Managed by BOM via `spring-boot-testcontainers` |
| Spring Boot | 3.5.9 | Tomcat 10.1.50 | Jakarta EE 10 APIs — use `jakarta.*` imports, NOT `javax.*` |
| Next.js | 14.x | shadcn/ui 3.5.x | shadcn 3.x targets React 18 + Next.js 14 App Router |
| Next.js | 14.x | Tailwind CSS 3.x | Do NOT upgrade to Tailwind 4 until shadcn/ui confirms compatibility |
| MapStruct | 1.6.3 | Lombok | Lombok annotation processor MUST appear before MapStruct in `annotationProcessor` list |
| Flyway | 10.x | PostgreSQL 16 | Requires `flyway-database-postgresql` artifact (split from core in Flyway 10) |
## Patterns
### JWT: Identity service issues, all other services validate
### RabbitMQ: Topic exchange pattern for events
# application.yml per service
### Flyway: One migration set per service
### Testcontainers: @ServiceConnection pattern (Spring Boot 3.1+)
### Virtual Threads (Java 21)
# application.yml
### Spring Boot Actuator on DOKS
# Kubernetes pod spec
### Structured Logging for Loki
## Tanzania-Specific Integration Guidance
### Azampay Integration
- Use `RestClient` (Spring Boot 3.2+ preferred over `RestTemplate`) with `Resilience4j` circuit breaker wrapping all Azampay calls.
- Azampay callback/webhook pattern: implement a public webhook endpoint in the payment service. Validate webhook signatures (if Azampay provides HMAC — verify during merchant onboarding).
- **Polling recovery pattern:** Azampay payments may not callback reliably. Implement a scheduled `@Scheduled` job (e.g., every 2 minutes) that queries pending payments older than 5 minutes and calls Azampay's transaction status API. This is the reconciliation job.
- **Idempotency:** Store Azampay transaction reference on payment record. On duplicate webhook or poll response, use `INSERT ... ON CONFLICT DO NOTHING` (PostgreSQL upsert) to prevent double-crediting the wallet.
- **Currency:** All amounts in TZS (integer cents — no decimals needed). Store as `BIGINT` in PostgreSQL to avoid floating-point issues.
### NIDA API Integration
- Treat as an HTTP API call from the identity service. Wrap in `RestClient` + `Resilience4j` retry (with exponential backoff).
- **Timeout strategy:** NIDA API may be slow (government infra). Set `connectTimeout` to 5s, `readTimeout` to 15s. Surface a user-friendly "verification is taking longer than expected" state in the UI for timeouts.
- **Caching:** Do NOT cache NIDA verification results. Each registration requires a live check. Cache the session state in Redis (e.g., `nida:pending:{userId}` with a 10-minute TTL) to prevent re-submission during a single registration flow.
- **Rate limiting:** Unknown. Implement a Redis-based rate limiter per IP at the API gateway (Traefik) before it hits the identity service.
- **Swahili names:** Tanzania names use UTF-8. Ensure `VARCHAR` columns are UTF-8 encoded (PostgreSQL default). No special handling needed.
## What NOT to Do
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `javax.*` imports | Spring Boot 3 uses Jakarta EE 10 — `javax.*` packages do not exist | `jakarta.*` imports throughout |
| `RestTemplate` for new code | Soft-deprecated in Spring 6; blocked in some future versions | `RestClient` (sync) or `WebClient` (reactive) |
| Sharing database schemas across services | Violates service ownership; creates implicit coupling | One logical database + one Flyway migration set per service |
| Cross-service synchronous HTTP in the critical path of an AMQP consumer | Creates distributed transaction risk and tight coupling | Fire-and-forget AMQP events; if you need data from another service, denormalize it at write time |
| Putting Actuator metrics on a different port from the app at MVP | Probes may succeed when app is broken | Use single port; split only if security requires it in production |
| Flyway Community + PostgreSQL 16 without `flyway-database-postgresql` artifact | Flyway 10 split the PostgreSQL driver module; omitting it causes silent degraded behavior | Add `org.flywaydb:flyway-database-postgresql` explicitly |
| Spring Boot 4.x | Spring Boot 4.0 targets Spring Framework 7 + Spring Security 7 — breaking changes throughout; Jackson 3 replaces Jackson 2 | Stay on Spring Boot 3.5.x for MVP |
| Testcontainers 2.x | v2 is a major API rewrite (JUnit 5 extension model changed); ecosystem tooling still on 1.x | Testcontainers 1.21.2 — stable, Spring Boot 3 fully integrated |
## Sources
- `/spring-projects/spring-boot` (Context7) — Version confirmation (3.5.9), virtual threads, Kubernetes probes, Redis, RabbitMQ, Flyway autoconfiguration, Testcontainers `@ServiceConnection`, structured logging, RestClient
- `/websites/spring_io_spring-boot_3_5` (Context7) — 3.5.x Spring Framework / Spring Security managed versions, OTLP tracing properties
- `/websites/spring_io_spring-security_reference_6_5` (Context7) — JWT resource server configuration, `NimbusJwtDecoder` builders, CSRF disable patterns
- `/flyway/flyway` (Context7) — Migration naming conventions, per-service patterns, Flyway 10 PostgreSQL module split
- `/testcontainers/testcontainers-java` (Context7) — Testcontainers 1.21.2 PostgreSQL + RabbitMQ modules
- `/resilience4j/resilience4j` (Context7) — Circuit breaker + retry decorator patterns
- `/shadcn-ui/ui` (Context7) — shadcn 3.5.x current version confirmation
- `/vercel/next.js` (Context7) — Next.js 14 App Router, TypeScript API route patterns
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
