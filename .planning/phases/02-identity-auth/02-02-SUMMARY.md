---
phase: 02-identity-auth
plan: "02"
subsystem: identity-service, shared-security
tags: [jwt, rsa, user-aggregate, security, flyway, wave-1]
dependency_graph:
  requires: [02-01]
  provides: [user-aggregate, jwt-contract, security-chain, nida-executor]
  affects: [02-03, 02-04, 02-05, all-8-modules]
tech_stack:
  added:
    - org.postgresql:postgresql runtimeOnly (BOM-managed JDBC driver — was missing)
  patterns:
    - NimbusJwtEncoder(ImmutableJWKSet RSAKey) + JwtIssuer for token issuance
    - NimbusJwtDecoder.withPublicKey in shared-security.JwtConfig for all-module validation
    - User JPA entity with Lombok @Builder + @EntityListeners(AuditingEntityListener)
    - DelegatingPasswordEncoder (BCrypt default) — supports future algorithm upgrade
    - Bounded ThreadPoolTaskExecutor("nidaExecutor") core=4, max=8, queue=50
    - StringRedisTemplate + RedisTemplate<String,String> with StringRedisSerializer
key_files:
  created:
    - services/identity-service/src/main/java/com/smsreseller/identity/user/VerificationStatus.java
    - services/identity-service/src/main/java/com/smsreseller/identity/user/User.java
    - services/identity-service/src/main/java/com/smsreseller/identity/user/UserRepository.java
    - services/identity-service/src/main/java/com/smsreseller/identity/user/IdentityUserDetailsService.java
    - services/identity-service/src/main/resources/db/migration/V1__create_users.sql
    - services/identity-service/src/main/java/com/smsreseller/identity/config/JwtConfig.java
    - services/identity-service/src/main/java/com/smsreseller/identity/token/JwtIssuer.java
    - services/identity-service/src/main/java/com/smsreseller/identity/config/SecurityConfig.java
    - services/identity-service/src/main/java/com/smsreseller/identity/config/RedisConfig.java
    - services/identity-service/src/main/java/com/smsreseller/identity/config/AsyncConfig.java
    - libs/shared-security/src/main/java/com/smsreseller/shared/security/JwtConfig.java
    - libs/shared-security/src/main/java/com/smsreseller/shared/security/VerificationStatus.java
    - libs/shared-security/src/main/java/com/smsreseller/shared/security/AuthClaims.java
    - services/identity-service/src/test/java/com/smsreseller/identity/UserPersistenceTest.java
  modified:
    - services/identity-service/src/test/java/com/smsreseller/identity/JwtIssuerUnitTest.java (rewritten from stub)
    - libs/shared-security/src/test/java/com/smsreseller/shared/security/JwtValidationUnitTest.java (forgery test added)
    - services/identity-service/src/test/resources/application-test.yml (removed invalid spring.profiles.active)
    - services/identity-service/build.gradle.kts (added postgresql-driver runtimeOnly)
    - gradle/libs.versions.toml (added postgresql-driver catalog entry)
decisions:
  - "JwtIssuer.withKeys() static factory enables unit testing without Spring context"
  - "shared-security JwtConfig reads spring.security.oauth2.resourceserver.jwt.public-key-location — consistent with Spring Security resource-server pattern used by all 8 downstream modules"
  - "DelegatingPasswordEncoder chosen over raw BCryptPasswordEncoder — enables algorithm migration via {id}hash prefix without schema changes"
  - "RedisTemplate<String,String> with StringRedisSerializer — avoids Java serialization issues across pod restarts"
  - "nidaExecutor queueCapacity=50 — bounded so Resilience4j circuit breaker (02-03) sees rejections during NIDA outages"
metrics:
  duration: "~35 minutes"
  completed: "2026-06-19"
  tasks_completed: 3
  tasks_total: 3
  files_created: 14
  files_modified: 5
---

# Phase 02 Plan 02: JWT Core & User Aggregate Summary

