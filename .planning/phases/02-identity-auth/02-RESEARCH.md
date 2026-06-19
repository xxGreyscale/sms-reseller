# Phase 2: Identity & Auth - Research

**Researched:** 2026-06-19
**Domain:** Spring Security 6.5 JWT issuance + resource-server validation, async external verification (mock-first), session/refresh-token lifecycle, password reset, transactional outbox eventing
**Confidence:** HIGH (stack is locked and BOM-managed; all APIs verified against Spring Security 6.5 docs and Spring Boot 3.5 reference)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** "Logged in, but walled." On successful registration the user immediately receives a session (access + refresh JWT) and is in PENDING_VERIFICATION status. They can log in and see the app shell, but every feature shows a "verification pending" gated state until status flips to VERIFIED.
- **D-02:** The JWT MUST carry the verification status as a claim (`verification_status: PENDING_VERIFICATION | VERIFIED`) so downstream modules enforce the wall locally without calling identity at runtime. Load-bearing design choice for the modular monolith.
- **D-03:** When NIDA verifies (or background retry succeeds), status → VERIFIED, 50 free SMS credits are granted (IDEN-03), and the default numeric sender ID is assigned (SNDR-01). Newly issued/refreshed tokens then carry the VERIFIED claim.
- **D-04:** `NidaVerificationService` interface with `StubNidaVerificationService` (`@Profile("stub")`, default in dev/staging) and `RealNidaVerificationService` (`@Profile("prod")`).
- **D-05:** Stub supports configurable outcomes — success / rejection / timeout / unavailable — via config and/or magic-NIN convention. Default outcome auto-verify after ~3s. Makes IDEN-08 degraded path demoable end-to-end.
- **D-06:** Tokens: 15-minute access JWT + 7-day refresh token.
- **D-07:** Multi-device, revoke-current session model. Logout (IDEN-06) revokes only the current device's session.
- **D-08:** Refresh tokens rotate on each use (used token invalidated, new one issued).
- **D-09:** Successful password reset revokes ALL sessions for that user.
- **D-10:** Login is email + password. Registration captures both phone and email; email is the login identifier.
- **D-11:** Password reset (IDEN-07) via email link with time-limited, single-use token.
- **D-12:** Password policy: minimum 8 characters + basic strength check. Account lockout after ~5 failed attempts for a short cooldown.
- **D-13:** Email sending is mock-first — interface + stub (records link in dev) + real impl behind a profile.

### Claude's Discretion
- Exact lockout thresholds, cooldown duration, password-strength rule specifics.
- Magic-NIN suffix convention and stub config surface for D-05.
- Refresh-token storage mechanism (Redis vs Postgres) — CLAUDE.md lists Redis for sessions/OTP; researcher to confirm.
- Email provider selection for D-13.

### Deferred Ideas (OUT OF SCOPE)
- Bundle catalog (definitions + read API) → Phase 3 (PYMT-01).
- Custom alphanumeric sender IDs + admin approval (SNDR-02/03/04) → Phase 4.
- Wallet/credit ledger mechanics → Phase 3 (consumes the "50 free credits granted" event only).
- Reset-by-SMS → email-only at MVP.
- Reconsider D-09 if too aggressive UX.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IDEN-01 | Register with phone number + email | Registration controller + `users` table; BCrypt password hash; Bean Validation on DTO. Both phone + email captured (D-10). |
| IDEN-02 | NIDA verify — returns PENDING immediately (async) | `@Async` dispatch on dedicated bounded executor; user persisted PENDING_VERIFICATION before NIDA call returns (D-01). |
| IDEN-03 | 50 free SMS credits on verification | Transactional outbox row written in same TX as status→VERIFIED; relay publishes `UserVerified` event to RabbitMQ for Phase 3 wallet. |
| IDEN-04 | Log in with email + password | `AuthenticationManager` / `DaoAuthenticationProvider` with `UserDetailsService`; issue access+refresh on success. |
| IDEN-05 | Session persists (JWT + refresh token) | RSA-signed access JWT (NimbusJwtEncoder) + opaque refresh token stored in Redis. |
| IDEN-06 | Log out and revoke session | Delete current device's refresh-token key from Redis (revoke-current, D-07). |
| IDEN-07 | Reset password via email link | Single-use, TTL'd reset token (Redis); mock-first email interface (D-13); revoke-all on success (D-09). |
| IDEN-08 | Graceful degrade when NIDA unavailable | Resilience4j circuit breaker + spring-retry; stay PENDING; `@Scheduled` background retry job; stub `unavailable`/`timeout` outcomes (D-05). |
| SNDR-01 | Default numeric sender ID at registration | Generated on verification (D-03) into `sender_ids` table; numeric shortcode format. |
</phase_requirements>

## Summary

