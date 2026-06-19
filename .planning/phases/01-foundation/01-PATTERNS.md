# Phase 1: Foundation - Pattern Map

**Mapped:** 2026-06-18
**Files analyzed:** 28 new files (greenfield — no existing codebase)
**Analogs found:** 0 / 28 in-codebase (project not yet written)
**Pattern source:** Official documentation patterns extracted from RESEARCH.md; named reference projects noted per file

---

## Greenfield Note

This project has no source code yet — only `CLAUDE.md` exists at the repository root. Every
pattern below is drawn from the canonical reference documented in `01-RESEARCH.md`. The
"Analog" column names the authoritative external reference the planner should cite when writing
plan actions. Code excerpts are taken verbatim from RESEARCH.md (which sourced them from
official docs).

---

## File Classification

| New File | Role | Data Flow | Closest Analog Reference | Match Quality |
|----------|------|-----------|--------------------------|---------------|
| `settings.gradle.kts` | config | batch | `docs.gradle.org/current/userguide/multi_project_builds.html` | canonical |
| `build.gradle.kts` (root) | config | batch | Spring Boot multi-module guide — `apply false` pattern | canonical |
| `gradle/libs.versions.toml` | config | batch | `docs.gradle.org/current/userguide/version_catalogs.html` | canonical |
| `buildSrc/build.gradle.kts` | config | batch | Gradle convention plugin bootstrap pattern | canonical |
| `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts` | config | batch | `spring.io/guides/gs/multi-module/` service convention | canonical |
| `buildSrc/src/main/kotlin/spring-boot-library.gradle.kts` | config | batch | `spring.io/guides/gs/multi-module/` library convention | canonical |
| `services/identity-service/build.gradle.kts` | config | batch | `spring-boot-service` convention plugin consumer pattern | role-match |
| `services/*/build.gradle.kts` (×7 additional services) | config | batch | Same as identity-service — identical one-liner apply | role-match |
| `libs/shared-security/build.gradle.kts` | config | batch | `spring-boot-library` convention plugin consumer pattern | canonical |
| `libs/shared-observability/build.gradle.kts` | config | batch | `spring-boot-library` convention + OTel deps pattern | canonical |
| `infra/docker/docker-compose.yml` | config | event-driven | `docs.docker.com/compose/how-tos/startup-order/` | canonical |
| `infra/docker/otel-collector-config.yaml` | config | event-driven | `opentelemetry.io/docs/collector/install/docker/` | canonical |
| `infra/k8s/base/kustomization.yaml` | config | batch | `kubectl.docs.kubernetes.io/references/kustomize/kustomization/` | canonical |
| `infra/k8s/base/api-deployment.yaml` | config | request-response | Spring Boot Actuator probe pattern + JVM container flags | canonical |
| `infra/k8s/base/worker-deployment.yaml` | config | event-driven | Same probe pattern as api-deployment; worker variant | role-match |
| `infra/k8s/base/admin-web-deployment.yaml` | config | request-response | Next.js 14 deployment; same probe structure | role-match |
| `infra/k8s/base/api-service.yaml` | config | request-response | Standard Kubernetes ClusterIP Service pattern | canonical |
| `infra/k8s/base/worker-service.yaml` | config | event-driven | Same ClusterIP pattern; headless acceptable for worker | role-match |
| `infra/k8s/base/admin-web-service.yaml` | config | request-response | Same ClusterIP pattern | role-match |
| `infra/k8s/dev/kustomization.yaml` | config | batch | Kustomize overlay `namePrefix` + image tag patch pattern | canonical |
| `infra/k8s/staging/kustomization.yaml` | config | batch | Same overlay pattern; staging image tags | role-match |
| `infra/k8s/prod/kustomization.yaml` | config | batch | Same overlay pattern; prod image tags | role-match |
| `.github/workflows/ci.yml` | config | batch | `docs.github.com/en/packages` — GHCR push + `GITHUB_TOKEN` | canonical |
| `.github/workflows/deploy-dev.yml` | config | batch | Same CI structure; push-only step with `kubectl -k` | role-match |
| `services/identity-service/Dockerfile` (template) | config | batch | Spring Boot layered JAR multi-stage Dockerfile pattern | canonical |
| `services/identity-service/src/main/resources/application.yml` | config | request-response | Spring Boot Actuator + OTel + Loki structured logging pattern | canonical |
| `libs/shared-observability/src/main/java/.../ObservabilityAutoConfiguration.java` | config | batch | `sentry-spring-boot-starter-jakarta` + `micrometer-tracing-bridge-otel` wiring | canonical |
| `apps/admin-web/` (Next.js scaffold) | component | request-response | `create-next-app@14 --app --typescript --tailwind` | canonical |

