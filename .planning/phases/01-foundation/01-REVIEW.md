---
phase: 01-foundation
reviewed: 2026-06-19T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - gradle/libs.versions.toml
  - gradle/wrapper/gradle-wrapper.properties
  - buildSrc/build.gradle.kts
  - buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
  - buildSrc/src/main/kotlin/spring-boot-library.gradle.kts
  - services/identity-service/build.gradle.kts
  - services/catalog-service/build.gradle.kts
  - services/wallet-service/build.gradle.kts
  - services/payment-service/build.gradle.kts
  - services/contact-service/build.gradle.kts
  - services/messaging-service/build.gradle.kts
  - services/notification-service/build.gradle.kts
  - services/admin-service/build.gradle.kts
  - libs/shared-security/build.gradle.kts
  - libs/shared-observability/build.gradle.kts
  - apps/admin-web/build.gradle.kts
  - libs/shared-security/src/main/java/com/opendesk/shared/security/package-info.java
  - libs/shared-security/src/test/java/com/opendesk/shared/security/SharedSecurityModuleTest.java
  - libs/shared-observability/src/main/java/com/opendesk/shared/observability/package-info.java
  - libs/shared-observability/src/test/java/com/opendesk/shared/observability/SharedObservabilityModuleTest.java
  - settings.gradle.kts
  - build.gradle.kts
  - gradle.properties
  - .gitignore
findings:
  critical: 3
  warning: 5
  info: 3
  total: 11
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-19T00:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Reviewed the complete Gradle multi-module monorepo skeleton for the open-desk platform: buildSrc convention plugins, version catalog, 8 service build files, 2 shared library build files, placeholder tests, settings, and configuration files.

The skeleton compiles and passes its Wave 0 tests as documented. However, three defects will cause failures the moment real implementation work begins in Phase 2:

1. The service convention plugin declares only the MapStruct annotation processor but not the MapStruct runtime jar — any service that writes a `@Mapper` interface will compile but throw `ClassNotFoundException` at runtime.
2. `spring-boot-starter-test` appears in neither the service convention plugin nor any of the eight service `build.gradle.kts` files — service-level integration tests added in Phase 2 will fail to compile.
3. `gradle.properties` commits a developer-machine-specific absolute path (`/Users/somar/.sdkman/...`) that will break every CI runner and every other developer's local build.

Five warnings cover version drift from catalog, a missing catalog entry required by CLAUDE.md, and dead code in the library convention plugin.

---

## Structural Findings (fallow)

No structural pre-pass was provided for this review.

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: MapStruct runtime jar absent from service convention plugin — ClassNotFoundException at runtime

**File:** `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts:21-23`
**Issue:** The plugin declares `annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")` (the APT code generator) and `compileOnly("org.projectlombok:lombok")` but omits `implementation("org.mapstruct:mapstruct")`. The `mapstruct` jar contains the `@Mapper`, `@Mapping`, `@MappingTarget` annotations and the base `BaseMapper` infrastructure that generated mapper classes reference at runtime. Without it, generated mapper classes will compile (because mapstruct-processor emits valid source), but every instantiation of a generated mapper will throw `ClassNotFoundException: org.mapstruct.factory.Mappers` at runtime. All eight services inherit this convention plugin; the failure will surface in every service the moment any mapper is introduced in Phase 2.

**Fix:**
```kotlin
// buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
dependencies {
    // ORDER MATTERS: lombok must precede mapstruct-processor (CLAUDE.md hard rule)
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:1.6.3")  // ADD: runtime jar required by generated code
}
```
Alternatively, once the catalog alias is used (see CR-03), reference it via the catalog accessor instead of the hardcoded string.

---

### CR-02: `spring-boot-starter-test` missing from service convention plugin and all eight service build files — service tests will not compile

**File:** `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts` (lines 19-24); also `services/*/build.gradle.kts` (all 8 files)
**Issue:** The `spring-boot-service` convention plugin declares no `testImplementation` dependencies. None of the eight service `build.gradle.kts` files add `spring-boot-starter-test` either. As a result, any test class added to a service in Phase 2 (integration tests, slice tests, unit tests using MockMvc or Spring Test) will fail at compile time with `package org.springframework.boot.test does not exist` and `cannot find symbol: class SpringRunner`. The library modules (`shared-security`, `shared-observability`) correctly declare `testImplementation(libs.spring.boot.starter.test)` in their own build files, but services have no equivalent.

**Fix:** Add to the service convention plugin so all eight services inherit it automatically:
```kotlin
// buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    compileOnly("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:1.6.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")  // ADD
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")             // ADD: version-aligns launcher with BOM engine
}
```

---

### CR-03: `gradle.properties` commits a developer-machine-specific absolute path — breaks CI and other developers