This phase turns the identity-service skeleton into the platform's sole JWT issuer and makes `libs/shared-security` a real validating library. The single most consequential design choice is **asymmetric signing**: identity-service signs access tokens with an **RSA private key** (`NimbusJwtEncoder`), and every other module validates with the matching **RSA public key** (`NimbusJwtDecoder.withPublicKey(...)`). This is what lets `verification_status` (D-02) be trusted everywhere with zero runtime callback to identity. A shared HMAC secret would also work but is rejected: it gives every module the power to *forge* tokens, which breaks the "identity issues, all others only validate" contract in CLAUDE.md.

Access tokens are short-lived (15 min, stateless, never stored). **Refresh tokens are opaque random strings stored in Redis** keyed per device, enabling rotation-on-use (D-08), revoke-current logout (D-07), and revoke-all-on-reset (D-09) — all of which require server-side state that a stateless JWT cannot provide. NIDA verification is fully async (D-01/IDEN-02): the user is persisted PENDING and returned immediately, while the actual NIDA call runs on a **dedicated bounded executor** (not the default virtual-thread `SimpleAsyncTaskExecutor`, which is unbounded and would let NIDA latency exhaust resources), wrapped in a Resilience4j circuit breaker + spring-retry. A `@Scheduled` job retries stuck PENDING users for the IDEN-08 degraded path. The 50-credit grant (IDEN-03) crosses to Phase 3's wallet via a **transactional outbox** written in the same DB transaction as the VERIFIED flip, then relayed to RabbitMQ — never a synchronous cross-module call (CLAUDE.md hard rule).

**Primary recommendation:** RSA keypair signing (identity holds private key, shared-security holds public key via a config property), opaque refresh tokens in Redis with rotation, async NIDA on a bounded dedicated executor behind Resilience4j, and a transactional-outbox → RabbitMQ event for the credit grant.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Register / login / password reset endpoints | API (identity-service) | — | identity-service owns auth surface |
| JWT *issuance* (sign with RSA private key) | API (identity-service) | — | identity is the sole issuer (CLAUDE.md) |
| JWT *validation* (verify with RSA public key) | Shared library (shared-security) | All 8 modules | each module validates locally, no callback (D-02) |
| Refresh-token store / rotation / revoke | Redis (DO Managed) | API (identity-service) | server-side session state; fast TTL ops |
| Password hashing | API (identity-service) | — | BCrypt via Spring Security |
| Account lockout counter | Redis | API (identity-service) | atomic INCR + TTL cooldown |
| Password-reset token (single-use, TTL) | Redis | API (identity-service) | TTL + single-use delete fits Redis |
| NIDA call | External API | API (identity-service, async) | wrapped in circuit breaker; mock-first stub |
| Verification state machine (PENDING→VERIFIED) | Database (identity schema) | API | durable status is source of truth; JWT claim is a cache |
| "50 free credits granted" event | Database outbox → RabbitMQ | Phase 3 wallet (consumer) | clean async boundary, no sync cross-module call |
| Default numeric sender ID | Database (identity schema) | API | assigned at verification |
| Email delivery | External (mock-first) | API | interface + stub + profiled real impl |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-security | BOM (6.5.x) | Auth, password encoding, filter chain | Already in catalog; project standard |
| spring-boot-starter-oauth2-resource-server | BOM | JWT decode/validate; pulls `spring-security-oauth2-jose` + `nimbus-jose-jwt` | Already in shared-security; `NimbusJwtEncoder` lives in `oauth2-jose` (transitive) |
| spring-boot-starter-data-jpa | BOM | `users`, `sender_ids`, `outbox` persistence | Already on identity-service |
| spring-boot-starter-data-redis | BOM | Refresh tokens, lockout counter, reset tokens, NIDA session state | Catalog confirmed; CLAUDE.md mandates Redis for sessions/OTP |
| spring-boot-starter-web | BOM | REST controllers | Already on identity-service |
| spring-boot-starter-validation | BOM | `@Valid` on register/login/reset DTOs | CLAUDE.md: validate all inbound payloads |
| spring-boot-starter-amqp | BOM | Publish `UserVerified` to RabbitMQ | **Not yet on identity-service build.gradle.kts — must be added** |
| flyway-core + flyway-database-postgresql | BOM (10.x) | Identity schema migrations | Already on identity-service |
| spring-retry | BOM | Exponential-backoff retry on NIDA calls | Catalog confirmed; CLAUDE.md NIDA guidance |
| resilience4j-spring-boot3 | 2.2.0 | Circuit breaker on NIDA (+ optional rate limiter) | Catalog confirmed; CLAUDE.md NIDA guidance |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| mapstruct + mapstruct-processor | 1.6.3 | DTO ↔ entity mapping | All controllers/services (CLAUDE.md) |
| lombok | BOM | Boilerplate (`@Data`, `@Builder`) | Entities/DTOs (processor order: lombok before mapstruct) |
| spring-boot-starter-mail (`JavaMailSender`) | BOM | Real email impl behind `@Profile("prod")` | Only the real email impl; stub needs nothing |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| RSA asymmetric signing | Shared HMAC secret (`NimbusJwtDecoder.withSecretKey`) | HMAC is simpler (one secret) but gives every module forge capability — violates "identity issues, others only validate". **Rejected.** |
| Public key as config property | Full JWKS endpoint on identity + `withJwkSetUri` | JWKS enables key rotation without redeploy, but adds a runtime HTTP dependency from every module to identity — contradicts D-02's "no runtime callback". At MVP (1 replica each), distribute the **public key via config/secret**; document JWKS as the post-MVP rotation path. |
| Opaque refresh tokens in Redis | JWT refresh tokens | JWTs can't be revoked server-side without a denylist anyway; rotation (D-08), revoke-current (D-07), revoke-all (D-09) all need server state → opaque + Redis is simpler and strictly correct. |
| Transactional outbox | Direct `RabbitTemplate.send()` in the verification TX | Direct publish risks lost event if broker is down after DB commit (or phantom event if publish succeeds then DB rolls back). Outbox guarantees exactly-the-committed-state is published. CLAUDE.md mandates event/outbox for this boundary. |
| BCrypt | Argon2 (`Argon2PasswordEncoder`) | Argon2 is stronger but needs BouncyCastle and tuning; BCrypt (strength 10–12) is the Spring default via `PasswordEncoderFactories.createDelegatingPasswordEncoder()`, battle-tested, zero extra deps. **Use BCrypt** (D-12 only requires min-8 + lockout). |