---

## Pattern Assignments

### `settings.gradle.kts`
**Role:** config — build entry point
**Analog:** `docs.gradle.org/current/userguide/multi_project_builds.html`

**Full file pattern** (from RESEARCH.md Code Examples):
```kotlin
// Source: docs.gradle.org/current/userguide/multi_project_builds.html [CITED]
rootProject.name = "open-desk"

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

---

### `build.gradle.kts` (root)
**Role:** config — root build file
**Analog:** Spring Boot multi-module guide `apply false` pattern

**Core pattern:**
```kotlin
// Root build.gradle.kts — plugins declared but NOT applied globally
plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Repositories visible to all subprojects
allprojects {
    repositories {
        mavenCentral()
    }
}
```

**Critical rule:** `apply false` at root is mandatory. If the Boot plugin is applied to all
subprojects, library modules (`shared-security`, `shared-observability`) get `bootJar` enabled,
which fails because they have no `main()` class.

---

### `gradle/libs.versions.toml`
**Role:** config — version catalog
**Analog:** `docs.gradle.org/current/userguide/version_catalogs.html`

**Full excerpt pattern** (from RESEARCH.md Code Examples):
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

---

### `buildSrc/build.gradle.kts`
**Role:** config — convention plugin classpath setup
**Analog:** Gradle convention plugin bootstrap pattern

**Core pattern:**
```kotlin
// buildSrc/build.gradle.kts
// Puts the Spring Boot plugin on the classpath so convention plugins can reference it
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.9")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")
}
```

---

### `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts`
**Role:** config — service convention plugin (applied to all 8 Spring Boot service modules)
**Analog:** `spring.io/guides/gs/multi-module/` + RESEARCH.md Pattern 1

**Full pattern** (from RESEARCH.md Pattern 1):
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

**Virtual threads addition** (from RESEARCH.md Open Question 2 recommendation):
```yaml
# Template application.yml included in convention plugin resource directory
# src/main/resources/application.yml (service template)
spring:
  threads:
    virtual:
      enabled: true