**File:** `gradle.properties:1`
**Issue:** `org.gradle.java.home=/Users/somar/.sdkman/candidates/java/21.0.11-tem` is an absolute path on one developer's laptop. When any CI runner (GitHub Actions, Jenkins, etc.) or a second developer clones the repository, the Gradle build fails immediately with `The configured Java home '/Users/somar/.sdkman/...' does not exist.` This is a BLOCKER for all CI/CD pipelines documented in CLAUDE.md (GitHub Actions → GHCR → DOKS deploy). The Java 21 toolchain is already declared in both convention plugins via `JavaLanguageVersion.of(21)`, so Gradle's toolchain auto-provisioning will download Java 21 automatically without this property. The property provides no benefit and breaks portability.

**Fix:** Remove the `org.gradle.java.home` line entirely. The toolchain declarations in the convention plugins are sufficient.
```properties
# gradle.properties — after fix
org.gradle.daemon=false
```
If local SDKMAN path must be preserved for a specific developer, add `gradle.properties` to `.gitignore` and document that each developer should set `org.gradle.java.home` locally (or rely on toolchain auto-provisioning).

---

## Warnings

### WR-01: `mapstruct-processor` version hardcoded in convention plugin instead of using the catalog — version drift risk

**File:** `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts:22`
**Issue:** The annotation processor is declared as `annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")` — a string literal. The version catalog defines `mapstruct = "1.6.3"` and a `mapstruct-processor` library alias. If the catalog version is bumped in `gradle/libs.versions.toml`, the convention plugin will silently continue using its own hardcoded `1.6.3`, causing a version split where the runtime jar (if added as `libs.mapstruct`) is a different version than the annotation processor. Mixed MapStruct versions produce undefined generated code behavior.

**Note:** Convention plugins in `buildSrc` cannot use `libs.*` catalog accessors directly (catalog is not accessible from `buildSrc` scope). The standard workaround is to read the version from a Gradle extra property or duplicate it as a `buildSrc`-local version constant that cross-references the catalog intent via a comment.

**Fix:** Centralize the version as a constant in buildSrc so it is a single point of truth:
```kotlin
// buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
// Add at top of file:
val mapstructVersion = "1.6.3"  // Must match [versions].mapstruct in gradle/libs.versions.toml

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    compileOnly("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
}
```
A comment on the constant must remind maintainers to keep it synchronized with `libs.versions.toml`.

---

### WR-02: Sentry version `8.44.0` deviates from CLAUDE.md constraint of `7.x Java SDK`

**File:** `gradle/libs.versions.toml:7` and `libs/shared-observability/build.gradle.kts:12`
**Issue:** CLAUDE.md explicitly states `| Sentry | **7.x Java SDK** | Error tracking | ...`. The version catalog pins `sentry = "8.44.0"`. Sentry 8.x is a major version with breaking API changes (renamed configuration properties, removed deprecated initializers, changed event processor interfaces). While Sentry 8.x does support Spring Boot 3, it is a different major version than what the project spec mandates. Future developers or automated renovate bots may reference CLAUDE.md as authoritative and be confused by the discrepancy. More importantly, if any Sentry 8.x breaking change requires a code path not yet written, Phase 2 will hit it unexpectedly.

**Fix (option A — preferred):** Downgrade to match CLAUDE.md:
```toml
# gradle/libs.versions.toml
sentry = "7.22.3"  # Latest stable 7.x as of review date
```
**Fix (option B):** Update CLAUDE.md to reflect the deliberate upgrade to 8.x and document the rationale.

---

### WR-03: `spring-boot-testcontainers` missing from version catalog — blocks `@ServiceConnection` pattern mandated by CLAUDE.md

**File:** `gradle/libs.versions.toml` (missing entry)
**Issue:** CLAUDE.md explicitly mandates: `Use @ServiceConnection on @Bean containers in @TestConfiguration classes` and lists `spring-boot-testcontainers` (BOM-managed, provided by `org.springframework.boot:spring-boot-testcontainers`) as a required supporting library. The version catalog defines `testcontainers-postgresql` and `testcontainers-rabbitmq` correctly but omits `spring-boot-testcontainers`. Without it, `@ServiceConnection` is unavailable and every service integration test written in Phase 2 must fall back to the verbose manual `DynamicPropertySource` wiring — contradicting the documented architecture pattern.

**Fix:** Add to the catalog (BOM-managed, no explicit version needed):
```toml
# gradle/libs.versions.toml [libraries] section
spring-boot-testcontainers = { module = "org.springframework.boot:spring-boot-testcontainers" }
```
Then add to the service convention plugin's `testImplementation` dependencies (or each service individually).

---

### WR-04: Dead code in `spring-boot-library` convention plugin — `BootJar` disable block matches zero tasks

**File:** `buildSrc/src/main/kotlin/spring-boot-library.gradle.kts:24-26`
**Issue:** The library convention plugin does not apply `org.springframework.boot` (by design and per CLAUDE.md). The `BootJar` task is only registered when the Boot plugin is applied. Therefore the block:
```kotlin
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar>().configureEach {
    enabled = false
}
```
configures zero tasks — it is a no-op. The code compiles (the `BootJar` class is on the buildSrc classpath), but it never executes against any real task. This creates a false impression that the `BootJar` task exists and is being disabled; a future maintainer might remove the `apply false` guard thinking this block is sufficient protection.

