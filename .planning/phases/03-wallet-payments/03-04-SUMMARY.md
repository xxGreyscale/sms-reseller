---
phase: 03-wallet-payments
plan: "04"
subsystem: wallet-service
tags: [amqp-consumer, idempotency, processed_events, bonus-credits, balance-api, transaction-history, jwt-resource-server, tdd, wlet-01, wlet-02]
dependency_graph:
  requires: [03-02]
  provides: [user-verified-consumer-03, processed-events-guard-03, wallet-read-api-03]
  affects: [03-05, 03-06]
tech_stack:
  added: []
  patterns:
    - "@RabbitListener @QueueBinding passive bind to identity.events TopicExchange — wallet does NOT redeclare identity exchange"
    - "INSERT INTO processed_events ON CONFLICT DO NOTHING — atomic idempotency guard (T-03-08)"
    - "@Transactional consumer: tryInsert + grantBonus commit together or both roll back (T-03-10)"
    - "userId ALWAYS from JWT subject auth.getSubject() — never from request body/path (IDOR, T-03-09, ASVS V4)"
    - "SecurityConfig resource-server only — no PasswordEncoder bean (wallet validates, never issues tokens)"
    - "Jackson2JsonMessageConverter on RabbitTemplate for JSON AMQP payload deserialization"
key_files:
  created:
    - services/wallet-service/src/main/resources/db/migration/V3__create_processed_events.sql
    - services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/ProcessedEvent.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/ProcessedEventRepository.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/UserVerifiedEvent.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/UserVerifiedConsumer.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/config/RabbitMqConfig.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/config/SecurityConfig.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/api/BalanceResponse.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/api/WalletController.java
    - services/wallet-service/src/main/java/com/smsreseller/wallet/transaction/CreditTransactionDto.java
    - services/wallet-service/src/test/java/com/smsreseller/wallet/TestKeys.java
    - services/wallet-service/src/test/java/com/smsreseller/wallet/JwtTestHelper.java
    - services/wallet-service/src/test/java/com/smsreseller/wallet/WalletTestConfiguration.java
  modified:
    - services/wallet-service/src/test/java/com/smsreseller/wallet/UserVerifiedConsumerIT.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/smsreseller/wallet/BalanceIT.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/smsreseller/wallet/TransactionHistoryIT.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/smsreseller/wallet/AbstractWalletIntegrationTest.java (@Import WalletTestConfiguration)
decisions:
  - "UserVerifiedEvent is a local record in wallet.consumer — no identity-service import; contract mirrored from 02-06-SUMMARY.md to respect service ownership boundary (CLAUDE.md)"
  - "RabbitMqConfig does NOT declare identity.events — @QueueBinding creates the binding passively to an already-declared exchange; declaring it again in wallet-service would be a hidden coupling"
  - "WalletTestConfiguration @TestConfiguration provides JwtTestHelper bean; imported via @Import on AbstractWalletIntegrationTest so all sub-ITs get it automatically without per-class @Import"
  - "ProcessedEventRepository.tryInsert uses nativeQuery INSERT ON CONFLICT DO NOTHING returning int rows — wraps to boolean default method; this avoids a SELECT-then-INSERT race that a findById check would create"
metrics:
  duration: "~22 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  tasks_total: 2
  files_created: 13
  files_modified: 4
---

# Phase 03 Plan 04: Wallet Inbound Consumer + Read API Summary

**One-liner:** Idempotent UserVerified AMQP consumer granting 50-credit BONUS lot (30-day expiry, D-03) via processed_events ON CONFLICT guard, plus JWT-scoped GET /balance and GET /transactions endpoints proven by 7 GREEN integration tests.

## What Was Built

### Task 1: UserVerified consumer + idempotency + processed_events (TDD RED→GREEN)

**RED:** `UserVerifiedConsumerIT` converted from `Assumptions.abort` placeholder to a real Spring container test that:
1. Publishes a JSON UserVerified payload to the `identity.events` exchange with routing key `identity.UserVerified`
2. Awaits (Awaitility) until the consumer processes the message and `BalanceService.getBalance()` returns 50
3. Asserts exactly one BONUS lot exists with `granted=50` and `expiresAt ≈ now+30d`
4. Publishes the same `eventId` again (duplicate delivery)
5. Asserts lot count is still 1 and balance is still 50 (idempotency)

RED gate confirmed: `NoSuchBeanDefinitionException` (UserVerifiedConsumer, SecurityConfig not yet wired) + compile-time errors on missing classes.

**GREEN implementation:**

1. **V3__create_processed_events.sql** — `processed_events(event_id VARCHAR(128) PRIMARY KEY, processed_at TIMESTAMPTZ DEFAULT now())`. PRIMARY KEY on `event_id` enables the `ON CONFLICT DO NOTHING` idempotency pattern atomically.

2. **ProcessedEvent** `@Entity` — immutable after insert (no `@Setter`). Constructor sets `processedAt = Instant.now()`.

3. **ProcessedEventRepository** — native query `INSERT INTO processed_events ... ON CONFLICT DO NOTHING` returning `int` rows affected. `default boolean tryInsert(String eventId)` wraps this to a boolean for clean caller code. No SELECT-then-INSERT race (atomically safe).

4. **UserVerifiedEvent** `record(String eventId, UUID userId, int freeCredits)` — local mirror of identity-service's event shape. No `com.smsreseller.identity` import (service boundary respected per CLAUDE.md).