**One-liner:** RSA-2048 asymmetric JWT issuance (NimbusJwtEncoder, identity-service) + NimbusJwtDecoder.withPublicKey shared-security contract, User JPA aggregate with V1 Flyway migration, and stateless SecurityFilterChain with bounded NIDA executor.

## What Was Built

Wave 1 production code for the identity-service JWT core and User aggregate:

1. **Task 1 — User aggregate, V1 migration, UserDetailsService:**
   - `VerificationStatus` enum: `PENDING_VERIFICATION` | `VERIFIED` with contract sync note
   - `User` JPA entity: UUID id, unique email + phone, passwordHash (never serialized), status default `PENDING_VERIFICATION`, createdAt/updatedAt auditing. All `jakarta.persistence.*` imports.
   - `UserRepository`: `findByEmail`, `existsByEmail`, `existsByPhone`
   - `IdentityUserDetailsService`: `loadUserByUsername(email)` → `UserDetails` with BCrypt hash + `ROLE_USER`
   - `V1__create_users.sql`: `users` table with named `UNIQUE` constraints on email and phone, `TIMESTAMPTZ` columns, UTF-8 default (PostgreSQL default)
   - 8 unit tests in `UserPersistenceTest` covering all behaviors — all GREEN

2. **Task 2 — JWT issuance + shared-security validation contract:**
   - Identity `JwtConfig`: loads RSA keys from `app.jwt.*-key-location` (Spring Resource PEM), exposes `JwtEncoder` (NimbusJwtEncoder + ImmutableJWKSet, keyID=`identity-1`) and `JwtDecoder` beans. Private key never logged (T-02-V6).
   - `JwtIssuer`: `issueAccessToken(UUID, VerificationStatus)` → RS256 signed JWT, 15-min TTL, issuer=`https://identity.sms-reseller`, `verification_status` claim (D-02), `roles=[ROLE_USER]`. `withKeys()` static factory for unit testing.
   - `shared-security/JwtConfig`: `@Bean JwtDecoder = NimbusJwtDecoder.withPublicKey(...)` — the bean all 8 downstream modules import.
   - `shared-security/VerificationStatus`: cross-module enum with explicit contract sync documentation.
   - `shared-security/AuthClaims`: `isVerified(Jwt)`, `getVerificationStatus(Jwt)` static helpers — D-02 feature gating API for downstream modules.
   - `JwtIssuerUnitTest`: 5 assertions (exp ≈ 15min, subject, verification_status, issuer, roles) — GREEN
   - `JwtValidationUnitTest`: added forgery-rejection test (T-02-05) + AuthClaims tests — all GREEN

3. **Task 3 — SecurityConfig, RedisConfig, AsyncConfig:**
   - `SecurityConfig`: STATELESS, csrf disabled, `permitAll` on 5 auth paths + `/actuator/health/**`, `anyRequest authenticated`, `oauth2ResourceServer.jwt(Customizer.withDefaults())`, `DelegatingPasswordEncoder` (BCrypt default, D-12), `DaoAuthenticationProvider` + `AuthenticationManager` for IDEN-04 login prep
   - `RedisConfig`: `StringRedisTemplate` + `RedisTemplate<String,String>` with `StringRedisSerializer` for refresh/reset/lockout/nida-pending key namespaces
   - `AsyncConfig`: `@EnableAsync` + bounded `ThreadPoolTaskExecutor` bean `"nidaExecutor"` (core=4, max=8, queue=50) — prevents T-02-DoS unbounded virtual-thread exhaustion during NIDA outages (Pitfall 2)
   - Spring application context loads in integration tests (AbstractIntegrationTest confirms)

## TDD Gate Compliance

