# Phase 1: Foundation - Research

**Researched:** 2026-06-18
**Domain:** Monorepo scaffold, Docker Compose local dev, GitHub Actions CI, Kustomize/Kubernetes manifests, observability wiring
**Confidence:** HIGH (core stack verified via official docs; a few configuration details MEDIUM)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFR-01 | System deploys to DOKS with 3 Kubernetes Deployments (api, worker, admin-web) via Kustomize | Kustomize 5.5.0 (in kubectl), base + overlay pattern documented — see Architecture Patterns |
| INFR-02 | Local development works with a single `docker compose up` (Postgres, Redis, RabbitMQ) | Docker Compose v2.38 with `depends_on: condition: service_healthy` — health check YAML provided |
| INFR-03 | CI pipeline (GitHub Actions) runs tests and builds images on every PR; deploys to dev on merge to main | GitHub Actions YAML pattern documented; GHCR push with GITHUB_TOKEN verified |
| INFR-04 | System emits structured JSON logs (Loki), metrics (Prometheus → Grafana), and distributed traces (OpenTelemetry + Sentry) | Spring Boot 3.5 BOM manages micrometer-tracing-bridge-otel; manual dep approach required (no Boot 3.5 OTel starter) |
| INFR-05 | All secrets managed via Kubernetes Secrets (Sealed Secrets or ESO) — never committed to Git | Sealed Secrets v0.37.0 documented; kubeseal CLI usage pattern verified |
</phase_requirements>

---

## Summary

Phase 1 lays the entire foundation that all subsequent phases build on: a Gradle multi-module monorepo, Docker Compose local dev stack, GitHub Actions CI pipeline, Kustomize Kubernetes manifests, and wired-in observability. No application business logic is written here — only the scaffold, tooling, and cross-cutting shared libraries (`shared-security`, `shared-observability`).

The monorepo will use a Gradle multi-module structure with a `buildSrc` convention plugin and a `gradle/libs.versions.toml` version catalog. Eight backend service modules (`identity-service`, `catalog-service`, `wallet-service`, `payment-service`, `contact-service`, `messaging-service`, `notification-service`, `admin-service`) plus two shared library modules (`shared-security`, `shared-observability`) and one frontend module (`admin-web`) form the top-level include list in `settings.gradle.kts`. Only application modules run `bootJar`; library modules disable it.