**Installation (add to `services/identity-service/build.gradle.kts`):**
```kotlin
implementation(libs.spring.boot.starter.security)
implementation(libs.spring.boot.starter.validation)
implementation(libs.spring.boot.starter.data.redis)
implementation(libs.spring.boot.starter.amqp)          // for UserVerified event
implementation(libs.spring.retry)
implementation(libs.resilience4j.spring.boot3)
implementation(libs.mapstruct)
annotationProcessor(libs.mapstruct.processor)
// spring-mail (real email impl) — add a catalog entry: spring-boot-starter-mail
testImplementation(libs.spring.boot.testcontainers)
testImplementation(libs.testcontainers.postgresql)
// add catalog entry: testcontainers-redis (com.redis:testcontainers-redis) OR use GenericContainer
```
Note: `oauth2-resource-server` is inherited transitively via `:libs:shared-security`, but identity-service also needs `spring-security-oauth2-jose` on its own classpath for `NimbusJwtEncoder`. Adding `spring-boot-starter-oauth2-resource-server` directly to identity-service is the clean way to guarantee the encoder class is present.

**Version verification:** All libraries above are BOM-managed by Spring Boot 3.5.9 or already pinned in `gradle/libs.versions.toml` (resilience4j 2.2.0, testcontainers 1.21.2, mapstruct 1.6.3) and were verified present in the catalog this session. `spring-boot-starter-mail` and a Redis Testcontainers module are the only new catalog entries needed. [VERIFIED: gradle/libs.versions.toml]

## Package Legitimacy Audit

> No new third-party packages from public registries are introduced. Every dependency is either Spring Boot BOM-managed (Maven Central, `org.springframework.*` / `io.micrometer.*`) or already pinned in the project version catalog from Phase 1.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| org.springframework.boot:* | Maven Central | 10+ yrs | massive | github.com/spring-projects/spring-boot | n/a (Maven, BOM-managed) | Approved |
| io.github.resilience4j:resilience4j-spring-boot3 | Maven Central | 7+ yrs | high | github.com/resilience4j/resilience4j | n/a | Approved (already in catalog) |
| org.mapstruct:mapstruct | Maven Central | 10+ yrs | high | github.com/mapstruct/mapstruct | n/a | Approved (already in catalog) |
| spring-boot-starter-mail | Maven Central | BOM-managed | massive | spring-projects/spring-boot | n/a | Approved (new catalog entry) |

slopcheck targets npm/PyPI; these are Maven Central artifacts from `org.springframework`/`io.github.resilience4j`/`org.mapstruct` with long histories and official source repos — no hallucination risk. **Packages removed: none. Packages flagged: none.**

## Architecture Patterns

### System Architecture Diagram