```

---

### `buildSrc/src/main/kotlin/spring-boot-library.gradle.kts`
**Role:** config — library convention plugin (applied to `shared-security`, `shared-observability`)
**Analog:** `spring.io/guides/gs/multi-module/` library variant + RESEARCH.md Pattern 1

**Full pattern** (from RESEARCH.md Pattern 1):
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

**Key difference from service plugin:** No `org.springframework.boot` plugin (only
`io.spring.dependency-management` for BOM access). `bootJar` explicitly disabled, plain `jar`
explicitly enabled.

---

### `services/*/build.gradle.kts` (all 8 service modules)
**Role:** config — service module build file
**Analog:** Convention plugin consumer pattern from `spring.io/guides/gs/multi-module/`

**Core pattern (identical for all 8 services):**
```kotlin
// services/identity-service/build.gradle.kts  (same for all 8 services)
plugins {
    id("spring-boot-service")
}

dependencies {
    implementation(project(":libs:shared-security"))
    implementation(project(":libs:shared-observability"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)  // REQUIRED: Flyway 10 split — do not omit
    // Service-specific deps added here in later phases
}
```

---

### `libs/shared-security/build.gradle.kts`
**Role:** config — security library build file
**Analog:** `spring-boot-library` convention + `spring-boot-starter-oauth2-resource-server`

**Core pattern:**
```kotlin
// libs/shared-security/build.gradle.kts
plugins {
    id("spring-boot-library")
}

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    // nimbus-jose-jwt is pulled transitively by oauth2-resource-server — do not add directly
}
```

---

### `libs/shared-observability/build.gradle.kts`
**Role:** config — observability library build file
**Analog:** RESEARCH.md Pattern 6 — manual OTel wiring (NOT Boot 4 starter)

**Full dependency pattern** (from RESEARCH.md Pattern 6):
```kotlin
// libs/shared-observability/build.gradle.kts
// Source: docs.spring.io/spring-boot/3.5/appendix/dependency-versions [VERIFIED]
plugins {
    id("spring-boot-library")
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)           // BOM-managed: 1.15.12
    implementation(libs.micrometer.tracing.otel)        // BOM-managed: 1.5.12
    implementation(libs.otel.exporter.otlp)             // BOM-managed: 1.49.0
    implementation(libs.sentry.spring.boot)             // NOT in BOM — pinned to 8.44.0
}
```

**CRITICAL:** Do NOT use `org.springframework.boot:spring-boot-starter-opentelemetry` — that
artifact is Boot 4 only and will fail to resolve under Boot 3.5.

---

### `infra/docker/docker-compose.yml`
**Role:** config — local dev stack
**Analog:** `docs.docker.com/compose/how-tos/startup-order/` — RESEARCH.md Pattern 2

**Full pattern** (from RESEARCH.md Pattern 2):
```yaml
# Source: docs.docker.com/compose/how-tos/startup-order/ [CITED]
# NOTE: Do NOT include a top-level "version:" key — deprecated in Docker Compose v2
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: opendesk
      POSTGRES_USER: opendesk
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdev}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "opendesk"]
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
      RABBITMQ_DEFAULT_USER: opendesk
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

**Key rule:** Any Spring Boot service added to compose must declare
`depends_on: postgres: condition: service_healthy` — NOT `condition: service_started`.

---

### `infra/docker/otel-collector-config.yaml`
**Role:** config — OTel Collector routing
**Analog:** `opentelemetry.io/docs/collector/install/docker/` — RESEARCH.md Code Examples

**Full pattern** (from RESEARCH.md Code Examples):
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

### `infra/k8s/base/kustomization.yaml`
**Role:** config — Kustomize base manifest
**Analog:** `kubectl.docs.kubernetes.io/references/kustomize/kustomization/` — RESEARCH.md Pattern 3

**Full pattern** (from RESEARCH.md Pattern 3):
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

---

### `infra/k8s/base/api-deployment.yaml`
**Role:** config — Spring Boot API Kubernetes Deployment
**Analog:** `docs.spring.io/spring-boot/reference/actuator/endpoints.html` — RESEARCH.md Pattern 4

**Health probe and JVM flags pattern** (from RESEARCH.md Pattern 4):
```yaml
# Source: docs.spring.io/spring-boot/reference/actuator/endpoints.html [CITED]
spec:
  containers:
    - name: api
      image: api  # Overridden by Kustomize overlay
      ports:
        - containerPort: 8080
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
      resources:
        requests:
          memory: "256Mi"
          cpu: "100m"
        limits:
          memory: "512Mi"
          cpu: "500m"
```

**Critical rules:**
- Never use `-Xmx` directly — only `MaxRAMPercentage=75` so JVM adapts to pod memory limits.
- Do NOT put Actuator on a separate management port at MVP — probes would succeed even when
  the main app port is broken.
- `management.endpoint.health.probes.enabled: true` must be set explicitly in application.yml.

---

### `infra/k8s/base/worker-deployment.yaml`
**Role:** config — worker Kubernetes Deployment (AMQP consumer)
**Analog:** Same probe pattern as `api-deployment.yaml`; worker has no inbound HTTP routes but
still exposes Actuator on a port for health probes.

**Difference from api-deployment:** No Ingress/Service needed for worker HTTP traffic. Only
Actuator health port exposed. Probe paths identical.

---

### `infra/k8s/base/admin-web-deployment.yaml`
**Role:** config — Next.js Kubernetes Deployment
**Analog:** Standard Next.js 14 pod pattern; no Spring Boot probes — use HTTP `GET /` or a
dedicated `/api/health` route as liveness/readiness target.

**Core pattern:**
```yaml
spec:
  containers:
    - name: admin-web
      image: admin-web  # Overridden by Kustomize overlay
      ports:
        - containerPort: 3000
      livenessProbe:
        httpGet:
          path: /api/health
          port: 3000
        failureThreshold: 3
        periodSeconds: 10
        initialDelaySeconds: 15
      readinessProbe:
        httpGet:
          path: /api/health
          port: 3000
        failureThreshold: 3
        periodSeconds: 5
        initialDelaySeconds: 10
      resources:
        requests:
          memory: "128Mi"
          cpu: "50m"
        limits:
          memory: "256Mi"
          cpu: "200m"
```

---

### `infra/k8s/base/api-service.yaml`, `worker-service.yaml`, `admin-web-service.yaml`
**Role:** config — Kubernetes ClusterIP Services
**Analog:** Standard Kubernetes ClusterIP Service pattern

**Core pattern (apply to all three):**
```yaml
apiVersion: v1
kind: Service
metadata:
  name: api          # Change per service: api / worker / admin-web
spec:
  selector:
    app: api         # Must match Deployment pod label
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080   # 3000 for admin-web
  type: ClusterIP
```

---

### `infra/k8s/dev/kustomization.yaml`
**Role:** config — dev overlay
**Analog:** `kubectl.docs.kubernetes.io/references/kustomize/kustomization/` — RESEARCH.md Pattern 3

**Full pattern** (from RESEARCH.md Pattern 3):
```yaml
# Source: kubectl.docs.kubernetes.io/references/kustomize/kustomization/ [CITED]
# infra/k8s/dev/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../base

namePrefix: dev-

images:
  - name: api
    newName: ghcr.io/yourorg/open-desk-api
    newTag: latest
  - name: worker
    newName: ghcr.io/yourorg/open-desk-worker
    newTag: latest
  - name: admin-web
    newName: ghcr.io/yourorg/open-desk-admin-web
    newTag: latest
```

**staging and prod overlays** use the same structure; replace `namePrefix: dev-` with
`staging-` / `prod-` and use specific image digest tags (`@sha256:...`) instead of `latest`
for prod.

---

### `.github/workflows/ci.yml`
**Role:** config — CI pipeline
**Analog:** `docs.github.com/en/packages` — RESEARCH.md Pattern 5

**Full pattern** (from RESEARCH.md Pattern 5):
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

**CI lint addition** (from RESEARCH.md Pitfall 8):
Add this step to `build-and-test` job to catch `javax.*` import regressions:
```yaml
      - name: Check for javax.* imports (must use jakarta.*)
        run: |
          if grep -r "import javax\." services/ libs/ --include="*.java" -l; then
            echo "ERROR: Found javax.* imports. Spring Boot 3 requires jakarta.*"
            exit 1
          fi
```

---

### `.github/workflows/deploy-dev.yml`
**Role:** config — deploy-on-merge pipeline
**Analog:** Same GitHub Actions structure as ci.yml; push-only deploy step

**Core deploy step pattern:**
```yaml
name: Deploy Dev

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Set up kubectl
        uses: azure/setup-kubectl@v4
        with:
          version: 'v1.32.2'
      - name: Configure kubeconfig
        run: |
          mkdir -p $HOME/.kube
          echo "${{ secrets.KUBECONFIG }}" > $HOME/.kube/config
      - name: Deploy to dev
        run: kubectl apply -k infra/k8s/dev/
```

---

### `services/identity-service/Dockerfile` (template for all services)
**Role:** config — container image build
**Analog:** Spring Boot layered JAR multi-stage Dockerfile — RESEARCH.md Code Examples

**Full pattern** (from RESEARCH.md Code Examples):
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
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", \
            "org.springframework.boot.loader.launch.JarLauncher"]
```

**Adapt per service:** Change the `:services:identity-service:bootJar` target and
`/app/services/identity-service/build/libs/` path for each service's Dockerfile.

---

### `services/identity-service/src/main/resources/application.yml` (template)
**Role:** config — Spring Boot application configuration
**Analog:** RESEARCH.md Patterns 4 + 6 — Actuator probes + OTel + structured logging

**Full pattern** (composed from RESEARCH.md Patterns 4 and 6):
```yaml
# Source: docs.spring.io/spring-boot/reference/actuator + docs.spring.io/spring-boot/3.5 [CITED]
spring:
  application:
    name: ${SERVICE_NAME:identity-service}
  threads:
    virtual:
      enabled: true         # Java 21 virtual threads
  datasource:
    url: jdbc:postgresql://postgres:5432/opendesk?currentSchema=identity
    username: opendesk
    password: ${POSTGRES_PASSWORD}
  flyway:
    schemas: identity
    locations: classpath:db/migration
    default-schema: identity

management:
  endpoint:
    health:
      probes:
        enabled: true       # Exposes /actuator/health/liveness and /readiness
  endpoints:
    web:
      exposure:
        include: "health,prometheus"
  tracing:
    sampling:
      probability: 1.0      # 100% in dev; reduce to 0.1 in prod overlay
  otlp:
    tracing:
      endpoint: http://otel-collector:4317
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics

logging:
  structured:
    format:
      console: logstash     # Produces Logstash-compatible JSON for Loki ingest

sentry:
  dsn: ${SENTRY_DSN:}       # Empty string disables Sentry without error
  traces-sample-rate: 1.0
```

**Note on OTel property path (from RESEARCH.md Open Question 4):** The property
`management.otlp.tracing.endpoint` is the Boot 3.5 path. The `management.opentelemetry.*`
namespace is Boot 4.x only. Verify empirically at implementation time.

---

### `libs/shared-observability/src/main/java/.../ObservabilityAutoConfiguration.java`
**Role:** config — Spring auto-configuration for observability wiring
**Analog:** Spring Boot `@AutoConfiguration` pattern; Sentry + Micrometer autoconfiguration

**Core pattern:**
```java
// libs/shared-observability/src/main/java/com/opendesk/shared/observability/ObservabilityAutoConfiguration.java
package com.opendesk.shared.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan
public class ObservabilityAutoConfiguration {
    // All beans (Sentry, Micrometer, OTel) are autoconfigured by their respective starters.
    // This class exists to trigger component scanning of this library package.
    // Register in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
}
```

**Registration file (required for Spring Boot 3 auto-configuration detection):**
```
# libs/shared-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.opendesk.shared.observability.ObservabilityAutoConfiguration
```

---

### `apps/admin-web/` (Next.js 14 scaffold)
**Role:** component — admin web frontend
**Analog:** `create-next-app@14` with App Router — RESEARCH.md Standard Stack

**Bootstrap command** (from RESEARCH.md Standard Stack):
```bash
npx create-next-app@14 apps/admin-web --typescript --tailwind --app --no-src-dir
```

**App Router health route** (needed for Kubernetes readiness probe):
```typescript
// apps/admin-web/app/api/health/route.ts
import { NextResponse } from 'next/server';

export async function GET() {
  return NextResponse.json({ status: 'ok' });
}
```

**Critical rules (from RESEARCH.md Pitfall 7):**
- Use `app/` directory exclusively — never `pages/`
- Delete `pages/` if `create-next-app` generates it
- Follow Server Components pattern; use Server Actions for mutations

---

## Shared Patterns

### Flyway 10 — PostgreSQL Driver Split
**Source:** RESEARCH.md Pattern 7 + Pitfall 4 (citing `CLAUDE.md` and Flyway 10 docs)
**Apply to:** All 8 service `build.gradle.kts` files that declare Flyway

Every service that uses Flyway must declare BOTH:
```kotlin
implementation(libs.flyway.core)
implementation(libs.flyway.postgresql)  // org.flywaydb:flyway-database-postgresql
```

Omitting `flyway-database-postgresql` causes `No database found to handle jdbc:postgresql://...`
at startup — this is a silent degradation in Flyway 10.

---

### Annotation Processor Order (Lombok before MapStruct)
**Source:** `CLAUDE.md` — "Lombok annotation processor MUST appear before MapStruct"
**Apply to:** `spring-boot-service.gradle.kts` convention plugin and all service `build.gradle.kts`

```kotlin
dependencies {
    // ORDER MATTERS: lombok must precede mapstruct-processor
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:1.6.3")
}
```

---

### Jakarta EE Imports (never javax.*)
**Source:** RESEARCH.md Pitfall 8 + `CLAUDE.md`
**Apply to:** All Java source files across all 8 services and 2 shared libs

```java
// CORRECT — Spring Boot 3 / Jakarta EE 10
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;

// WRONG — will not compile under Spring Boot 3
// import javax.validation.Valid;
// import javax.persistence.Entity;
```

---

### `depends_on: condition: service_healthy` (Docker Compose startup ordering)
**Source:** RESEARCH.md Pattern 2 + Pitfall 3
**Apply to:** Any Spring Boot service added to `docker-compose.yml`

```yaml
# Apply to every spring boot service container in docker-compose.yml
depends_on:
  postgres:
    condition: service_healthy
  redis:
    condition: service_healthy
  rabbitmq:
    condition: service_healthy
```

Using `condition: service_started` (the default) does NOT wait for the inner service to accept
connections — intermittent Flyway failures result.

---

### JVM Container Memory Flag (never -Xmx)
**Source:** RESEARCH.md Pattern 4 + Pitfall (Don't Hand-Roll section)
**Apply to:** All Kubernetes Deployment YAML files and all Dockerfiles

```yaml
# Kubernetes Deployment env — apply to api, worker, admin-web (Spring Boot)
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
```

```dockerfile
# Dockerfile ENTRYPOINT — all Spring Boot service images
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", \
            "org.springframework.boot.loader.launch.JarLauncher"]
```

---

### Sealed Secrets — Commit-Safe Kubernetes Secrets
**Source:** RESEARCH.md Standard Stack (Sealed Secrets v0.37.0) + INFR-05
**Apply to:** All Kubernetes secret manifests; the CI deploy workflow

```bash
# Seal a secret (run locally once controller is installed)
kubectl create secret generic db-credentials \
  --from-literal=POSTGRES_PASSWORD=<value> \
  --dry-run=client -o yaml | \
  kubeseal --format yaml > infra/k8s/base/db-credentials-sealed.yaml
```

```yaml
# Result — safe to commit to Git
# infra/k8s/base/db-credentials-sealed.yaml
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: db-credentials
spec:
  encryptedData:
    POSTGRES_PASSWORD: <encrypted-base64>
```

**Backup the controller key** to a secure location (NOT Git) after cluster creation:
```bash
kubectl get secret -n kube-system sealed-secrets-key -o yaml > sealed-secrets-backup-key.yaml
```

---

### Actuator Probe Enablement (application.yml mandatory block)
**Source:** RESEARCH.md Pattern 4
**Apply to:** `application.yml` of all 8 Spring Boot services

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true       # REQUIRED — without this, /liveness and /readiness return 404
  endpoints:
    web:
      exposure:
        include: "health,prometheus"
```

Do NOT split Actuator onto a separate management port at MVP — probes would succeed even when
the main application port is broken.

---

## No Analog Found (in-codebase)

All 28 files have no in-codebase analog because the project is greenfield. The table below
lists files where the external reference pattern is less fully specified in RESEARCH.md, so the
planner should note these as areas requiring research-driven inference.

| File | Role | Data Flow | Reason / Guidance |
|------|------|-----------|-------------------|
| `infra/k8s/base/worker-deployment.yaml` | config | event-driven | No reference pattern for worker-without-ingress; copy api-deployment probe section, remove ingress/service port exposure |
| `infra/k8s/base/admin-web-deployment.yaml` | config | request-response | Next.js 14 Kubernetes deployment — probe target is `/api/health` route; resource requests lower than JVM services |
| `.github/workflows/deploy-dev.yml` | config | batch | Kubeconfig injection pattern for DOKS needs empirical validation — `secrets.KUBECONFIG` must be base64-encoded kubeconfig stored as GitHub Actions secret |
| `libs/shared-security/src/main/java/.../` | config | request-response | JWT resource server bean configuration — pattern documented in `CLAUDE.md` (NimbusJwtDecoder) but full class structure is inferred from Spring Security 6.5 docs; no in-codebase example |
| `infra/k8s/staging/kustomization.yaml` | config | batch | Staging-specific: image digest (not `latest`) preferred; replica count can remain 1 at MVP |
| `infra/k8s/prod/kustomization.yaml` | config | batch | Prod-specific: `@sha256:` digests required; HPA stub should be commented in for later activation |

---

## Metadata

**Analog search scope:** Entire repository root (`/Users/somar/Desktop/private/open-desk/`)
**Files scanned:** 1 (`CLAUDE.md` only — codebase is greenfield)
**Pattern source:** RESEARCH.md (01-RESEARCH.md) — all patterns sourced from official docs
**Pattern extraction date:** 2026-06-18
**Valid until:** 2026-08-18 (matches RESEARCH.md validity window)
