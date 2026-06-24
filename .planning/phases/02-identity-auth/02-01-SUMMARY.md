---
phase: 02-identity-auth
plan: "01"
subsystem: identity-service, shared-security
tags: [test-infrastructure, build, testcontainers, jwt, rsa, wave-0]
dependency_graph:
  requires: [01-01]
  provides: [test-infra-02, jwt-contract-proof, build-deps-02]
  affects: [02-02, 02-03, 02-04, 02-05]
tech_stack:
  added:
    - spring-boot-starter-security (BOM)
    - spring-boot-starter-oauth2-resource-server (BOM)
    - spring-boot-starter-validation (BOM)
    - spring-boot-starter-data-redis (BOM)
    - spring-boot-starter-amqp (BOM)
    - spring-boot-starter-mail catalog entry (BOM, new catalog entry)
    - spring-retry (BOM)
    - resilience4j-spring-boot3 2.2.0
    - mapstruct 1.6.3
    - testcontainers-junit-jupiter 1.21.2 (new catalog entry)
    - testcontainers-postgresql 1.21.2
    - testcontainers-rabbitmq 1.21.2
    - spring-boot-testcontainers (BOM)
  patterns:
    - Testcontainers @ServiceConnection for Postgres 16
    - Testcontainers GenericContainer + @DynamicPropertySource for Redis 7
    - RSA-2048 test keypair (PKCS#8 private, X.509 public) shared between identity and shared-security tests
    - Assumptions.abort for lightweight placeholder ITs (no Spring context, no container spin-up)
key_files:
  created:
    - gradle/libs.versions.toml (spring-boot-starter-mail + testcontainers-junit-jupiter entries added)
    - services/identity-service/build.gradle.kts (full Phase 2 dep set)
    - services/identity-service/src/main/java/com/smsreseller/identity/IdentityServiceApplication.java
    - services/identity-service/src/main/resources/application.yml
    - services/identity-service/src/test/resources/application-test.yml
    - services/identity-service/src/test/java/com/smsreseller/identity/AbstractIntegrationTest.java
    - services/identity-service/src/test/java/com/smsreseller/identity/TestKeys.java
    - services/identity-service/src/test/resources/test-keys/jwt-private.pem
    - services/identity-service/src/test/resources/test-keys/jwt-public.pem
    - services/identity-service/src/test/java/com/smsreseller/identity/RegistrationIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/LoginIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/LockoutIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/RefreshRotationIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/LogoutIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/PasswordResetIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/VerificationOutboxIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/NidaDegradedIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/SenderIdIT.java
    - services/identity-service/src/test/java/com/smsreseller/identity/JwtIssuerUnitTest.java
    - libs/shared-security/src/test/java/com/smsreseller/shared/security/JwtValidationUnitTest.java
    - libs/shared-security/src/test/resources/test-keys/jwt-private.pem
    - libs/shared-security/src/test/resources/test-keys/jwt-public.pem
  modified: []
decisions:
  - "Redis Testcontainers uses GenericContainer(redis:7) + @DynamicPropertySource — no com.redis:testcontainers-redis catalog entry added (not needed; keeps dep set minimal)"
  - "testcontainers-junit-jupiter added as new catalog entry (required for @Testcontainers/@Container annotations; not transitive via spring-boot-testcontainers)"
  - "RSA test keys copied to libs/shared-security/src/test/resources/ so JwtValidationUnitTest runs standalone without depending on identity-service test sources"
metrics:
  duration: "~25 minutes"
  completed: "2026-06-19"
  tasks_completed: 3
  tasks_total: 3
  files_created: 23
  files_modified: 2
---

# Phase 02 Plan 01: Test Infrastructure & Build Foundation Summary

**One-liner:** Testcontainers Postgres 16 + Redis 7 base, shared RSA-2048 test keypair, and 11 placeholder ITs with green cross-module JWT contract proof using NimbusJwtEncoder/NimbusJwtDecoder.

## What Was Built

Wave 0 test infrastructure for the identity-service and shared-security module:

1. **Build deps (Task 1):** Full Phase 2 dependency set added to `services/identity-service/build.gradle.kts` — security, oauth2-resource-server, validation, data-redis, amqp, spring-retry, resilience4j, mapstruct, and three testcontainers modules. Two new catalog entries added: `spring-boot-starter-mail` and `testcontainers-junit-jupiter`.

2. **App entrypoint + config + test fixtures (Task 2):** `IdentityServiceApplication` with `@SpringBootApplication`, `@EnableScheduling`, `@EnableJpaAuditing`. `application.yml` with virtual threads, Flyway, actuator probes, and placeholder JWT/NIDA config keys. `application-test.yml` with short TTLs and create-drop JPA. Fixed RSA-2048 keypair in `test-keys/` (PKCS#8 private, X.509 public). `TestKeys.java` loader. `AbstractIntegrationTest` base with Postgres 16 `@ServiceConnection` + Redis 7 `GenericContainer` via `@DynamicPropertySource`.

3. **Validation map (Task 3):** 10 placeholder IT stubs (one per requirement: IDEN-01 through IDEN-08 + SNDR-01 + JwtIssuer) using `Assumptions.abort` — no Spring context, no container start, cheap to run. `JwtValidationUnitTest` in `shared-security` is a **real green assertion**: signs a JWT with `verification_status: PENDING_VERIFICATION` using the RSA private key and decodes it with `NimbusJwtDecoder.withPublicKey(publicKey)`, proving the D-02 cross-module contract.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing testcontainers-junit-jupiter dependency**
- **Found during:** Task 2 — `AbstractIntegrationTest` could not compile due to missing `org.testcontainers.junit.jupiter` package
- **Issue:** `spring-boot-testcontainers` does not transitively include `testcontainers:junit-jupiter` (the JUnit 5 extension providing `@Testcontainers` and `@Container` annotations)
- **Fix:** Added `testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }` to catalog and added `testImplementation(libs.testcontainers.junit.jupiter)` to identity-service build
- **Files modified:** `gradle/libs.versions.toml`, `services/identity-service/build.gradle.kts`

**2. [Rule 1 - Bug] Ambiguous assertThat overload on getClaim return type**
- **Found during:** Task 3 — `JwtValidationUnitTest` failed to compile with "reference to assertThat is ambiguous" on `decoded.getClaim("verification_status")` (returns `Object`)
- **Fix:** Cast to `(String)` before passing to `assertThat`
- **Files modified:** `libs/shared-security/src/test/java/com/smsreseller/shared/security/JwtValidationUnitTest.java`

**3. [Rule 2 - Design] RSA keys copied to shared-security test resources**
- **Found during:** Task 3 — `JwtValidationUnitTest` in `shared-security` can't reference `TestKeys.java` from `identity-service` test sources (separate Gradle modules with no test source dependency)
- **Fix:** Copied RSA PEM files to `libs/shared-security/src/test/resources/test-keys/` and inlined key-loading helpers in the test. This is correct: each module is independently testable
- **Files created:** Two PEM files in shared-security test resources

## Threat Model Notes

- **T-02-01 (accepted):** RSA test keypair committed to `src/test/resources/test-keys/`. Keys are clearly test-only; prod keys injected via K8s Secret (INFR-05). Files are in `src/test/` scope and excluded from production artifacts.
- **T-02-10 (mitigated):** `JwtValidationUnitTest` proves that a tampered or forged token (signed with any other key) would fail `NimbusJwtDecoder.withPublicKey()` — only the matching public key validates. Contract is green.
- **T-02-SC (mitigated):** All new deps are Maven Central / Spring Boot BOM-managed. `testcontainers-junit-jupiter` is from `org.testcontainers` (same group as existing testcontainers deps). No new third-party registries.

## Known Stubs

The following ITs are intentional placeholder stubs (by design for Wave 0):

| File | Stub method | Resolved in plan |
|------|-------------|-----------------|
| RegistrationIT.java | returnsImmediatelyWithPendingStatus | 02-03 |
| LoginIT.java | returnsAccessAndRefreshTokens | 02-03 |
| LockoutIT.java | locksAccountAfterMaxFailedAttempts | 02-03 |
| RefreshRotationIT.java | rotatesRefreshTokenOnUse | 02-04 |
| LogoutIT.java | revokesCurrentDeviceSessionOnly | 02-04 |
| PasswordResetIT.java | resetsPasswordAndRevokesAllSessions | 02-05 |
| VerificationOutboxIT.java | writesOutboxRowInSameTransactionAsVerifiedFlip | 02-03 |
| NidaDegradedIT.java | staysPendingWhenNidaUnavailableAndScheduledRetryRecovers | 02-03 |
| SenderIdIT.java | assignsDefaultNumericSenderIdOnVerification | 02-03 |
| JwtIssuerUnitTest.java | issuesJwtWithVerificationStatusClaim | 02-02 |

These stubs are intentional — they exist to make the validation map non-empty and give each later plan a named test target to rewrite. They use `Assumptions.abort` so they report as "skipped" not "failed".

## Self-Check: PASSED

- [x] IdentityServiceApplication.java exists and contains @SpringBootApplication + @EnableScheduling
- [x] jwt-private.pem and jwt-public.pem exist in identity-service test-keys/
- [x] AbstractIntegrationTest references "postgres:16" and "redis:7"
- [x] No javax.* imports in created sources
- [x] `./gradlew :services:identity-service:test :libs:shared-security:test --no-daemon` BUILD SUCCESSFUL
- [x] JwtValidationUnitTest passes green with real encode/decode assertion
- [x] Placeholder ITs contain no @SpringBootTest and do not extend AbstractIntegrationTest
- [x] Commits: 469de42 (deps), d6bfac0 (entrypoint+fixture), e527b26 (tests)
