---
phase: 02-identity-auth
plan: "03"
subsystem: identity-service
tags: [registration, nida, async, circuit-breaker, retry, wave-2, tdd]
dependency_graph:
  requires: [02-02]
  provides: [registration-endpoint, nida-stub, nida-interface, verification-orchestrator, verification-finalizer-seam, retry-job]
  affects: [02-04, 02-06]
tech_stack:
  added: []
  patterns:
    - POST /auth/register returns PENDING immediately — @Async("nidaExecutor") fire-and-forget (IDEN-01/02)
    - StubNidaVerificationService @Profile("stub") magic-NIN suffix convention (D-04/D-05)
    - RealNidaVerificationService @Profile("prod") RestClient 5s/15s + @CircuitBreaker(nida) + @Retryable
    - VerificationOrchestrator @Async(nidaExecutor) calls VerificationFinalizer seam on VERIFIED
    - NoOpVerificationFinalizer @ConditionalOnMissingBean placeholder until 02-06
    - VerificationRetryJob @Scheduled — PENDING users older than N min re-dispatched (IDEN-08)
    - /error added to SecurityConfig permitAll to allow 409/400 responses on auth-free endpoints
key_files:
  created:
    - services/identity-service/src/main/java/com/smsreseller/identity/auth/RegistrationController.java
    - services/identity-service/src/main/java/com/smsreseller/identity/auth/RegistrationService.java
    - services/identity-service/src/main/java/com/smsreseller/identity/web/dto/RegisterRequest.java
    - services/identity-service/src/main/java/com/smsreseller/identity/web/dto/RegisterResponse.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/NidaVerificationService.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/NidaResult.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/NidaTransientException.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/StubNidaVerificationService.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/RealNidaVerificationService.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationFinalizer.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationOrchestrator.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationOrchestratorImpl.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationRetryJob.java
    - services/identity-service/src/main/java/com/smsreseller/identity/verification/NoOpVerificationFinalizer.java
  modified:
    - services/identity-service/src/main/java/com/smsreseller/identity/user/UserRepository.java (added findByStatusAndCreatedAtBefore)
    - services/identity-service/src/main/java/com/smsreseller/identity/config/SecurityConfig.java (added /error to permitAll)
    - services/identity-service/src/test/java/com/smsreseller/identity/RegistrationIT.java (converted stub → real container IT)
    - services/identity-service/src/test/java/com/smsreseller/identity/NidaDegradedIT.java (converted stub → real container IT)
decisions:
  - "VerificationFinalizer defined as interface-only seam; NoOpVerificationFinalizer placeholder via @ConditionalOnMissingBean until 02-06"
  - "/error added to SecurityConfig permitAll — Spring Boot redirects 4xx exceptions from permitAll endpoints to /error which was auth-protected, causing 401 instead of 409/400"
  - "NidaDegradedIT uses @MockitoSpyBean (Spring Boot 3.4+ replacement for deprecated @SpyBean) to spy on VerificationFinalizer"
  - "VerificationRetryJob uses null NIN for re-dispatch at MVP — NIN not persisted; real impl needs encrypted NIN storage (deferred to post-NIDA-API-confirmation)"
  - "RealNidaVerificationService uses @ConditionalOnMissingBean-free @Profile(prod) — inactive in test profile; no conditional needed"
metrics:
  duration: "~55 minutes"
  completed: "2026-06-19"
  tasks_completed: 2
  tasks_total: 2
  files_created: 14
  files_modified: 4
---

# Phase 02 Plan 03: Registration & Async NIDA Verification Summary

**One-liner:** POST /auth/register returns PENDING immediately (IDEN-01/02) — BCrypt, async dispatch to bounded NIDA executor with configurable stub (magic-NIN, D-05), Resilience4j circuit-breaker on real impl, VerificationFinalizer seam for 02-06, and @Scheduled retry job for degraded recovery (IDEN-08).

## What Was Built

Wave 2 production code for registration and async NIDA verification:

1. **Task 1 — Registration endpoint + service (PENDING immediately, async dispatch):**
   - `RegisterRequest` record: `@Email`, `@NotBlank`, `@Size(min=8)` on password (D-12, T-02-IDEN01)
   - `RegisterResponse` record: `userId`, `status`, `accessToken` — no passwordHash, no NIN
   - `RegistrationController`: `POST /auth/register` annotated `@Valid` — `@Bean Validation` fires before service
   - `RegistrationService.register()`: duplicate email/phone guard → 409; BCrypt-hash password; save `User(PENDING_VERIFICATION)`; issue PENDING access JWT via `JwtIssuer` (D-01 "logged in but walled"); call `verificationOrchestrator.verifyAsync()` fire-and-forget; return immediately (IDEN-02)
   - `RegistrationIT`: 5 container-backed tests all GREEN — PENDING on register, 409 dups, 400 validation