The Docker Compose stack runs Postgres 16, Redis 7, and RabbitMQ 3 with proper `healthcheck` blocks so dependent services wait until databases are truly ready before starting. Spring Boot 3.5 uses the manual observability approach: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` are both managed by the Spring Boot 3.5 BOM (no explicit version needed). `spring-boot-starter-opentelemetry` is a Spring Boot 4 artifact only — do not use it.

**Primary recommendation:** Start with `settings.gradle.kts` + `buildSrc` convention plugins + `gradle/libs.versions.toml`. This produces a one-command setup for any developer, enforces consistent versions across all 10+ modules, and feeds directly into the GitHub Actions `./gradlew build` command that drives the CI pipeline.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Gradle multi-module monorepo structure | Build tooling | — | All 8 services + 2 shared libs share one Gradle build |
| Docker Compose local dev (Postgres, Redis, RabbitMQ) | Local infra | — | Developer machines; not deployed to k8s |
| GitHub Actions CI (compile, test, image, push) | CI/CD pipeline | — | Runs on GitHub-hosted runners, pushes to GHCR |
| Kustomize base + overlay manifests | Kubernetes manifest layer | DOKS runtime | Base manifests + env overlays for 3 deployments |
| JVM memory configuration (`-XX:MaxRAMPercentage`) | Container runtime | Kubernetes resource limits | JVM must be cgroup-aware inside k8s pods |
| Kubernetes health probes (startup/liveness/readiness) | API/Backend tier | Kubernetes control plane | Actuator endpoints consumed by kubelet |
| Sealed Secrets / secret management | Kubernetes cluster | CI/CD pipeline | Encrypted secrets committed to Git, decrypted in-cluster |
| OpenTelemetry traces + Prometheus metrics | Backend (shared-observability lib) | OTel Collector (Docker Compose) | All services import shared lib; collector routes to backends |
| Structured JSON logging (Loki) | Backend runtime | Loki shipper | `logging.structured.format.console=logstash` produces JSON |
| Sentry error tracking | Backend (shared-observability lib) | Sentry cloud | DSN injected as env var; captured by Spring Boot starter |
| Next.js admin-web scaffold | Frontend Server (SSR) | — | Next.js 14 App Router; admin panel |

---

## Standard Stack

### Core — Build System
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Gradle plugin | 3.5.9 [VERIFIED: CLAUDE.md] | `bootJar`, `bootBuildImage`, BOM management | Canonical way to build Spring Boot JARs with Gradle |
| `io.spring.dependency-management` | 1.1.7 [VERIFIED: docs.spring.io/spring-boot/3.5/gradle-plugin] | Applies Spring Boot BOM to non-bootable submodules | Required for shared library modules |
| Gradle | 8.11.1 [VERIFIED: local env] | Build tool; version catalog | Spring Boot 3.5 plugin requires Gradle 8.x |
| `gradle/libs.versions.toml` | Gradle 8 feature | Version catalog | Type-safe accessors for dependencies across 10+ modules |

### Core — Local Dev
| Service | Image | Purpose | Notes |
|---------|-------|---------|-------|
| PostgreSQL | `postgres:16-alpine` [ASSUMED] | Primary DB — all 8 schemas on one instance | 8 schemas created by Flyway migrations at startup |
| Redis | `redis:7-alpine` [ASSUMED] | Cache, OTP storage, distributed locks | |
| RabbitMQ | `rabbitmq:3-management-alpine` [ASSUMED] | Async inter-service messaging | `-management` tag provides UI at port 15672 |
| OTel Collector | `otel/opentelemetry-collector:latest` [CITED: opentelemetry.io/docs/collector/install/docker] | Routes traces/metrics/logs to local backends | Receives on 4317 (gRPC) / 4318 (HTTP) |

### Core — Kubernetes / CI
| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| Kustomize | 5.5.0 (bundled in kubectl) [VERIFIED: local env] | Base + overlay manifest management | No standalone install needed; use `kubectl -k` |
| Sealed Secrets controller | v0.37.0 [CITED: github.com/bitnami-labs/sealed-secrets/releases] | Encrypt secrets for GitOps | Installed via Helm or direct manifest |
| kubeseal CLI | v0.37.0 [CITED: same release page] | Client to seal secrets | Must match controller version |
| GitHub Actions | N/A | CI/CD | `actions/checkout@v6`, `gradle/actions/setup-gradle`, `docker/login-action@v3`, `docker/build-push-action` |

### Core — Observability (Spring Boot 3.5)
| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `spring-boot-starter-actuator` | BOM-managed | Metrics, health, probes | Required base for all observability |
| `micrometer-registry-prometheus` | 1.15.12 (BOM-managed) [VERIFIED: docs.spring.io/spring-boot/3.5] | Prometheus `/actuator/prometheus` endpoint | |
| `micrometer-tracing-bridge-otel` | 1.5.12 (BOM-managed) [VERIFIED: docs.spring.io/spring-boot/3.5] | Bridges Micrometer Observation to OTel | |
| `opentelemetry-exporter-otlp` | 1.49.0 (BOM-managed) [VERIFIED: docs.spring.io/spring-boot/3.5] | Exports traces to OTLP endpoint | |
| `sentry-spring-boot-starter-jakarta` | 8.44.0 [CITED: docs.sentry.io/platforms/java/guides/spring-boot] | Error tracking; Jakarta EE (Spring Boot 3) variant | Explicit version — NOT in Boot BOM |

### Core — Shared Modules
| Module | Type | Purpose |
|--------|------|---------|
| `shared-security` | Gradle `java` library (no bootJar) | JWT validation library; all 8 services depend on it |
| `shared-observability` | Gradle `java` library (no bootJar) | Wires OTel/Prometheus/Sentry; all 8 services depend on it |

### Supporting — Frontend
| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| Next.js | 14.2.35 [VERIFIED: npm registry] | Admin web scaffold | `create-next-app` with `--app` flag (App Router) |
| TypeScript | 5.x (bundled with Next.js 14) [CITED: CLAUDE.md] | Type safety | Auto-configured |
| Tailwind CSS | 3.x [CITED: CLAUDE.md] | Utility CSS | shadcn/ui requires Tailwind 3; do not upgrade to 4 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `bootBuildImage` (CNB buildpacks) | Multi-stage Dockerfile | Buildpacks are zero-config but produce larger images; Dockerfile gives full control over layers and base image size. For MVP with DOKS, either works — but Dockerfile is more transparent |
| Sealed Secrets | External Secrets Operator (ESO) | ESO integrates with cloud vaults (AWS SM, HashiCorp) — appropriate if DO has a managed secrets store. Sealed Secrets is self-contained, simpler at MVP, and does not require external vault infrastructure |
| `gradle/libs.versions.toml` | `buildSrc` with hardcoded constants | Version catalog gives IDE type-safe accessors and is the current Gradle best practice; buildSrc constants work but are less ergonomic |

**Installation notes:**

Gradle (backend):
```bash
# In root build.gradle.kts — no install; Gradle wrapper bootstraps
./gradlew wrapper --gradle-version=8.11.1
```

Next.js (admin-web):
```bash
npx create-next-app@14 apps/admin-web --typescript --tailwind --app --no-src-dir
```

Kubernetes tooling:
```bash
# Sealed Secrets controller
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets-controller sealed-secrets/sealed-secrets \
  --namespace kube-system --version 2.17.0

# kubeseal CLI (macOS example)
brew install kubeseal
```

---

## Package Legitimacy Audit

> This phase installs infrastructure tooling (binaries, not application dependencies). The primary concern is verifying Docker image names and Gradle/npm packages.

| Package | Registry | Notes | slopcheck | Disposition |
|---------|----------|-------|-----------|-------------|
| `next@14.2.35` | npm | Verified via `npm view next@"14.x"` | N/A (pypi check irrelevant) | Approved — confirmed on npm [VERIFIED: npm registry] |
| `typescript` (bundled) | npm | Bundled with Next.js 14; no direct install needed | N/A | Approved |
| `tailwindcss@3.x` | npm | Installed by `create-next-app` | N/A | Approved [CITED: CLAUDE.md] |
| `postgres:16-alpine` | Docker Hub | Official Postgres image | N/A | Approved [ASSUMED] |
| `redis:7-alpine` | Docker Hub | Official Redis image | N/A | Approved [ASSUMED] |
| `rabbitmq:3-management-alpine` | Docker Hub | Official RabbitMQ image with management UI | N/A | Approved [ASSUMED] |
| `otel/opentelemetry-collector` | Docker Hub | OpenTelemetry project official image | N/A (binary) | Approved [CITED: opentelemetry.io] |
| `sentry-spring-boot-starter-jakarta:8.44.0` | Maven Central | Confirmed via docs.sentry.io | N/A (Maven) | Approved [CITED: docs.sentry.io] |
| `sealed-secrets` (pypi check) | PyPI | Pypi result is irrelevant — this is a Helm chart / Go binary | [SLOP on pypi — expected] | Use Helm chart or GitHub release binary; NOT a Python package |

**Note on slopcheck:** slopcheck operates on PyPI. All packages in this phase are either Maven Central (Java), npm (Node.js), Docker Hub (container images), or GitHub releases (Go binaries). The pypi [SLOP] verdict for `sealed-secrets`, `gradle-wrapper`, `spring-boot-gradle-plugin` etc. means those names don't exist on PyPI — this is expected and correct. Cross-ecosystem name checking is not relevant here.

**Packages removed due to slopcheck [SLOP] verdict:** None — all [SLOP] results were expected cross-ecosystem false positives.
**Packages flagged as suspicious [SUS]:** None.

---

## Architecture Patterns

### System Architecture Diagram

```
Developer Machine
     |
     | docker compose up
     v