5. **UserVerifiedConsumer** `@Component @RabbitListener` — binds to queue `wallet.identity.UserVerified` (durable) on exchange `identity.events` (TOPIC, durable) with key `identity.UserVerified`. `@Transactional onUserVerified`: guard via `tryInsert(eventId)` → if duplicate returns early; else `lotService.grantBonus(userId, freeCredits, now+30d)`.

6. **RabbitMqConfig** — declares `wallet.events` TopicExchange (own exchange only), `Jackson2JsonMessageConverter`, `RabbitTemplate` with JSON converter. Identity exchange (`identity.events`) is NOT declared here — `@QueueBinding` creates the binding passively.

### Task 2: Wallet read API + SecurityConfig (TDD RED→GREEN)

**RED:** `BalanceIT` and `TransactionHistoryIT` converted from placeholders to real Spring MockMvc-style tests (using `TestRestTemplate`) asserting:
- WLET-01: `GET /api/v1/wallet/balance` returns `{"availableCredits": 50}` for seeded lot
- WLET-01 IDOR: another user's lots are NOT counted (cross-user isolation)
- WLET-01 auth: no JWT → 401
- WLET-02: `GET /api/v1/wallet/transactions` returns `content[].txnType=GRANT`, most-recent first
- WLET-02 IDOR: another user's transactions excluded from page
- WLET-02 auth: no JWT → 401

RED gate: `NoSuchBeanDefinitionException` for `JwtTestHelper` bean.

**GREEN implementation:**

7. **SecurityConfig** — `@EnableWebSecurity`, CSRF disabled, `STATELESS`, `permitAll(/actuator/health/**, /error)`, `anyRequest().authenticated()`, `oauth2ResourceServer.jwt`. No `PasswordEncoder` bean — resource-server only (cannot be mistaken for an auth-issuing service).

8. **BalanceResponse** `record(int availableCredits)` — JSON serializes to `{"availableCredits": N}`.

9. **CreditTransactionDto** `record(id, lotId, txnType, delta, referenceId, createdAt)` + `from(CreditTransaction)` factory — read-only API projection, never exposes entity directly.

10. **WalletController** `@RestController /api/v1/wallet`:
    - `GET /balance` — `UUID userId = UUID.fromString(auth.getSubject())` → `BalanceResponse(balanceService.getBalance(userId))`
    - `GET /transactions` — same `userId` extraction → `Page<CreditTransactionDto>` from `creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)`
    - userId NEVER from request body/path/query — IDOR-safe

11. **TestKeys** + **JwtTestHelper** + **WalletTestConfiguration** — RSA key loader, JWT minter, and `@TestConfiguration` wiring. `AbstractWalletIntegrationTest` imports `WalletTestConfiguration` so all sub-ITs get `JwtTestHelper @Autowired` automatically.

## Downstream API Surface (for plans 03-05, 03-06)

| Endpoint | Contract | Security |
|----------|----------|----------|
| `GET /api/v1/wallet/balance` | `BalanceResponse { availableCredits: int }` | JWT required; userId from sub |
| `GET /api/v1/wallet/transactions` | `Page<CreditTransactionDto>` newest-first | JWT required; userId from sub |

| Component | Used by |
|-----------|---------|
| `ProcessedEventRepository.tryInsert` | Any future AMQP consumer needing idempotency |
| `processed_events` table | 03-05 payment callback consumer, 03-06 refund consumer |

## Deviations from Plan

None — plan executed exactly as written.

## TDD Gate Compliance

- [x] `test(03-04):` RED commit exists: `db9ffc6`
- [x] `feat(03-04):` GREEN commit exists after RED: `f870c5a`
- [x] REFACTOR not needed — implementation was clean on first pass

## Known Stubs

None — all endpoints return real data from the database. No hardcoded or placeholder values.

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-03-08 (duplicate UserVerified double-grant) | Mitigated | `processed_events` ON CONFLICT DO NOTHING; `UserVerifiedConsumerIT` asserts single grant on re-delivery |
| T-03-09 (IDOR balance/history) | Mitigated | `WalletController` extracts userId from `auth.getSubject()` only; `BalanceIT` + `TransactionHistoryIT` assert cross-user exclusion |
| T-03-10 (lost bonus on consumer crash) | Mitigated | `@Transactional` on `onUserVerified` — processed_events INSERT + grantBonus commit together or both roll back |

## Threat Flags

None — no new network endpoints beyond what was planned. `GET /balance` and `GET /transactions` were in scope per the plan's threat model.

## Self-Check: PASSED

- [x] `UserVerifiedConsumer` uses `@RabbitListener` with exchange `identity.events` key `identity.UserVerified`
- [x] `RabbitMqConfig.java` has zero references to `identity.events` (only declares `wallet.events`)
- [x] `V3__create_processed_events.sql` contains `processed_events` with `PRIMARY KEY (event_id)`
- [x] `SecurityConfig.java` has zero `PasswordEncoder` references
- [x] `WalletController.java` extracts userId via `auth.getSubject()` — no userId in path/body/query
- [x] No `javax.*` imports in any created source file
- [x] No `com.smsreseller.identity` imports in any wallet source file
- [x] `./gradlew :services:wallet-service:test` BUILD SUCCESSFUL (all 7 new tests GREEN, prior tests unaffected)
- [x] RED commit `db9ffc6` exists before GREEN commit `f870c5a`