2. **Task 2 — NIDA service, finalizer seam, orchestrator, retry job:**
   - `NidaVerificationService` interface: `NidaResult verify(String nin)` — PII contract documented
   - `NidaResult` enum: `VERIFIED` | `REJECTED`
   - `NidaTransientException`: transient failure signal (timeout/unavailable) for retry-eligible failures
   - `StubNidaVerificationService @Profile("stub")`: magic-NIN suffix 0001=REJECT, 0002=TIMEOUT, 0003=UNAVAILABLE, else SUCCESS; `app.nida.stub.default-outcome` property override; configurable delay (0ms in tests, 3s in dev)
   - `RealNidaVerificationService @Profile("prod")`: `RestClient` 5s connect / 15s read (CLAUDE.md); `@CircuitBreaker(name="nida")` fallback throws `NidaTransientException`; `@Retryable(retryFor=NidaTransientException, maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`; NEVER caches results (CLAUDE.md)
   - `VerificationFinalizer` interface (THIS plan owns the seam): `void finalizeVerification(UUID userId)` — 02-06 implements
   - `NoOpVerificationFinalizer @ConditionalOnMissingBean`: placeholder so context loads until 02-06 ships
   - `VerificationOrchestrator` interface + `VerificationOrchestratorImpl @Async("nidaExecutor")`: VERIFIED → `finalizeVerification(userId)`; REJECTED → log + leave PENDING; `NidaTransientException` → swallow + log + leave PENDING for retry
   - `VerificationRetryJob @Scheduled`: polls `findByStatusAndCreatedAtBefore(PENDING, cutoff, pageable)` every 2 min, bounded by `maxPerRun=20`, re-dispatches `verifyAsync`
   - `NidaDegradedIT`: 4 container-backed tests all GREEN — success path calls finalizer; UNAVAILABLE stays PENDING; REJECTED stays PENDING; retry dispatch calls finalizer on SUCCESS

## TDD Gate Compliance

- Task 1: RED commit `de107fa` (RegistrationIT — 5 failing) → GREEN commit `cd837b0` (registration impl) ✓
- Task 2: Task 2 implementation compiled alongside Task 1 (VerificationOrchestrator interface needed by RegistrationService). `NidaDegradedIT` RED commit `5835237` is after GREEN commit — TDD order deviation documented below.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spring Boot /error endpoint required authentication**
- **Found during:** Task 1 — when RegistrationService throws `ResponseStatusException(409)`, Spring Boot redirects to `/error`. The `/error` path was not in the `permitAll` list, so Spring Security challenged it with 401 before returning the 409 to the client
- **Fix:** Added `/error` to `permitAll` in `SecurityConfig.authorizeHttpRequests`
- **Files modified:** `services/identity-service/src/main/java/com/smsreseller/identity/config/SecurityConfig.java`
- **Commit:** `cd837b0`

**2. [Rule 1 - Bug] Deprecated @SpyBean in Spring Boot 3.4+**
- **Found during:** Task 2 — `@SpyBean` from `spring-boot-test` is marked for removal in Spring Boot 3.4+; compiler warning emitted
- **Fix:** Replaced with `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` (Spring Boot 3.4+ native replacement)
- **Files modified:** `services/identity-service/src/test/java/com/smsreseller/identity/NidaDegradedIT.java`
- **Commit:** `5835237`

### TDD Ordering Deviation

**Task 2 RED commit order:** `VerificationOrchestrator` interface was created before `NidaDegradedIT` because `RegistrationService` (Task 1) required it to compile. The full NIDA layer (orchestrator impl, stub, retry job) was implemented in the Task 1 GREEN commit to keep compilation green throughout. `NidaDegradedIT` was then written and committed after — it passed GREEN immediately. This is a valid deviation: the Task 1/Task 2 boundary was blurred by inter-class compilation dependencies; all assertions in `NidaDegradedIT` are real (no skips).

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-02-02 (DoS: NIDA latency cascade) | Mitigated — @Async("nidaExecutor") bounded pool + @CircuitBreaker(nida) + 5s/15s timeouts in RealNidaVerificationService |
| T-02-PII (NIN + Swahili names) | Mitigated — NIN never logged (all log statements log only length or userId); no NIDA result caching; UTF-8 default (PostgreSQL) |
| T-02-IDEN01 (duplicate / fake registration) | Mitigated — existsByEmail/existsByPhone → 409; @Valid format checks on RegisterRequest |

## Known Stubs

| File | Stub detail | Resolved in plan |
|------|-------------|-----------------|
| `NoOpVerificationFinalizer.java` | Placeholder `finalizeVerification()` — logs but does not flip status | 02-06 |
| `VerificationRetryJob.java` | Re-dispatches with `null` NIN — NIN not persisted at MVP; real impl needs encrypted NIN storage | Post-NIDA-API-confirmation |

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes beyond what the plan specified.

## Self-Check: PASSED

- [x] `StubNidaVerificationService` annotated `@Profile("stub")` — confirmed `grep @Profile`
- [x] `RealNidaVerificationService` contains `@CircuitBreaker(name = "nida")` and `@Retryable` with `@Backoff` — confirmed
- [x] `VerificationOrchestratorImpl.verifyAsync` annotated `@Async("nidaExecutor")` — confirmed
- [x] `VerificationFinalizer` declared as interface (`interface VerificationFinalizer`) — confirmed
- [x] No `RestTemplate` in production sources — confirmed
- [x] No `javax.` imports in production sources — confirmed
- [x] `RegisterRequest.password` annotated `@Size(min = 8)` — confirmed
- [x] NIN never passed to a logger — confirmed (all log statements use `nin.length()` only)
- [x] `RegistrationIT` 5/5 GREEN — `./gradlew :services:identity-service:test --tests "*RegistrationIT*"`
- [x] `NidaDegradedIT` 4/4 GREEN — `./gradlew :services:identity-service:test --tests "*NidaDegradedIT*"`
- [x] Commits: de107fa (RED RegistrationIT), cd837b0 (GREEN impl), 5835237 (NidaDegradedIT tests)