┌────────────────────────────────────────────────────────────────┐
│  Docker Compose Local Dev Stack                                │
│                                                                │
│  postgres:16  ──► (8 schemas via Flyway migrations)           │
│  redis:7      ──► (cache / OTP / locks)                       │
│  rabbitmq:3   ──► (AMQP broker)                               │
│  otel-collector──► stdout (dev) / Loki+Prometheus (full)      │
│                                                                │
│  Spring Boot services (api, worker) ──► otel-collector:4317   │
│  Next.js admin-web (port 3000)                                 │
└────────────────────────────────────────────────────────────────┘
     |
     | git push / PR opened
     v
┌────────────────────────────────────────────────────────────────┐
│  GitHub Actions CI                                             │
│                                                                │
│  on: pull_request ──► ./gradlew build (compile + unit tests)  │
│                    ──► docker build-push-action ──► GHCR      │
│  on: push to main  ──► kubectl apply -k infra/k8s/dev/        │
└────────────────────────────────────────────────────────────────┘
     |
     | kubectl apply -k
     v
┌────────────────────────────────────────────────────────────────┐
│  DOKS Kubernetes Cluster                                       │
│                                                                │
│  Traefik ingress ──► api Deployment (1 replica)               │
│                  ──► worker Deployment (1 replica)            │
│                  ──► admin-web Deployment (1 replica)         │
│                                                                │
│  SealedSecret ──► controller decrypts ──► Secret ──► Pod env  │
│                                                                │
│  Actuator /actuator/health/liveness   ──► kubelet liveness    │
│  Actuator /actuator/health/readiness  ──► kubelet readiness   │
└────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
sms-reseller/
├── settings.gradle.kts               # include all modules
├── build.gradle.kts                  # root: apply false, allprojects repos
├── gradle/
│   └── libs.versions.toml            # version catalog
├── buildSrc/
│   ├── build.gradle.kts              # declares Spring Boot plugin dep
│   └── src/main/kotlin/
│       ├── spring-boot-service.gradle.kts   # convention: bootJar, Java 21, test config
│       └── spring-boot-library.gradle.kts  # convention: bootJar disabled, java lib
├── services/
│   ├── identity-service/
│   │   ├── build.gradle.kts          # apply("spring-boot-service")
│   │   └── src/
│   ├── catalog-service/
│   ├── wallet-service/
│   ├── payment-service/
│   ├── contact-service/
│   ├── messaging-service/
│   ├── notification-service/
│   └── admin-service/
├── libs/
│   ├── shared-security/
│   │   └── build.gradle.kts          # apply("spring-boot-library")
│   └── shared-observability/
│       └── build.gradle.kts
├── apps/
│   └── admin-web/                    # Next.js 14 App Router
├── infra/
│   ├── docker/
│   │   └── docker-compose.yml
│   └── k8s/
│       ├── base/
│       │   ├── kustomization.yaml
│       │   ├── api-deployment.yaml
│       │   ├── worker-deployment.yaml
│       │   └── admin-web-deployment.yaml
│       ├── dev/
│       │   └── kustomization.yaml    # patch: replicas=1, dev image tags
│       ├── staging/
│       │   └── kustomization.yaml
│       └── prod/
│           └── kustomization.yaml
└── .github/
    └── workflows/
        ├── ci.yml                    # PR: compile + test + build image
        └── deploy-dev.yml            # merge to main: kubectl apply -k dev
```

### Pattern 1: Gradle Convention Plugins via buildSrc

**What:** Convention plugins defined in `buildSrc/` share build logic across all modules without copy-pasting.

**When to use:** Every module. Services use `spring-boot-service`, libraries use `spring-boot-library`.

**Example:**
```kotlin
// Source: docs.gradle.org/current/userguide/multi_project_builds.html [CITED]
// buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")  // Required for Spring expression language
}

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

```kotlin
// buildSrc/src/main/kotlin/spring-boot-library.gradle.kts
// For shared-security, shared-observability — NOT executable JARs
plugins {
    id("java")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Disable bootJar — library modules do not have a main class
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar>().configureEach {
    enabled = false
}
tasks.withType<Jar>().configureEach {
    enabled = true
}
```

### Pattern 2: Docker Compose with Health Checks

**What:** Services use `depends_on: condition: service_healthy` to prevent startup-ordering races.

**When to use:** Any compose service that connects to Postgres, Redis, or RabbitMQ.

