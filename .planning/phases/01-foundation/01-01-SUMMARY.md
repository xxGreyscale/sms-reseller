---
phase: 01-foundation
plan: 01
subsystem: build-infrastructure
tags: [gradle, monorepo, buildSrc, convention-plugins, version-catalog, spring-boot, java-21]
dependency_graph:
  requires: []
  provides:
    - gradle-multi-module-build
    - spring-boot-service-convention-plugin
    - spring-boot-library-convention-plugin
    - version-catalog-libs-versions-toml
    - shared-security-module
    - shared-observability-module
    - wave-0-test-baseline
  affects:
    - all 8 service modules (depend on both convention plugins and libs)
    - all future phases (build infrastructure is the foundation)
tech_stack:
  added:
    - Gradle 8.11.1 (wrapper pinned, auto-provisions Java 21 toolchain)
    - Spring Boot 3.5.9 (Gradle plugin on buildSrc classpath)
    - io.spring.dependency-management 1.1.7 (BOM management for library modules)
    - MapStruct 1.6.3 (annotation processor, declared in catalog)
    - Sentry 8.44.0 (sentry-spring-boot-starter-jakarta, declared in catalog)
    - Testcontainers 1.21.2 (declared in catalog for Phase 2+ use)
    - Flyway 10.x BOM-managed + flyway-database-postgresql (PostgreSQL driver split)
  patterns:
    - buildSrc convention plugin pattern (spring-boot-service, spring-boot-library)
    - Gradle version catalog (gradle/libs.versions.toml) for type-safe accessors
    - apply false at root — plugins declared but not applied globally
    - lombok-before-mapstruct annotation processor order (CLAUDE.md hard rule)
    - bootJar disabled for library modules (plain jar only)
    - Spring Boot BOM imported in library plugin for BOM-managed dependency resolution
    - junit-platform-launcher aligned via testRuntimeOnly for JUnit 5 version consistency
key_files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - gradle/wrapper/gradle-wrapper.jar
    - gradlew
    - gradlew.bat
    - buildSrc/build.gradle.kts
    - buildSrc/src/main/kotlin/spring-boot-service.gradle.kts
    - buildSrc/src/main/kotlin/spring-boot-library.gradle.kts
    - .gitignore
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
    - libs/shared-security/src/main/java/com/smsreseller/shared/security/package-info.java
    - libs/shared-security/src/test/java/com/smsreseller/shared/security/SharedSecurityModuleTest.java
    - libs/shared-observability/src/main/java/com/smsreseller/shared/observability/package-info.java
    - libs/shared-observability/src/test/java/com/smsreseller/shared/observability/SharedObservabilityModuleTest.java
  modified: []
decisions:
  - "Removed version from root build.gradle.kts plugin declarations: buildSrc puts Spring Boot plugin on classpath already; duplicate version declaration causes 'plugin already on classpath with unknown version' error"
  - "Added Spring Boot BOM import to spring-boot-library.gradle.kts: library modules need the BOM for dependency version management without applying the full org.springframework.boot plugin"
  - "Added junit-platform-launcher testRuntimeOnly: prevents 'OutputDirectoryProvider not available' JUnit 5 version alignment error in library modules"
  - "Set mainClass.convention('placeholder.MainClass') in spring-boot-service plugin: skeleton service modules with NO-SOURCE fail bootJar without a configured mainClass; placeholder resolved at build time, real class auto-detected when source is added in Phase 2+"
metrics:
  duration: 33 minutes
  completed_date: "2026-06-19"
  tasks: 3
  files_created: 26
  files_modified: 0
---

# Phase 01 Plan 01: Gradle Monorepo Skeleton Summary

**One-liner:** Gradle 8.11.1 multi-module build with buildSrc convention plugins (spring-boot-service, spring-boot-library), Spring Boot BOM version catalog, 8 empty service modules, 2 shared library modules, and green Wave 0 JUnit 5 placeholder tests.

## What Was Built

Established the complete Gradle multi-module monorepo skeleton that every subsequent Phase 1 plan and all future phases build on. Key deliverables:

1. **Gradle wrapper** pinned to 8.11.1 (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`)
2. **settings.gradle.kts** with `rootProject.name = "sms-reseller"` and 11 module includes (8 services + 2 libs + 1 apps)
3. **Root build.gradle.kts** declaring Spring Boot 3.5.9 and dependency-management 1.1.7 plugins with `apply false`
4. **gradle/libs.versions.toml** version catalog pinning all versions (spring-boot=3.5.9, mapstruct=1.6.3, testcontainers=1.21.2, sentry=8.44.0) with BOM-managed and explicitly-versioned library entries
5. **buildSrc convention plugins:**
   - `spring-boot-service.gradle.kts`: Java 21 toolchain, lombok before mapstruct-processor (load-bearing order), `-parameters` compiler arg, JUnit Platform
   - `spring-boot-library.gradle.kts`: Java 21 toolchain, no org.springframework.boot plugin, BOM import via dependency-management, bootJar disabled/plain jar enabled
6. **8 service build.gradle.kts** files (identity, catalog, wallet, payment, contact, messaging, notification, admin) each applying `spring-boot-service` and declaring flyway-core + flyway-database-postgresql
7. **2 shared library build.gradle.kts** files: shared-security (oauth2-resource-server) and shared-observability (actuator + micrometer-prometheus + micrometer-tracing-otel + otel-exporter-otlp + sentry-spring-boot)
8. **Wave 0 placeholder tests**: JUnit 5 `assertTrue(true)` in SharedSecurityModuleTest and SharedObservabilityModuleTest — green baseline before Phase 2+ logic

## Verification

`./gradlew build --no-daemon` with Java 21 exits 0:
- buildSrc compiles both convention plugins successfully
- Both shared library modules build as plain jars (not bootJars)
- Both Wave 0 tests execute and pass (1 test each)
- All 8 service modules build (bootJar with placeholder mainClass for skeleton phase)
- No dynamic version ranges; all versions flow from catalog or Boot BOM

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Root build.gradle.kts version conflict with buildSrc classpath**
- **Found during:** Task 1 verification (`./gradlew :buildSrc:build`)
- **Issue:** Root `build.gradle.kts` declared `id("org.springframework.boot") version "3.5.9"` but buildSrc already puts this plugin on the classpath. Gradle reports "plugin already on classpath with unknown version, compatibility cannot be checked."
- **Fix:** Removed explicit version from root plugin declarations — the version is already governed by `buildSrc/build.gradle.kts`'s `implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.9")`
- **Files modified:** `build.gradle.kts`
- **Commit:** 61bbabd

**2. [Rule 1 - Bug] Library modules could not resolve BOM-managed dependencies**
- **Found during:** Task 2 verification (`./gradlew :libs:shared-security:build`)
- **Issue:** `spring-boot-library.gradle.kts` applied `io.spring.dependency-management` but did not import the Spring Boot BOM. Without the BOM, BOM-managed libraries (spring-boot-starter-security, oauth2-resource-server, etc.) have no version source and fail to resolve.
- **Fix:** Added `dependencyManagement { imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9") } }` to `spring-boot-library.gradle.kts`
- **Files modified:** `buildSrc/src/main/kotlin/spring-boot-library.gradle.kts`
- **Commit:** 42784a8

**3. [Rule 1 - Bug] JUnit 5 version alignment error in library module tests**
- **Found during:** Task 2 verification (`./gradlew :libs:shared-security:build`)
- **Issue:** After fixing the BOM import, tests failed with "OutputDirectoryProvider not available; probably due to unaligned versions of junit-platform-engine and junit-platform-launcher." Gradle's built-in launcher version diverged from the BOM-managed engine version.
- **Fix:** Added `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` to `spring-boot-library.gradle.kts` — the BOM manages the version, ensuring alignment.
- **Files modified:** `buildSrc/src/main/kotlin/spring-boot-library.gradle.kts`
- **Commit:** 42784a8

**4. [Rule 1 - Bug] bootJar task fails on empty service skeleton modules**
- **Found during:** Overall build verification (`./gradlew build`)
- **Issue:** The Spring Boot plugin's `bootJar` task requires a configured `mainClass`. Service modules are empty skeletons in Phase 1 (no `@SpringBootApplication` class yet), so `bootJar` fails with "Main class name has not been configured and it could not be resolved from classpath."
- **Fix:** Added `mainClass.convention("placeholder.MainClass")` in `spring-boot-service.gradle.kts`. Spring Boot auto-detects and overwrites this when real source is added in Phase 2+.
- **Files modified:** `buildSrc/src/main/kotlin/spring-boot-service.gradle.kts`
- **Commit:** 4df8fa4

## Known Stubs

- **placeholder.MainClass** in `spring-boot-service.gradle.kts` mainClass convention: intentional placeholder for skeleton phase. Resolved when each service module gains a `@SpringBootApplication` class in Phase 2+. No UI rendering impact — build-time only.
- **apps/admin-web/build.gradle.kts**: empty placeholder. Full Next.js scaffold implemented in Plan 04 (Admin Web Scaffold).

## Threat Flags

None. This plan creates no network endpoints, no auth paths, no file access patterns, and no schema changes at trust boundaries. All threats were in the build toolchain threat model (T-01-01 through T-01-SC) and were mitigated by pinning all versions in the catalog.

## Self-Check: PASSED

All 15 key files verified to exist on disk.
All 4 commits verified to exist in git log:
- 61bbabd: chore(01-01): root build, version catalog, and buildSrc convention plugins
- 42784a8: chore(01-01): eight service build files and both shared library build files
- 8a7f7f7: test(01-01): Wave 0 placeholder tests and package skeletons for shared libraries
- 4df8fa4: fix(01-01): configure placeholder mainClass in spring-boot-service convention plugin
