---
phase: 04
plan: 01
subsystem: contact-service, messaging-service
tags: [test-infra, build-scaffolding, testcontainers, wave-0, red-baseline]
dependency_graph:
  requires: []
  provides: [contact-service-build, messaging-service-build, contact-it-base, messaging-it-base, phase-4-red-baseline]
  affects: [04-02, 04-03, 04-04, 04-05, 04-06, 04-07, 04-08]
tech_stack:
  added:
    - commons-csv 1.14.1 (contact-service — CSV import)
    - libphonenumber 9.0.32 (contact-service — E.164 normalization)
    - spring-boot-starter-amqp (both services)
    - spring-boot-starter-security + oauth2-resource-server (both services)
    - resilience4j-spring-boot3 2.2.0 (messaging-service)
    - testcontainers-postgresql + testcontainers-rabbitmq (both services)
  patterns:
    - Testcontainers base class with static containers + @ServiceConnection (PG16)
    - @DynamicPropertySource for RabbitMQ connection wiring
    - Assumptions.abort() placeholder-IT convention (Wave 0 RED baseline)
    - Shortened DLX TTL ladder in application-test.yml (2s/4s/6s)
key_files:
  created:
    - gradle/libs.versions.toml (commons-csv + libphonenumber aliases added)
    - services/contact-service/build.gradle.kts
    - services/messaging-service/build.gradle.kts
    - services/contact-service/src/main/resources/application.yml
    - services/messaging-service/src/main/resources/application.yml
    - services/messaging-service/src/test/resources/application-test.yml
    - services/contact-service/src/test/resources/application-test.yml
    - services/contact-service/src/main/java/com/opendesk/contact/ContactServiceApplication.java
    - services/messaging-service/src/main/java/com/opendesk/messaging/MessagingServiceApplication.java
    - services/contact-service/src/test/java/com/opendesk/contact/AbstractContactIntegrationTest.java
    - services/messaging-service/src/test/java/com/opendesk/messaging/AbstractMessagingIntegrationTest.java
    - 13 placeholder IT/unit test files (9 contact + 13 messaging = 22 test methods total)
    - .planning/phases/04-contacts-messaging/04-VALIDATION.md (populated, nyquist_compliant=true)
  modified:
    - gradle/libs.versions.toml (version + library entries added)
decisions:
  - commons-csv chosen over opencsv for simpler streaming API (no field-mapping ceremony, Reader-based)
  - contact-service has no RabbitMQ in test base (no AMQP consumers in Phase 4)
  - messaging-service has PG16 + RabbitMQ in test base (quorum/DLX consumer tests)
  - RSA-2048 test keypair copied verbatim from wallet-service (same keypair, no new generation — Phase 3 pattern)
  - spring-security + oauth2-resource-server added to both services (nimbus-jose-jwt transitive dep for JwtTestHelper)
metrics:
  duration: ~8 minutes
  completed: 2026-06-21
  tasks: 3
  files: 29
---

# Phase 04 Plan 01: Test Infrastructure & Build Foundation Summary

**One-liner:** Wave 0 Testcontainers IT bases + 22 placeholder aborting tests (RED baseline) for contact-service and messaging-service; commons-csv 1.14.1 and libphonenumber 9.0.32 added to version catalog.

## What Was Built

**Task 1 — Version catalog + build deps + application.yml:**
- Added `commons-csv = "1.14.1"` and `libphonenumber = "9.0.32"` to `gradle/libs.versions.toml`
- contact-service `build.gradle.kts`: commons-csv, libphonenumber, spring-amqp, spring-security, oauth2-resource-server, validation, Testcontainers PG16
- messaging-service `build.gradle.kts`: spring-amqp, spring-security, oauth2-resource-server, resilience4j, validation, Testcontainers PG16 + RabbitMQ
- contact-service `application.yml`: datasource/flyway/resource-server/virtual-threads config
- messaging-service `application.yml`: same base + `default-requeue-rejected: false` + `acknowledge-mode: manual` (D-06, Pitfall 1)
- messaging-service `application-test.yml`: shortened DLX TTL ladder (2s/4s/6s vs 60s/300s/900s prod) for test feedback budget

**Task 2 — Testcontainers IT base classes:**
- `AbstractContactIntegrationTest`: PG16 `@ServiceConnection`, profiles `{stub, test}`, no RabbitMQ (contact-service has no AMQP consumers)
- `AbstractMessagingIntegrationTest`: PG16 `@ServiceConnection` + RabbitMQ `@DynamicPropertySource`, profiles `{stub, test}`
- ContactTestConfiguration + MessagingTestConfiguration with JwtTestHelper bean
- JwtTestHelper extended for messaging-service with `createAdminToken()` (ROLE_ADMIN for sender-ID endpoints)

**Task 3 — Placeholder tests (RED baseline) + validation map:**
- 9 contact-service placeholder tests covering CONT-01..09
- 13 messaging-service placeholder tests covering MESG-01..10 + SNDR-02/03/04
- All 22 report as SKIPPED/ABORTED (not failed) via `Assumptions.abort()`
- `04-VALIDATION.md`: 22-row per-task verification map populated; `nyquist_compliant: true`; `wave_0_complete: true`

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 3f74356 | chore | Version catalog aliases + build deps + application.yml for both services |
| 789f7be | test | Testcontainers IT base classes (AbstractContactIntegrationTest + AbstractMessagingIntegrationTest) |
| 4cec544 | test | 22 placeholder failing tests for all Phase 4 requirement IDs; 04-VALIDATION.md populated |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Added spring-security + oauth2-resource-server to both service builds**
- **Found during:** Task 2 (JwtTestHelper compilation failed with nimbus-jose-jwt missing)
- **Issue:** Both services needed `spring-boot-starter-oauth2-resource-server` for JWT resource server validation and to get `nimbus-jose-jwt` on the test classpath for JwtTestHelper.
- **Fix:** Added `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` to both service build files.
- **Files modified:** `services/contact-service/build.gradle.kts`, `services/messaging-service/build.gradle.kts`
- **Commit:** 789f7be

## Known Stubs

None — this plan creates only test scaffolding and build infrastructure. No production logic.

## Threat Flags

None beyond what the plan's threat model covers:
- commons-csv and libphonenumber are Apache and Google artifacts verified on Maven Central (Package Legitimacy Audit passed in 04-RESEARCH.md)
- Both pinned to exact versions in the version catalog

## Self-Check: PASSED

- AbstractContactIntegrationTest: FOUND at services/contact-service/src/test/java/com/opendesk/contact/AbstractContactIntegrationTest.java
- AbstractMessagingIntegrationTest: FOUND at services/messaging-service/src/test/java/com/opendesk/messaging/AbstractMessagingIntegrationTest.java
- 04-VALIDATION.md: FOUND at .planning/phases/04-contacts-messaging/04-VALIDATION.md with nyquist_compliant: true
- Build: `./gradlew :services:contact-service:compileTestJava :services:messaging-service:compileTestJava` GREEN
- Tests: `./gradlew :services:contact-service:test :services:messaging-service:test` GREEN (22 skipped placeholders)
- Commits 3f74356, 789f7be, 4cec544: VERIFIED in git log