**Example:**
```yaml
# Source: docs.docker.com/compose/how-tos/startup-order/ [CITED]
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: smsreseller
      POSTGRES_USER: smsreseller
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdev}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "smsreseller"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 10s
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 3
    ports:
      - "6379:6379"

  rabbitmq:
    image: rabbitmq:3-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: smsreseller
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-localdev}
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
    ports:
      - "5672:5672"
      - "15672:15672"  # Management UI

  otel-collector:
    image: otel/opentelemetry-collector:latest
    volumes:
      - ./otel-collector-config.yaml:/etc/otelcol/config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
      - "8888:8888"   # Collector metrics

volumes:
  postgres_data:
```

### Pattern 3: Kustomize Base + Overlay for Kubernetes Deployments

**What:** A base defines the canonical Deployment; overlays patch image tags and replica counts per environment.

**Example:**
```yaml
# Source: kubectl.docs.kubernetes.io/references/kustomize/kustomization/ [CITED]
# infra/k8s/base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - api-deployment.yaml
  - worker-deployment.yaml
  - admin-web-deployment.yaml
  - api-service.yaml
  - worker-service.yaml
  - admin-web-service.yaml

configMapGenerator:
  - name: app-config
    literals:
      - SPRING_PROFILES_ACTIVE=stub
```

```yaml
# infra/k8s/dev/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../base

namePrefix: dev-

images:
  - name: api
    newName: ghcr.io/yourorg/sms-reseller-api
    newTag: latest
  - name: worker
    newName: ghcr.io/yourorg/sms-reseller-worker
    newTag: latest
  - name: admin-web
    newName: ghcr.io/yourorg/sms-reseller-admin-web
    newTag: latest
```

### Pattern 4: Spring Boot Actuator Probes on DOKS

**What:** Spring Boot Actuator exposes `/actuator/health/liveness` and `/actuator/health/readiness`; Kubernetes kubelet probes use these.

**application.yml:**
```yaml
# Source: docs.spring.io/spring-boot/reference/actuator/endpoints.html [CITED]
management:
  endpoint:
    health:
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: "health,prometheus"
spring:
  application:
    name: api-service
```

**Kubernetes Deployment snippet:**
```yaml
# Source: docs.spring.io/spring-boot/reference/actuator/endpoints.html [CITED]
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 3
  periodSeconds: 10
  initialDelaySeconds: 30
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  failureThreshold: 3
  periodSeconds: 5
  initialDelaySeconds: 10
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```

### Pattern 5: GitHub Actions CI Workflow (PR + Push)

**What:** PR triggers compile + test + Docker build (no push); merge to main triggers build + push to GHCR.

**Example:**
```yaml
# Source: docs.github.com/en/packages [CITED]
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build

  build-and-push-image:
    needs: build-and-test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v6
      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

### Pattern 6: Spring Boot 3.5 Observability Wiring (manual OTel — NOT Boot 4 starter)

**CRITICAL:** `spring-boot-starter-opentelemetry` is a Spring Boot 4 artifact. Spring Boot 3.5 uses individual dependencies managed by the BOM. [VERIFIED: docs.spring.io/spring-boot/3.5/appendix/dependency-versions]

```kotlin
// shared-observability/build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")           // BOM-managed
    implementation("io.micrometer:micrometer-tracing-bridge-otel")          // BOM-managed: 1.5.12
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")          // BOM-managed: 1.49.0
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.44.0")  // NOT in BOM — pin explicitly
}
```

```yaml
# application.yml for services using shared-observability
management:
  tracing:
    sampling:
      probability: 1.0          # 100% in dev; reduce to 0.1 in prod
  otlp:
    tracing:
      endpoint: http://otel-collector:4317   # gRPC endpoint
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
  endpoints:
    web:
      exposure:
        include: health,prometheus
logging:
  structured:
    format:
      console: logstash          # Produces Logstash-compatible JSON for Loki
spring:
  application:
    name: ${SERVICE_NAME:api-service}
sentry:
  dsn: ${SENTRY_DSN:}
  traces-sample-rate: 1.0
```

### Pattern 7: Flyway — 8 Schemas on One Postgres Instance

**What:** Each service's Flyway migrates its own schema. One Postgres database with 8 separate PostgreSQL schemas (not databases).

**When to use:** The Docker Compose postgres image gets a single database (`smsreseller`). Each service connects with a JDBC URL that includes `?currentSchema=identity` etc. Flyway runs at startup and creates the schema if absent.

**Example (identity-service application.yml):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/smsreseller?currentSchema=identity
    username: smsreseller
    password: ${POSTGRES_PASSWORD}
  flyway:
    schemas: identity
    locations: classpath:db/migration
    default-schema: identity
```

**Flyway 10 explicit driver requirement:**
```kotlin
// Every service build.gradle.kts
dependencies {
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")  // Required since Flyway 10 [CITED: CLAUDE.md]
}
```

### Anti-Patterns to Avoid