- Task 1: RED commit `14e41a5` (UserPersistenceTest) → GREEN commit `1bd0f62` (User aggregate) ✓
- Task 2: RED commit `1f27484` (JwtIssuerUnitTest + forgery test) → GREEN commit `93b21a0` (JWT impl) ✓
- Task 3: Config-only — exempt from RED gate requirement ✓

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing PostgreSQL JDBC driver on identity-service classpath**
- **Found during:** Task 1 — Testcontainers `@ServiceConnection` wires Postgres connection details but the JDBC driver (`org.postgresql:postgresql`) was not on the runtime classpath, causing `Failed to load driver class org.postgresql.Driver`
- **Fix:** Added `postgresql-driver = { module = "org.postgresql:postgresql" }` to `gradle/libs.versions.toml` and `runtimeOnly(libs.postgresql.driver)` to `services/identity-service/build.gradle.kts`
- **Files modified:** `gradle/libs.versions.toml`, `services/identity-service/build.gradle.kts`
- **Commit:** `1bd0f62`

**2. [Rule 1 - Bug] spring.profiles.active invalid in profile-specific application-test.yml**
- **Found during:** Task 1 — Spring Boot 3.5 throws `InvalidConfigDataPropertyException: Property 'spring.profiles.active' imported from location 'class path resource [application-test.yml]' is invalid in a profile specific resource`
- **Fix:** Removed `spring.profiles.active: stub,test` from `application-test.yml`. The profiles are already correctly set via `@ActiveProfiles({"stub", "test"})` on `AbstractIntegrationTest`.
- **Files modified:** `services/identity-service/src/test/resources/application-test.yml`
- **Commit:** `1bd0f62`

**3. [Rule 1 - Bug] Type erasure on `List<?>` getClaim assertion**
- **Found during:** Task 2 — `assertThat(roles).contains("ROLE_USER")` failed to compile due to ambiguous overload on `List<?>` (wildcard captures prevent `String` argument); both `contains(T)` and `containsAnyOf(T...)` triggered the same error
- **Fix:** Cast `decoded.getClaim("roles")` to `(List<String>)` with `@SuppressWarnings("unchecked")`
- **Files modified:** `services/identity-service/src/test/java/com/smsreseller/identity/JwtIssuerUnitTest.java`
- **Commit:** `93b21a0`

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-02-05 (JWT forgery) | Mitigated — `tokenSignedWithForeignKeyIsRejectedByDecoder` test GREEN |
| T-02-01 (password storage) | Mitigated — `DelegatingPasswordEncoder` (BCrypt), `passwordHash` never in DTOs |
| T-02-V6 (RSA private key leak) | Mitigated — `JwtConfig` never logs private key material |
| T-02-DoS (NIDA unbounded executor) | Mitigated — bounded `nidaExecutor` (queue=50, max=8) |
| T-02-AC (access control) | Mitigated — STATELESS + anyRequest authenticated, permitAll only on explicit auth paths |

## Known Stubs

None — all files built in this plan are fully implemented. The following placeholder ITs from 02-01 remain as Assumptions.abort stubs and will be resolved in later plans:

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

## Self-Check: PASSED

- [x] V1__create_users.sql contains "create table users" and unique constraints on email and phone
- [x] User.java imports jakarta.persistence (no javax.)
- [x] JwtConfig.java contains "NimbusJwtEncoder"; shared-security JwtConfig contains "NimbusJwtDecoder.withPublicKey" (confirmed via grep)
- [x] JwtIssuerUnitTest asserts exp within 14-16 minutes of issuance and verification_status claim present — GREEN
- [x] JwtValidationUnitTest: token from identity key decodes; token from foreign key throws JwtException — GREEN
- [x] AuthClaims.isVerified(jwt) returns true only when claim == VERIFIED — GREEN
- [x] No "javax." imports in any created source file
- [x] nidaExecutor bean defined with bounded queueCapacity=50 (not Integer.MAX_VALUE)
- [x] `./gradlew :services:identity-service:test :libs:shared-security:test --no-daemon` — BUILD SUCCESSFUL
- [x] Commits: 14e41a5 (RED user test), 1bd0f62 (GREEN user impl), 1f27484 (RED JWT test), 93b21a0 (GREEN JWT impl), b49c92f (config beans)