```
                          REGISTRATION (IDEN-01/02)
  client ──register(email,phone,nin,pwd)──▶ identity-service
                                              │ persist user (PENDING_VERIFICATION), BCrypt pwd
                                              │ issue access(PENDING) + refresh(Redis)   ◀── returns immediately (D-01)
                                              │
                                              └─@Async (dedicated bounded executor)─▶ NidaVerificationService
                                                                                        │  (interface)
                                              ┌──────────────────────────────────┐      ├─Stub @Profile(stub)  [configurable outcome, D-05]
                                              │ Resilience4j CB + spring-retry    │◀─────┤
                                              └──────────────────────────────────┘      └─Real @Profile(prod)  [RestClient → NIDA gov API]
                                                          │ success
                                                          ▼  (single DB TX)
                                          ┌────────────────────────────────────────────┐
                                          │ user.status = VERIFIED                       │
                                          │ INSERT sender_ids (numeric shortcode, SNDR-01)│
                                          │ INSERT outbox (UserVerified{userId,50})       │  IDEN-03
                                          └────────────────────────────────────────────┘
                                                          │  commit
                            @Scheduled relay ────────────┤
                                                          ▼
                                                  RabbitMQ topic exchange ──▶ Phase 3 wallet (grants 50 credits)

  @Scheduled retry job ──poll PENDING users older than N──▶ re-run verification  (IDEN-08 degraded recovery)

                          LOGIN / SESSION (IDEN-04/05/06)
  client ──login(email,pwd)──▶ AuthenticationManager(BCrypt) ──▶ [lockout check: Redis INCR]
                                              │ success
                                              ▼ issue access JWT (RSA-signed, verification_status claim)
                                                + opaque refresh token  ──store──▶ Redis  refresh:{userId}:{deviceId}
  client ──refresh(refreshToken)──▶ validate+delete old key ──issue new pair (rotation, D-08)──▶ Redis
  client ──logout──▶ delete refresh:{userId}:{currentDeviceId}                          (revoke-current, D-07)

                          VALIDATION (every other module)
  client ──Bearer access──▶ Traefik (edge JWT pre-check) ──▶ any module
                                              └─ shared-security: NimbusJwtDecoder.withPublicKey(RSA_PUB)
                                                  reads verification_status claim ──▶ gate features locally (D-02)

                          PASSWORD RESET (IDEN-07)
  client ──forgot(email)──▶ create single-use token ──Redis SETEX reset:{token} ttl──▶ EmailSender (stub records link)
  client ──reset(token,newPwd)──▶ validate+delete token ──BCrypt new pwd──▶ delete ALL refresh:{userId}:* (revoke-all, D-09)
```

### Recommended Project Structure
```
services/identity-service/src/main/java/com/opendesk/identity/
├── IdentityServiceApplication.java        # @SpringBootApplication (replaces placeholder.MainClass)
├── config/
│   ├── SecurityConfig.java                # SecurityFilterChain: stateless, CSRF off, permit auth endpoints
│   ├── JwtConfig.java                     # JwtEncoder (RSA private) + JwtDecoder (RSA public) beans
│   ├── RedisConfig.java                   # RedisTemplate / StringRedisTemplate
│   └── AsyncConfig.java                   # dedicated bounded ThreadPoolTaskExecutor for NIDA ("nidaExecutor")
├── auth/                                  # register, login, refresh, logout controllers + services
├── token/                                 # JwtIssuer (claims incl. verification_status), RefreshTokenService (Redis)
├── verification/
│   ├── NidaVerificationService.java       # interface (D-04)
│   ├── StubNidaVerificationService.java   # @Profile("stub") configurable outcomes (D-05)
│   ├── RealNidaVerificationService.java   # @Profile("prod") RestClient + Resilience4j
│   └── VerificationRetryJob.java          # @Scheduled background retry (IDEN-08)
├── password/                              # PasswordResetService, EmailSender interface + Stub/Real (D-13)
├── lockout/                               # LoginAttemptService (Redis INCR + TTL)
├── sender/                                # SenderIdService (numeric shortcode generation, SNDR-01)
├── outbox/                                # OutboxEntry entity, OutboxRelay @Scheduled publisher
├── user/                                  # User entity, UserRepository, UserDetailsService
└── web/dto/                               # request/response DTOs with Bean Validation

services/identity-service/src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_sender_ids.sql
└── V3__create_outbox.sql                  # refresh/reset/lockout live in Redis, not Postgres
```

### Pattern 1: Asymmetric JWT issuance + validation
**What:** identity signs with RSA private key; shared-security verifies with RSA public key.
**When to use:** Always in this architecture (CLAUDE.md JWT pattern).
**Example:**
```java
// Source: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html [CITED]
// identity-service — JwtConfig.java  [ASSUMED detail: exact key-loading wiring]
@Bean
JwtEncoder jwtEncoder(RSAPublicKey pub, RSAPrivateKey priv) {
    JWK jwk = new RSAKey.Builder(pub).privateKey(priv).keyID("identity-1").build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
}
// issue:
JwtClaimsSet claims = JwtClaimsSet.builder()
    .issuer("https://identity.open-desk")
    .subject(userId.toString())
    .issuedAt(now).expiresAt(now.plus(15, ChronoUnit.MINUTES))
    .claim("verification_status", user.getStatus().name())   // D-02 — load-bearing
    .claim("roles", List.of("ROLE_USER"))
    .build();
String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

// shared-security — JwtDecoder bean (validation side)
@Bean
JwtDecoder jwtDecoder(RSAPublicKey pub) { return NimbusJwtDecoder.withPublicKey(pub).build(); }
```
Public key distribution at MVP: inject `spring.security.oauth2.resourceserver.jwt.public-key-location` (Kubernetes Secret/ConfigMap) into every module; identity holds both halves as secrets.