- **Applying `org.springframework.boot` plugin to shared library modules:** Causes Gradle to generate a `bootJar` that fails because there is no `main()` class. Use `apply false` at root level and only apply in service modules. [CITED: spring.io/guides/gs/multi-module]
- **Using `spring-boot-starter-opentelemetry` with Spring Boot 3.5:** This artifact is Boot 4 only. It will fail to resolve. Use `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` directly (both BOM-managed in 3.5). [VERIFIED: docs.spring.io/spring-boot/3.5/appendix]
- **Omitting `flyway-database-postgresql` artifact:** Flyway 10 split the PostgreSQL driver into a separate module. Without it, migrations silently degrade or throw driver-not-found errors. [CITED: CLAUDE.md]
- **Hardcoding `-Xmx` in JVM args:** Inside a container, the cgroup memory limit changes per environment. Use `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75` so the JVM adapts automatically. [CITED: Kubernetes JVM guides]
- **Starting Spring Boot app service before Postgres is healthy:** Docker Compose `depends_on: condition: service_started` does NOT wait for the DB to accept connections — only `condition: service_healthy` does. [CITED: docs.docker.com/compose]
- **Committing real Kubernetes Secrets to Git:** Kubernetes `Secret` objects are base64 — trivially decoded. All secrets MUST go through Sealed Secrets (or ESO). [CITED: CLAUDE.md INFR-05]
- **Using a management port separate from the app port at MVP:** Splits Actuator onto a different port, causing probes to succeed even when the app port is broken. Use `management.endpoint.health.probes.add-additional-paths: true` to expose on main port instead. [CITED: CLAUDE.md constraint]
- **Using `RestTemplate` in any new code:** Spring Boot 3 documents it as soft-deprecated. Use `RestClient`. [CITED: CLAUDE.md]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Secret encryption for GitOps | Custom base64/AES scripts | Sealed Secrets (kubeseal) | Asymmetric encryption, controller manages key rotation, GitOps-native |
| Multi-environment config overlays | Shell scripts replacing YAML values | Kustomize patches | Declarative, kubectl-native, no templating engine needed |
| Docker image OCI layer optimization | Manual Dockerfile copy-layer tricks | Spring Boot layered JAR (`bootBuildImage` or multi-stage Dockerfile with `COPY --from=builder /app/layers`) | Spring Boot 3.2+ has a `layers.idx` that optimally splits app/deps/Spring/snapshot layers |
| JVM container memory tuning | Manual `-Xmx` calculations | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75` | JVM 11+ reads cgroup limits automatically; hardcoded `-Xmx` breaks when pod resource limits change |
| Prometheus scrape config generation | Custom metrics endpoint | `micrometer-registry-prometheus` | Auto-exposes `/actuator/prometheus` in correct format; handles all timer/gauge/counter registration |
| Distributed trace correlation | Manual MDC injection | `micrometer-tracing-bridge-otel` | Automatically propagates W3C Trace Context headers; correlates logs + traces via `%X{traceId}` |
| Loki log format parsing | Custom JSON serializer | `logging.structured.format.console=logstash` | Spring Boot 3.5 structured logging produces Logstash-compatible JSON directly; no Logback appender config needed |

**Key insight:** In this stack, the "infrastructure as code" layer (Kustomize, Sealed Secrets, Gradle BOM) exists precisely to eliminate hand-rolled scripting. Every hand-rolled alternative introduces a gap that breaks on environment change.

---

## Common Pitfalls

### Pitfall 1: `bootJar` Fails on Library Modules
**What goes wrong:** `Could not find main class` build error on shared library modules.
**Why it happens:** The `org.springframework.boot` plugin is applied to ALL modules via `allprojects`, which enables `bootJar` globally. Library modules have no `main()`.
**How to avoid:** Apply `org.springframework.boot` with `apply false` at root. Convention plugin `spring-boot-library.gradle.kts` explicitly disables `bootJar` and enables plain `jar`.
**Warning signs:** Build error mentioning `No main manifest attribute` or `Could not find main class`.

### Pitfall 2: Spring Boot OTel Starter Doesn't Exist in 3.5
**What goes wrong:** `org.springframework.boot:spring-boot-starter-opentelemetry` fails to resolve. Unresolved dependency error.
**Why it happens:** This artifact was introduced in Spring Boot 4.0 only. Spring Boot 3.5 manages `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` via BOM but has no bundled OTel starter.
**How to avoid:** Use the two individual dependencies (both BOM-managed, no explicit version needed). Do NOT add the OTel starter.
**Warning signs:** `Could not resolve io.spring.dependency-management ... spring-boot-starter-opentelemetry`.

### Pitfall 3: Compose Service Starts Before Database Is Ready
**What goes wrong:** Spring Boot app throws `Connection refused` or Flyway fails on first startup.
**Why it happens:** Docker starts containers in dependency order but does not wait for the inner service to be accepting connections.
**How to avoid:** `depends_on: condition: service_healthy` combined with `healthcheck: test: ["CMD", "pg_isready", "-U", "smsreseller"]` on the postgres service.
**Warning signs:** Intermittent startup failures that succeed on second `docker compose up`.

### Pitfall 4: Flyway Missing `flyway-database-postgresql` on Flyway 10
**What goes wrong:** Flyway silently falls back to a generic JDBC driver or throws a driver resolution error.
**Why it happens:** Flyway 10 moved the PostgreSQL driver support into `org.flywaydb:flyway-database-postgresql` which must be declared explicitly.
**How to avoid:** Every service module that uses Flyway must declare both `flyway-core` and `flyway-database-postgresql`.
**Warning signs:** `No database found to handle jdbc:postgresql://...` at startup.

### Pitfall 5: Sealed Secret Decryption Key Loss
**What goes wrong:** All sealed secrets become undecryptable after controller reinstall or cluster disaster.
**Why it happens:** The controller's private key is stored in a Kubernetes Secret — if the cluster is deleted, the key is gone.
**How to avoid:** Backup the controller key: `kubectl get secret -n kube-system sealed-secrets-key -o yaml > sealed-secrets-backup-key.yaml` and store securely (e.g., in a password manager or DO spaces bucket, NOT in Git).
**Warning signs:** `error decrypting secret` events in controller pod logs after cluster change.