**Fix:** Remove the dead `BootJar` block from the library plugin. The absence of the Boot plugin is the correct and complete guard. Add a comment explaining why:
```kotlin
// buildSrc/src/main/kotlin/spring-boot-library.gradle.kts
// org.springframework.boot plugin is NOT applied — BootJar task is therefore never registered.
// Plain Jar is produced automatically by the java plugin.
tasks.withType<Jar>().configureEach {
    enabled = true
}
```

---

### WR-05: Critical Phase 2 catalog entries missing — `spring-boot-starter-redis`, `spring-retry`, `resilience4j` absent

**File:** `gradle/libs.versions.toml` (missing entries)
**Issue:** CLAUDE.md mandates three additional dependencies that are absent from the version catalog:
- `spring-boot-starter-data-redis` — required for NIDA OTP storage (`nida:pending:{userId}` Redis key with 10-minute TTL) and rate limiting.
- `spring-retry` — required for Azampay polling recovery and upstream SMS provider calls.
- `resilience4j-spring-boot3` (version `2.2.0`, not BOM-managed) — required to wrap all Azampay, NIDA, and SMS provider calls per CLAUDE.md.

These are not Phase 1 deliverables, but the version catalog is the single source of truth for all declared dependencies. Omitting them now means Phase 2 implementers must discover and add them individually per service, risking version inconsistency across services (e.g., different resilience4j versions in payment-service vs messaging-service).

**Fix:** Add to the catalog now as the canonical declarations:
```toml
# gradle/libs.versions.toml
[versions]
resilience4j = "2.2.0"   # Not in Boot BOM — must be explicit

[libraries]
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis" }
spring-retry = { module = "org.springframework.retry:spring-retry" }
resilience4j-spring-boot3 = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }
```

---

## Info

### IN-01: `gradle-wrapper.jar` is tracked in git — binary artifact with no integrity pin

**File:** `gradle/wrapper/gradle-wrapper.jar` (tracked in git)
**Issue:** The Gradle wrapper JAR is a binary tracked in version control with no SHA-256 checksum in `gradle-wrapper.properties`. Gradle 7.2+ added `distributionSha256Sum` validation; the Gradle security guidelines recommend also validating the wrapper JAR itself via `gradle/verification-metadata.xml`. A tampered `gradle-wrapper.jar` would silently execute arbitrary code at build time for every developer and CI runner. The project already sets `validateDistributionUrl=true` (which validates the distribution zip) but does not extend that protection to the wrapper jar bootstrap.

**Fix:** Generate and commit a `gradle/verification-metadata.xml` with checksums for the wrapper JAR and critical build dependencies:
```bash
./gradlew --write-verification-metadata sha256 help
```
This produces `gradle/verification-metadata.xml` which should be committed and kept up to date.

---

### IN-02: `.gitignore` missing Terraform state file patterns despite Terraform being in the locked stack

**File:** `.gitignore`
**Issue:** CLAUDE.md includes Terraform 1.x as a locked infrastructure tool (`Terraform | 1.x | Cluster/DB/DNS provisioning`). The `.gitignore` does not exclude `*.tfstate`, `*.tfstate.backup`, `.terraform/`, or `*.tfvars`. Terraform state files contain infrastructure secrets (database passwords, API keys, cluster credentials) in plaintext. Accidentally committing a `terraform.tfstate` would be a critical secret exposure. The CLAUDE.md instruction to use DO Spaces as the Terraform backend reduces (but does not eliminate) this risk if a developer runs `terraform init` without configuring the remote backend first.

**Fix:** Add Terraform-specific entries to `.gitignore`:
```gitignore
# Terraform
*.tfstate
*.tfstate.backup
.terraform/
*.tfvars
!*.tfvars.example
```

---

### IN-03: `org.gradle.daemon=false` in shared `gradle.properties` penalizes all developers' local build speed

**File:** `gradle.properties:2`
**Issue:** `org.gradle.daemon=false` disables the Gradle daemon globally for every developer and CI run. The daemon provides significant build speed improvements (JVM warm-up amortized across builds, class caching). Disabling it is appropriate for CI environments (where each run is a fresh process and daemon persistence offers no benefit), but applying it in a committed `gradle.properties` forces all local development builds to pay the cold-start JVM cost on every `./gradlew` invocation. This is a quality-of-life defect; it does not affect correctness.

**Fix:** Remove `org.gradle.daemon=false` from the committed `gradle.properties` (relying on the Gradle default, which is daemon-enabled). In CI pipelines, disable the daemon explicitly in the GitHub Actions step:
```yaml
# .github/workflows/*.yml
- run: ./gradlew build --no-daemon
```
Or set `GRADLE_OPTS: "-Dorg.gradle.daemon=false"` as a CI environment variable.

---

_Reviewed: 2026-06-19T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