### Pattern 2: Refresh-token rotation in Redis
**What:** opaque random token per device; on refresh, validate→delete old→issue new.
**Keys:** `refresh:{userId}:{deviceId}` → value = hashed token + metadata; TTL 7 days.
- Logout (D-07): `DEL refresh:{userId}:{currentDeviceId}`
- Reset (D-09): `DEL refresh:{userId}:*` (use SCAN, never `KEYS` in prod)
- Rotation (D-08): atomic delete-old + set-new; reuse of a deleted token = reuse attack → revoke all that user's sessions.

### Pattern 3: Async verification on a bounded executor
**What:** `@Async("nidaExecutor")` where `nidaExecutor` is a `ThreadPoolTaskExecutor` with a fixed pool + bounded queue.
**Why not the default:** With `spring.threads.virtual.enabled=true`, the auto `SimpleAsyncTaskExecutor` is *unbounded* (one virtual thread per task). A slow/hung NIDA endpoint would spawn unlimited in-flight calls → the "NIDA thread-pool exhaustion" risk flagged in ROADMAP. A bounded dedicated executor + Resilience4j circuit breaker caps concurrency and fails fast. [CITED: docs.spring.io/spring-boot task-execution]

### Pattern 4: Transactional outbox for the credit grant (IDEN-03)
**What:** in the same `@Transactional` method that flips status→VERIFIED, INSERT an `outbox` row. A separate `@Scheduled` relay polls unsent rows and publishes to RabbitMQ, marking them sent.
**Why:** guarantees the event reflects exactly the committed DB state — no lost/phantom events. [CITED: wimdeblauwe.com transactional-outbox]

### Anti-Patterns to Avoid
- **Shared HMAC secret for JWTs** — every module could forge tokens. Use RSA asymmetric.
- **Synchronous call to wallet to grant credits** — violates CLAUDE.md; use outbox event.
- **Storing refresh tokens as plaintext in Redis** — store a hash; treat like a password.
- **`KEYS refresh:{userId}:*`** in revoke-all — blocks Redis; use `SCAN`.
- **Blocking the registration response on the NIDA call** — breaks IDEN-02 (must return PENDING immediately).
- **Caching NIDA verification results** — CLAUDE.md explicitly forbids; each registration is a live check.
- **`javax.*` / `RestTemplate`** — use `jakarta.*` and `RestClient` (CLAUDE.md).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom salt+hash | `BCryptPasswordEncoder` / `DelegatingPasswordEncoder` | Timing-safe, salted, upgrade path built-in |
| JWT sign/verify | Manual base64+RSA | `NimbusJwtEncoder` / `NimbusJwtDecoder` | Correct JOSE handling, claim validation, exp/nbf checks |
| Retry with backoff | `while` + `Thread.sleep` | `spring-retry` `@Retryable(backoff=@Backoff(...))` | Exponential backoff, max attempts, recover hooks |
| Circuit breaking | Manual failure counters | Resilience4j `@CircuitBreaker` | Half-open probing, sliding-window, metrics |
| Reliable cross-module events | Direct broker send in TX | Transactional outbox + relay | Avoids lost/phantom events on broker/DB failure |
| Account lockout | DB column + manual reset | Redis `INCR` + `EXPIRE` cooldown | Atomic, auto-expiring, no migration churn |
| Token TTL/expiry | Cron sweeping a table | Redis `SETEX` | TTL is native; auto-evicts |
| DTO↔entity mapping | Hand-written copy code | MapStruct | CLAUDE.md standard; compile-time, null-safe |

**Key insight:** Every primitive this phase needs (hashing, signing, retry, circuit-breaking, TTL state) already ships in the locked stack. Custom implementations here are pure risk with no upside.

## Common Pitfalls

### Pitfall 1: `NimbusJwtEncoder` not on identity-service classpath
**What goes wrong:** identity-service depends only on `:libs:shared-security` (which has `oauth2-resource-server`). The *encoder* lives in `spring-security-oauth2-jose`; while transitively present, relying on transitive deps for a core class is fragile.
**How to avoid:** Add `spring-boot-starter-oauth2-resource-server` (or `spring-security-oauth2-jose`) directly to identity-service's build.gradle.kts.
**Warning sign:** `ClassNotFoundException: NimbusJwtEncoder` at startup.

### Pitfall 2: Virtual-thread executor makes NIDA concurrency unbounded
**What goes wrong:** `spring.threads.virtual.enabled=true` + `@Async` (default executor) = unlimited concurrent NIDA calls; a hung NIDA endpoint starves the service.
**How to avoid:** Dedicated bounded `ThreadPoolTaskExecutor` for NIDA + Resilience4j circuit breaker + `readTimeout=15s` (CLAUDE.md).
**Warning sign:** Connection pool/socket exhaustion when NIDA is slow.