### Pitfall 6: Java Version Mismatch (Dev Machine vs CI vs Container)
**What goes wrong:** Code compiles locally but fails in CI or container — or vice versa.
**Why it happens:** Dev machine may have Java 25 as default (verified: Java 25 on dev machine), but the Gradle toolchain should lock to 21.
**How to avoid:** Set `JavaLanguageVersion.of(21)` in the Gradle Java toolchain block in the convention plugin. Gradle will download Java 21 via toolchain provisioning if not present. In CI, use `actions/setup-java@v4` with `java-version: '21'`. In Dockerfile, use `eclipse-temurin:21-jre-alpine` as runtime base.
**Warning signs:** `UnsupportedClassVersionError`, different behavior locally vs CI.

### Pitfall 7: Next.js 14 `app/` vs `pages/` Directory Confusion
**What goes wrong:** Mixing App Router (`app/`) and Pages Router (`pages/`) patterns creates unexpected routing behavior.
**Why it happens:** `create-next-app` for Next.js 14 defaults to App Router but developers familiar with older Next.js use `pages/` patterns.
**How to avoid:** Use `--app` flag with `create-next-app`. Delete `pages/` directory if it appears. Follow Server Components pattern throughout.
**Warning signs:** Route conflicts, double rendering, API routes not matching expected paths.

### Pitfall 8: `javax.*` Imports in Spring Boot 3
**What goes wrong:** Compilation errors throughout the codebase.
**Why it happens:** Spring Boot 3 uses Jakarta EE 10 — `javax.*` packages do not exist; all are `jakarta.*`.
**How to avoid:** IDE should auto-suggest `jakarta.*` with Spring Boot 3 dependencies. Add a lint/compile check: `grep -r "import javax\." src/ --include="*.java"` to CI.
**Warning signs:** `package javax.validation does not exist`.

---

## Code Examples

### Gradle Root `settings.gradle.kts`
```kotlin
// Source: docs.gradle.org/current/userguide/multi_project_builds.html [CITED]
rootProject.name = "sms-reseller"

include(
    "services:identity-service",
    "services:catalog-service",
    "services:wallet-service",
    "services:payment-service",
    "services:contact-service",
    "services:messaging-service",
    "services:notification-service",
    "services:admin-service",
    "libs:shared-security",
    "libs:shared-observability",
    "apps:admin-web"
)
```

### Gradle `libs.versions.toml` (excerpt)
```toml
# Source: docs.gradle.org/current/userguide/version_catalogs.html [CITED]
[versions]
spring-boot = "3.5.9"
spring-dependency-management = "1.1.7"
mapstruct = "1.6.3"
testcontainers = "1.21.2"
sentry = "8.44.0"

[libraries]
# BOM-managed — no version needed in dependency declarations
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-amqp = { module = "org.springframework.boot:spring-boot-starter-amqp" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-oauth2-resource-server = { module = "org.springframework.boot:spring-boot-starter-oauth2-resource-server" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
flyway-core = { module = "org.flywaydb:flyway-core" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql" }
micrometer-prometheus = { module = "io.micrometer:micrometer-registry-prometheus" }
micrometer-tracing-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel" }
otel-exporter-otlp = { module = "io.opentelemetry:opentelemetry-exporter-otlp" }

# Explicitly versioned — NOT in Boot BOM
mapstruct = { module = "org.mapstruct:mapstruct", version.ref = "mapstruct" }
mapstruct-processor = { module = "org.mapstruct:mapstruct-processor", version.ref = "mapstruct" }
sentry-spring-boot = { module = "io.sentry:sentry-spring-boot-starter-jakarta", version.ref = "sentry" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-rabbitmq = { module = "org.testcontainers:rabbitmq", version.ref = "testcontainers" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
```

### Dockerfile (multi-stage layered JAR)
```dockerfile
# Source: Spring Boot layered JAR documentation [CITED: docs.spring.io]
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY --chown=root:root . .
RUN ./gradlew :services:identity-service:bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/services/identity-service/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
```

### OTel Collector Config (local dev)
```yaml
# Source: opentelemetry.io/docs/collector/install/docker/ [CITED]
# infra/docker/otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:

exporters:
  debug:
    verbosity: normal
  prometheus:
    endpoint: 0.0.0.0:8889

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [debug]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [debug]
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Gradle `allprojects { }` block for shared config | `buildSrc` convention plugins | Gradle 7+ (recommended practice) | Avoids implicit cross-project dependency; faster incremental builds |
| `RestTemplate` for HTTP clients | `RestClient` (sync) / `WebClient` (reactive) | Spring 6.1 / Boot 3.2 | `RestTemplate` is soft-deprecated; new code must use `RestClient` |
| `javax.*` imports | `jakarta.*` imports | Spring Boot 3.0 / Jakarta EE 10 | Hard break — old code fails to compile |
| Flyway bundled PostgreSQL driver | Separate `flyway-database-postgresql` artifact | Flyway 10 | Must be declared explicitly; omission causes runtime driver errors |
| `spring-cloud-sleuth` for tracing | Micrometer Tracing + OTel bridge | Spring Boot 3.0 | Sleuth is discontinued; replaced by Micrometer Tracing |
| Manual `-Xmx` JVM heap sizing | `-XX:MaxRAMPercentage=75.0` | JDK 8u191+ (UseContainerSupport) | JVM dynamically reads cgroup memory limit; hardcoded `-Xmx` fails on pod resize |
| `spring-boot-starter-opentelemetry` | Manual `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` | Spring Boot 4.0 introduces the starter; 3.5 does NOT have it | Do not use the starter with Boot 3.5 |

**Deprecated / outdated:**
- `spring-cloud-sleuth`: Discontinued. All distributed tracing in Spring Boot 3 goes through Micrometer Tracing.
- `docker-compose.yml` `version:` key: No longer required in Docker Compose v2. Omit it.
- `javax.*` packages: Do not exist in Spring Boot 3 / Jakarta EE 10.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Docker image tags `postgres:16-alpine`, `redis:7-alpine`, `rabbitmq:3-management-alpine` are correct official image names | Standard Stack / Code Examples | Wrong image name causes `docker compose up` to fail; easily fixed |
| A2 | Sentry SDK version 8.44.0 is compatible with Spring Boot 3.5 / Jakarta EE | Standard Stack | Incompatible version could cause classpath conflicts; check Sentry docs at onboarding |
| A3 | `bootBuildImage` alternative (CNB buildpacks) produces a working image for DOKS | Alternatives Considered | If buildpack builder is unavailable or too slow in CI, switch to multi-stage Dockerfile |
| A4 | The `management.otlp.tracing.endpoint` property path is correct for Spring Boot 3.5 (vs `management.opentelemetry.tracing.export.otlp.endpoint`) | Code Examples | Wrong property path means traces are silently not exported; test by checking OTel Collector logs |

**If this table is empty:** Not applicable — four assumptions logged above.

---

## Open Questions

1. **Docker image strategy per service: one Dockerfile or per-module?**
   - What we know: The monorepo has 8 services. Multi-stage Dockerfile shown assumes one per service.
   - What's unclear: Whether a single root Dockerfile with `BUILD_SERVICE` ARG is preferable to 8 separate Dockerfiles.
   - Recommendation: Start with 8 separate Dockerfiles in each service directory; they share the same template. If this becomes verbose, evaluate a root Dockerfile with build args later.

2. **Virtual threads (`spring.threads.virtual.enabled=true`) in Phase 1 skeleton**
   - What we know: CLAUDE.md recommends virtual threads for all services (Java 21).
   - What's unclear: Should the shared convention plugin enable virtual threads by default, or leave it to each service's application.yml?
   - Recommendation: Set `spring.threads.virtual.enabled=true` in the shared `application.yml` template within the convention plugin's resource directory, so all services get it automatically.

3. **Sealed Secrets vs ESO for MVP on DOKS**
   - What we know: CLAUDE.md says "Sealed Secrets or ESO". Both are valid.
   - What's unclear: DigitalOcean does not have a managed secrets store; ESO's advantage (external vault) doesn't apply unless DO Spaces is used as a KV backend.
   - Recommendation: Sealed Secrets for MVP. No external vault dependency. Add ESO only if vault integration is needed post-MVP.

4. **`management.otlp.tracing.endpoint` vs `management.opentelemetry.tracing.export.otlp.endpoint`**
   - What we know: Both property paths appear in Spring Boot 3.x documentation; `management.opentelemetry.tracing.export.otlp.*` is documented in Spring Boot 4.x reference.
   - What's unclear: The exact property path for Spring Boot 3.5 gRPC endpoint.
   - Recommendation: Verify empirically at Phase 1 implementation time by checking `spring-boot-actuator-autoconfigure` source for the 3.5 binding class.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 LTS | Spring Boot compilation + runtime | Partial — Java 25 is current; Java 21 via sdkman | 21.0.11-tem (sdkman) | `sdk use java 21.0.11-tem` before building |
| Gradle 8.x | Multi-module build | Yes — Gradle wrapper downloads it | 8.11.1 (local) | Gradle Wrapper auto-provisions |
| Docker Desktop | Local dev stack | Yes | Docker 28.3.0 | — |
| Docker Compose v2 | `docker compose up` | Yes | 2.38.1 | — |
| kubectl | Kubernetes manifest apply | Yes | v1.32.2 | — |
| Kustomize | Overlay apply | Yes (bundled in kubectl) | v5.5.0 | Use `kubectl -k` not standalone `kustomize` |
| Node.js | Next.js admin-web | Yes | v22.17.0 | — |
| npm | Next.js dependencies | Yes | 11.12.1 | — |
| Terraform | DO infrastructure provisioning | No | — | Phase 0 concern; not blocking Phase 1 code |
| kubeseal | Seal secrets locally | No | — | Can be installed via `brew install kubeseal` or GitHub release binary |
| Sentry DSN | Error tracking wiring | No (Phase 0 procurement) | — | DSN injected at runtime via env var; app compiles fine without it |
| GitHub repository (GHCR) | Image push | No (not yet created) | — | Required before CI push step; local builds work without it |

**Missing dependencies with no fallback:**
- None that block Phase 1 development work on the local machine.

**Missing dependencies with fallback:**
- Java 21: Available via sdkman (`sdk use java 21.0.11-tem`) — must set JAVA_HOME to Java 21 before running `./gradlew build`. The Gradle toolchain will also auto-provision Java 21 from Foojay during CI.
- kubeseal: Install via `brew install kubeseal` when ready to create sealed secrets.
- Terraform: Phase 0 procurement; not blocking Phase 1 code.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (JUnit Platform) via `spring-boot-starter-test` (BOM-managed) |
| Config file | None — auto-configured by Spring Boot test starter |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew build` (includes test + jar) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFR-02 | `docker compose up` starts all services without error | Smoke (manual) | `docker compose up --wait && docker compose ps` | No — Wave 0 |
| INFR-03 | CI pipeline compiles and builds image | CI pipeline (GitHub Actions) | `./gradlew build` (invoked by workflow) | No — Wave 0 |
| INFR-04 | Shared-observability lib compiles with OTel/Prometheus dependencies | Unit (compile) | `./gradlew :libs:shared-observability:build` | No — Wave 0 |
| INFR-05 | Sealed secret can be created and decrypted by controller | Manual verification | `kubeseal --fetch-cert ... | kubeseal ...` | No — manual |
| INFR-01 | `kubectl apply -k infra/k8s/dev/` succeeds without errors | Integration (CI) | `kubectl apply -k infra/k8s/dev/ --dry-run=client` | No — Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :libs:shared-security:build :libs:shared-observability:build` (fast — libraries only)
- **Per wave merge:** `./gradlew build` (full multi-module compile + unit tests)
- **Phase gate:** Full suite green + `docker compose up --wait` + `kubectl apply --dry-run=client` before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `libs/shared-security/src/test/java/...` — placeholder unit test to confirm module compiles
- [ ] `libs/shared-observability/src/test/java/...` — placeholder unit test to confirm OTel dependencies resolve
- [ ] `infra/docker/docker-compose.yml` — required before INFR-02 can be validated
- [ ] `infra/k8s/base/kustomization.yaml` — required before INFR-01 dry-run passes
- [ ] `.github/workflows/ci.yml` — required before INFR-03 can be validated

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No — Phase 1 is infrastructure only; no user auth endpoints | — |
| V3 Session Management | No | — |
| V4 Access Control | Partial — Kubernetes RBAC for `kubectl apply` | `kubeconfig` with least-privilege service account in CI |
| V5 Input Validation | No — no application endpoints in this phase | — |
| V6 Cryptography | Yes — Sealed Secrets uses RSA-4096 asymmetric encryption | Sealed Secrets controller (Bitnami) — never hand-roll |
| V7 Error Handling | No — only scaffold | — |
| V9 Communication | Yes — all inter-service comms inside cluster over mTLS or service mesh | Traefik TLS termination at ingress; internal service comms via k8s Service DNS |

### Known Threat Patterns for This Phase's Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret committed to Git | Information Disclosure | Sealed Secrets — never commit plain Kubernetes Secrets |
| Overprivileged CI service account | Elevation of Privilege | Create a dedicated `github-actions-deployer` SA with namespace-scoped Role; not `cluster-admin` |
| Container image with known CVEs | Tampering | Use `eclipse-temurin:21-jre-alpine` (small attack surface); enable GHCR vulnerability scanning |
| Unauthorized image push to GHCR | Spoofing | `GITHUB_TOKEN` scoped to repo; `packages: write` permission only on push-to-main job |
| Docker Desktop daemon access from CI | Elevation of Privilege | GitHub Actions uses ubuntu runner with Docker daemon; do not expose Docker socket to untrusted containers |

---

## Sources

### Primary (HIGH confidence)
- `docs.spring.io/spring-boot/3.5/gradle-plugin/managing-dependencies.html` — Multi-module BOM setup, `apply false`, `io.spring.dependency-management` version 1.1.7
- `docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html` — Confirmed BOM-managed versions: `micrometer-tracing-bridge-otel` 1.5.12, `opentelemetry-exporter-otlp` 1.49.0, `micrometer-registry-prometheus` 1.15.12
- `docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes` — Liveness/readiness/startup probe configuration and application.yaml properties
- `docs.docker.com/compose/how-tos/startup-order/` — `depends_on: condition: service_healthy` syntax, PostgreSQL health check
- `docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows/publishing-and-installing-a-package-with-github-actions` — GHCR workflow with GITHUB_TOKEN
- `kubectl.docs.kubernetes.io/references/kustomize/kustomization/` — kustomization.yaml fields: resources, namePrefix, patches, configMapGenerator
- `spring.io/guides/gs/multi-module/` — Library module configuration: `bootJar` disabled, `jar` enabled
- `docs.sentry.io/platforms/java/guides/spring-boot/` — `sentry-spring-boot-starter-jakarta:8.44.0` for Spring Boot 3, DSN config
- `opentelemetry.io/docs/collector/install/docker/` — OTel Collector Docker image, port mapping, minimal config YAML
- `github.com/bitnami-labs/sealed-secrets/releases` — Sealed Secrets v0.37.0 (latest, 2026-05-21)
- `docs.gradle.org/current/userguide/version_catalogs.html` — libs.versions.toml format and usage

### Secondary (MEDIUM confidence)
- Multiple web sources on JVM `MaxRAMPercentage=75` for Kubernetes — consistent across pretius.com, focused.io
- Spring Boot blog (spring.io/blog/2025/11/18) — confirmed `spring-boot-starter-opentelemetry` is Boot 4 concept
- GitHub Actions Gradle example gist + gradle.io docs — `gradle/actions/setup-gradle@v4` caching behavior

### Tertiary (LOW confidence)
- Docker image tags (`postgres:16-alpine`, `redis:7-alpine`, `rabbitmq:3-management-alpine`) — from training knowledge; verify against Docker Hub official image pages at implementation time [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — Core library versions verified against Spring Boot 3.5 BOM via official docs
- Architecture (Gradle multi-module): HIGH — Verified against Gradle and Spring official guides
- Architecture (Kustomize, CI, Secrets): MEDIUM — Patterns well-documented; specific YAML details need validation against actual cluster
- Pitfalls: HIGH — All pitfalls sourced from official documentation or are direct consequences of documented version changes
- Observability (OTel): MEDIUM — BOM versions verified; exact `management.otlp.tracing.endpoint` property path for Boot 3.5 needs empirical confirmation

**Research date:** 2026-06-18
**Valid until:** 2026-08-18 (60 days — stable ecosystem, no fast-moving parts in this phase)