### Pitfall 3: verification_status claim goes stale after verification
**What goes wrong:** User verifies, but their existing access token still says PENDING for up to 15 min; features stay walled.
**How to avoid:** Status flip is async and the access token is short-lived (15 min) — acceptable. Document it: the *durable* source of truth is the DB; the claim is a 15-min-max cache. On the next refresh the new token carries VERIFIED. The client should refresh after seeing verification succeed (or poll a `/me` endpoint that reads DB status). Do NOT add a runtime callback to identity (violates D-02).
**Warning sign:** "I verified but still can't send" reports — expected within the access-token TTL window.

### Pitfall 4: Refresh-token reuse not detected
**What goes wrong:** Rotation issues a new token but doesn't invalidate the old; a stolen old token still works.
**How to avoid:** On refresh, delete the old key atomically; if a request presents a token whose key is already gone (and it isn't the current one), treat as reuse → revoke all that user's sessions.
**Warning sign:** Multiple valid refresh tokens per device.

### Pitfall 5: Outbox relay double-publishes
**What goes wrong:** Relay publishes then crashes before marking sent → duplicate event → Phase 3 grants 100 credits.
**How to avoid:** Outbox guarantees at-least-once; the **wallet consumer must be idempotent** (Phase 3 owns this — CLAUDE.md prescribes a `processed_events` table). Identity should put a stable `eventId` (UUID) on each outbox row so the consumer can dedupe.
**Warning sign:** Users with 100 free credits.

### Pitfall 6: Flyway `flyway-database-postgresql` omitted
**What goes wrong:** Flyway 10 split the PG driver; silent degraded behavior. (Already on identity-service build — keep it.)

## Code Examples

### BCrypt delegating encoder
```java
// Source: Spring Security 6.5 reference [CITED]
@Bean PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt default
}
```

### Stateless SecurityFilterChain (issuer side)
```java
// Source: Spring Security 6.5 reference [CITED] — CSRF off for stateless JWT (CLAUDE.md)
@Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/auth/register","/auth/login","/auth/refresh",
                             "/auth/forgot","/auth/reset","/actuator/health/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())); // validates its own issued tokens
    return http.build();
}
```

### Resilience4j + retry on real NIDA call
```java
// Source: resilience4j docs + spring-retry [CITED]
@CircuitBreaker(name = "nida", fallbackMethod = "nidaUnavailable")
@Retryable(retryFor = NidaTransientException.class,
           maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public NidaResult verify(String nin) { /* RestClient call, 5s connect / 15s read */ }
```

### Stub with configurable / magic-NIN outcomes (D-05)
```java
@Profile("stub")
@Service
class StubNidaVerificationService implements NidaVerificationService {
  // suffix convention (discretion): ...0001=REJECT, ...0002=TIMEOUT, ...0003=UNAVAILABLE, else SUCCESS after ~3s
  // also overridable via property: app.nida.stub.default-outcome=SUCCESS|REJECT|TIMEOUT|UNAVAILABLE
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` for NIDA | `RestClient` | Spring 6.1 / Boot 3.2 | CLAUDE.md hard rule — use RestClient |
| `@EnableRetry`-only retry config | `spring.rabbitmq.*.retry.*` props + `@Retryable` | Boot 3.x | spring-retry available without extra enable annotation in Boot 3 |
| `ThreadPoolTaskExecutor` everywhere | virtual threads via `spring.threads.virtual.enabled` | Boot 3.2 + Java 21 | Use VTs broadly BUT bound the NIDA executor explicitly |
| HS256 shared secret | RS256 asymmetric (issuer/validator split) | n/a (design choice) | Enables trust-without-forge across modules |

**Deprecated/outdated:**
- `RestTemplate` for new code, `javax.*` imports, Spring Boot 4.x (all per CLAUDE.md "What NOT to Do").

## Runtime State Inventory

> Greenfield phase (no rename/refactor). Section included only to note the durable-vs-ephemeral state split this phase introduces, since it is load-bearing for sessions.

| Category | Items | Action |
|----------|-------|--------|
| Stored data (Postgres) | `users`, `sender_ids`, `outbox` | Flyway V1–V3 migrations |
| Ephemeral state (Redis) | `refresh:{userId}:{deviceId}`, `reset:{token}`, `lockout:{email}`, `nida:pending:{userId}` | runtime only; TTL-managed; no migration |
| Secrets / env | RSA private key (identity only), RSA public key (all modules), Redis URL, RabbitMQ URI, mail creds | Kubernetes Secrets / Sealed Secrets (INFR-05) |
| Cross-module contract | `verification_status` claim schema + `UserVerified` event schema | shared-security and Phase 3 must agree — document in code |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Public key distributed via config property at MVP (not JWKS endpoint) | Stack/Alternatives | If key rotation is needed at MVP, requires redeploy of all modules; low risk at 1-replica MVP |
| A2 | Refresh/reset/lockout state lives in Redis (not Postgres) | Architecture | Confirmed by CLAUDE.md "Redis for sessions/OTP"; low risk |
| A3 | BCrypt (not Argon2) is sufficient for D-12 | Alternatives | Low — meets min-8+lockout; upgradeable via DelegatingPasswordEncoder |
| A4 | Numeric sender ID = generated shortcode (e.g., 5–6 digit) unique per user | SNDR-01 | Exact format/length unspecified in requirements — needs a planner decision; TCRA shortcode rules unknown |
| A5 | Email provider = `spring-boot-starter-mail` / SMTP for the real impl | Stack | Provider choice was left to researcher (D-13); SMTP is the lowest-friction default, swappable later |
| A6 | Magic-NIN suffix convention (0001=reject etc.) | Code Examples | Convention is discretionary (D-05); planner may choose different suffixes |
| A7 | Exact key wiring (PEM location, keyID) | Pattern 1 | Implementation detail; verified pattern, not exact project config |

## Open Questions

1. **Numeric sender ID format/length (SNDR-01)**
   - Known: must be numeric, assigned at registration/verification, unique.
   - Unclear: length, prefix, whether it must be a real Tanzania shortcode or a platform-internal placeholder until TCRA provisioning.
   - Recommendation: generate a platform-internal unique numeric ID (e.g., zero-padded sequence or random 6-digit checked for uniqueness) at MVP; flag that real shortcode provisioning is a Phase 0/external concern. Planner to confirm format.

2. **Public-key rotation strategy**
   - Known: MVP uses static public key via config.
   - Unclear: how/when keys rotate post-MVP.
   - Recommendation: document JWKS endpoint as the post-MVP rotation path; out of scope for Phase 2.

3. **Email provider concrete choice (D-13)**
   - Recommendation: interface `EmailSender`; stub logs the reset link; real impl uses `JavaMailSender` (SMTP) behind `@Profile("prod")`. Confirm SMTP creds source (Kubernetes Secret).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 | users/sender_ids/outbox | via Docker Compose (INFR-02) | 16 | Testcontainers in tests |
| Redis 7 | sessions/lockout/reset/OTP | via Docker Compose (INFR-02) | 7 | Testcontainers / GenericContainer in tests |
| RabbitMQ 3 | UserVerified event relay | via Docker Compose (INFR-02) | 3.x | Testcontainers; outbox tolerates broker-down (events stay unsent) |
| NIDA API | real verification | NOT available (Phase 0 unconfirmed) | — | **Stub (`@Profile("stub")`) is the MVP path** — fully covers all flows incl. IDEN-08 |
| SMTP / mail | real password-reset email | NOT required at MVP | — | Stub `EmailSender` records link in dev |

**Missing with no fallback:** none. **Missing with fallback:** NIDA (stub), SMTP (stub) — both intentional mock-first.

## Validation Architecture

> nyquist_validation is enabled (no config override found). Test framework is JUnit 5 (Phase 1 Wave-0 baseline) + Spring Boot Test + Testcontainers (Postgres pinned 1.21.2; add Redis + RabbitMQ modules).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + spring-boot-starter-test + Testcontainers 1.21.2 |
| Config file | none yet for identity-service — Wave 0 creates `@SpringBootTest` + `@Testcontainers` base |
| Quick run command | `./gradlew :services:identity-service:test --tests "*UnitTest" --no-daemon` |
| Full suite command | `./gradlew :services:identity-service:test --no-daemon` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IDEN-01 | register persists user PENDING + hashed pwd | integration | `:services:identity-service:test --tests "RegistrationIT"` | ❌ Wave 0 |
| IDEN-02 | register returns PENDING immediately (no block on NIDA) | integration | `--tests "RegistrationIT.returnsImmediately"` | ❌ Wave 0 |
| IDEN-03 | verification writes outbox UserVerified(50) | integration | `--tests "VerificationOutboxIT"` | ❌ Wave 0 |
| IDEN-04 | login email+pwd issues access+refresh | integration | `--tests "LoginIT"` | ❌ Wave 0 |
| IDEN-04 | wrong pwd ×5 → lockout | integration | `--tests "LockoutIT"` | ❌ Wave 0 |
| IDEN-05 | access JWT carries verification_status; decodes with public key | unit | `--tests "JwtIssuerUnitTest"` | ❌ Wave 0 |
| IDEN-05 | refresh rotation invalidates old token | integration | `--tests "RefreshRotationIT"` | ❌ Wave 0 |
| IDEN-06 | logout revokes only current device | integration | `--tests "LogoutIT"` | ❌ Wave 0 |
| IDEN-07 | reset token single-use + TTL; reset revokes all sessions | integration | `--tests "PasswordResetIT"` | ❌ Wave 0 |
| IDEN-08 | NIDA unavailable → stays PENDING → retry job verifies | integration | `--tests "NidaDegradedIT"` (stub outcome=UNAVAILABLE) | ❌ Wave 0 |
| SNDR-01 | numeric sender ID assigned on verification, unique | integration | `--tests "SenderIdIT"` | ❌ Wave 0 |
| D-02 cross-module | shared-security decodes identity-issued token + reads claim | unit (in shared-security) | `:libs:shared-security:test --tests "JwtValidationUnitTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** quick unit run (`*UnitTest`) — sub-30s, no containers.
- **Per wave merge:** full identity-service suite (spins Testcontainers Postgres+Redis+RabbitMQ).
- **Phase gate:** full suite green + `:libs:shared-security:test` green before `/gsd:verify-work`.

### Wave 0 Gaps
- [ ] `IdentityServiceApplication.java` — real `@SpringBootApplication` (replaces placeholder.MainClass)
- [ ] `AbstractIntegrationTest` base with `@ServiceConnection` Postgres + Redis (+ RabbitMQ) containers
- [ ] Catalog entries: `spring-boot-starter-mail`, a Redis Testcontainers module (`com.redis:testcontainers-redis`) or use `GenericContainer`, `testcontainers-rabbitmq` (already in catalog)
- [ ] `application-test.yml` activating `stub` profile + ephemeral RSA test keypair
- [ ] Test RSA keypair fixture shared between identity (sign) and shared-security (verify) tests
- [ ] `JwtIssuerUnitTest`, `JwtValidationUnitTest` (cross-module claim contract — highest priority, load-bearing for D-02)

## Security Domain

> security_enforcement enabled (no override).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | BCrypt password hashing; lockout after ~5 attempts (Redis); min-8 policy (D-12) |
| V3 Session Management | yes | Opaque refresh tokens in Redis; rotation (D-08); revoke-current (D-07); revoke-all on reset (D-09); 15-min access TTL |
| V4 Access Control | yes | `verification_status` + `roles` claims; STATELESS filter chain; permitAll only on auth endpoints |
| V5 Input Validation | yes | Bean Validation (`@Valid`) on all DTOs; NIN/email/phone format checks; UTF-8 Swahili-safe |
| V6 Cryptography | yes | RSA keypair via Nimbus (never hand-rolled); private key in Kubernetes Secret; never logged |
| V7 Error/Logging | yes | Structured JSON logs; never log passwords, tokens, reset links (prod), or RSA private key |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Token forgery by a downstream module | Spoofing | Asymmetric RSA — modules hold public key only, cannot sign |
| Refresh-token theft / replay | Spoofing/Tampering | Rotation + reuse detection → revoke-all |
| Credential stuffing / brute force | Spoofing | Redis lockout counter + Traefik IP rate limit (CLAUDE.md) |
| Password-reset token brute force / reuse | Tampering | High-entropy single-use token, short TTL, deleted on use |
| Double credit grant | Tampering | Outbox eventId + idempotent wallet consumer (Phase 3) |
| NIDA DoS / latency cascade | DoS | Bounded executor + circuit breaker + timeouts (5s/15s) |
| PII (NIN, Swahili names) exposure in logs | Info Disclosure | Never log NIN/tokens; UTF-8 columns; no NIDA result caching |
| Forged "verified" status | Elevation | Status is a signed claim; DB is durable source of truth |

## Sources

### Primary (HIGH confidence)
- Spring Security 6.5 reference — JWT resource server (`NimbusJwtDecoder.withPublicKey` / `withSecretKey` / `withJwkSetUri`): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html [CITED]
- Spring Boot reference — Task Execution & Scheduling (virtual-thread `SimpleAsyncTaskExecutor` is unbounded; `@Async` executor behavior): https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html [CITED]
- Project CLAUDE.md — locked stack, JWT issue/validate pattern, NIDA guidance, Redis-for-sessions, Resilience4j+spring-retry, jakarta/RestClient rules [VERIFIED: ./CLAUDE.md]
- Project version catalog + Phase 1 SUMMARY — available BOM-managed deps and convention plugins [VERIFIED: gradle/libs.versions.toml, 01-01-SUMMARY.md]

### Secondary (MEDIUM confidence)
- NimbusJwtEncoder API (RSAKey/ImmutableJWKSet/JwtClaimsSet usage): https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html
- Transactional outbox with Spring Boot — Wim Deblauwe: https://www.wimdeblauwe.com/blog/2024/06/25/transactional-outbox-pattern-with-spring-boot/

### Tertiary (LOW confidence)
- Spring Security + JWT walkthroughs (Baeldung, dev.to) — used only to cross-check the encoder/claims pattern; superseded by official docs above.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all deps locked/BOM-managed and verified in catalog.
- Architecture (asymmetric JWT, Redis sessions, outbox, async-bounded NIDA): HIGH — matches CLAUDE.md mandates and verified Spring docs.
- Pitfalls: HIGH — derived from verified executor/encoder behavior and CLAUDE.md hard rules.
- Sender-ID format (SNDR-01) & email provider: MEDIUM — needs a planner/business decision (Open Questions 1, 3).

**Research date:** 2026-06-19
**Valid until:** 2026-07-19 (stable, locked stack; revisit if Spring Boot patch or NIDA access changes)
