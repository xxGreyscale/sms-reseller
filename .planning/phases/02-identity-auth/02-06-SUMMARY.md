---
phase: 02-identity-auth
plan: "06"
subsystem: identity-service
tags: [transactional-outbox, sender-id, rabbitmq, tdd, wave-3, iden-03, sndr-01]
dependency_graph:
  requires: [02-03]
  provides: [verification-finalizer-impl, sender-id, outbox-relay, rabbitmq-topology]
  affects: [phase-3-wallet]
tech_stack:
  added:
    - RabbitMQContainer (testcontainers-rabbitmq — already in build.gradle.kts; now wired in AbstractIntegrationTest)
    - Jackson2JsonMessageConverter (spring-boot-starter-amqp transitive)
  patterns:
    - Transactional outbox (Pattern 4): status + sender_id + outbox written atomically in one TX
    - "@Scheduled fixedDelay relay: bounded batch, at-least-once publish, mark-sent per row"
    - "Topic exchange identity.events with routing key identity.<EventType>"
    - "Idempotent finalize: already-VERIFIED guard returns early — no double sender ID, no double outbox row (T-02-03)"
    - "SenderIdService.assign() collision-retry loop with existsBySenderId check before INSERT"
key_files:
  created:
    - services/identity-service/src/main/java/com/opendesk/identity/verification/VerificationFinalizerImpl.java
    - services/identity-service/src/main/java/com/opendesk/identity/sender/SenderId.java
    - services/identity-service/src/main/java/com/opendesk/identity/sender/SenderIdRepository.java
    - services/identity-service/src/main/java/com/opendesk/identity/sender/SenderIdService.java
    - services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxEntry.java
    - services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxRepository.java
    - services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxRelay.java
    - services/identity-service/src/main/java/com/opendesk/identity/outbox/UserVerifiedEvent.java
    - services/identity-service/src/main/java/com/opendesk/identity/config/RabbitMqConfig.java
    - services/identity-service/src/main/resources/db/migration/V2__create_sender_ids.sql
    - services/identity-service/src/main/resources/db/migration/V3__create_outbox.sql
  modified:
    - services/identity-service/src/test/java/com/opendesk/identity/SenderIdIT.java (converted stub → real container IT)
    - services/identity-service/src/test/java/com/opendesk/identity/VerificationOutboxIT.java (converted stub → real container IT)
    - services/identity-service/src/test/java/com/opendesk/identity/AbstractIntegrationTest.java (added RabbitMQContainer)
decisions:
  - "VerificationFinalizerImpl bean name 'transactionalVerificationFinalizer' displaces NoOpVerificationFinalizer (@ConditionalOnMissingBean(name=...)) without removing the placeholder"
  - "SenderIdService.assign() uses @Transactional(REQUIRED) — joins outer TX from VerificationFinalizerImpl; also callable standalone in tests"
  - "OutboxRelay.markSent() is a separate @Transactional call per row — publish failure on one row does not block subsequent rows in the batch"
  - "identity.events TopicExchange with routing key prefix identity. — Phase 3 wallet binds queue to identity.UserVerified"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-19"
  tasks_completed: 2
  tasks_total: 2
  files_created: 11
  files_modified: 3
---

# Phase 02 Plan 06: Verification Finalize Transaction + Outbox Relay Summary

**One-liner:** VerificationFinalizerImpl atomically flips status→VERIFIED + assigns a 6-digit numeric sender ID (SNDR-01) + writes a UserVerified(50 credits) transactional outbox row (IDEN-03) in one Postgres TX; @Scheduled OutboxRelay publishes unsent rows to identity.events topic exchange at-least-once.

## What Was Built

Wave 3 production code completing the verification finalization boundary:

1. **Task 1 — SenderId + Outbox entities, repositories, V2/V3 migrations, UserVerifiedEvent:**
   - `SenderId` @Entity: UUID id, user_id (unique FK), sender_id varchar(10) (unique), created_at
   - `SenderIdRepository`: `findByUserId`, `existsBySenderId`
   - `SenderIdService.assign(userId)`: idempotent — returns existing shortcode or generates a unique 6-digit zero-padded numeric (collision-retry loop, MAX_RETRIES=10); placeholder until TCRA provisioning (SNDR-02, Phase 4)
   - `OutboxEntry` @Entity: id, event_id (unique), aggregate_type, aggregate_id, event_type, payload TEXT, sent boolean (default false), created_at, sent_at
   - `OutboxRepository`: `findBySentFalseOrderByCreatedAtAsc(Pageable)` (relay, bounded) + `findBySentFalse()` (tests only)
   - `UserVerifiedEvent` record: eventId, userId, freeCredits=50; `DEFAULT_FREE_CREDITS=50` constant
   - `V2__create_sender_ids.sql`: sender_ids table with `uq_sender_ids_sender_id` unique constraint and `user_id` FK
   - `V3__create_outbox.sql`: outbox table with `uq_outbox_event_id` unique constraint + partial index on `sent=FALSE`
   - `SenderIdIT`: 2/2 GREEN — 6-digit numeric shortcode assigned; re-assign returns same shortcode (idempotent)

2. **Task 2 — VerificationFinalizerImpl (single TX) + OutboxRelay (RabbitMQ):**
   - `VerificationFinalizerImpl @Service("transactionalVerificationFinalizer")`: implements VerificationFinalizer seam (02-03); `finalizeVerification(userId)` is `@Transactional` — loads user, idempotent guard if already VERIFIED, sets status=VERIFIED, calls `SenderIdService.assign()`, INSERTs OutboxEntry{eventType="UserVerified", payload=JSON{eventId,userId,freeCredits:50}}
   - `OutboxRelay @Component @Scheduled(fixedDelay=5000)`: fetches `findBySentFalseOrderByCreatedAtAsc(PageRequest.of(0, 50))`, publishes each to `identity.events` exchange with routing key `identity.<eventType>`, calls `markSent()` in separate TX per row; publish failure logs warning and continues (at-least-once, retry next run)
   - `RabbitMqConfig`: `TopicExchange("identity.events", durable=true)`, `Jackson2JsonMessageConverter`, `RabbitTemplate` with JSON converter
   - `AbstractIntegrationTest`: added `RabbitMQContainer("rabbitmq:3-management")` with `@DynamicPropertySource` wiring host/port/credentials
   - `VerificationOutboxIT`: 2/2 GREEN — outbox row written (event_type=UserVerified, credits=50) in same TX as VERIFIED flip; re-finalize produces no second row

## TDD Gate Compliance

- Task 1 RED: commit `72962bd` (SenderIdIT + VerificationOutboxIT — both failing) ✓
- Task 1 GREEN: commit `f4f481b` (SenderId + Outbox entities + migrations) ✓
- Task 2 GREEN: commit `3d64670` (VerificationFinalizerImpl + OutboxRelay + RabbitMqConfig) ✓

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SenderIdService.assign() used Propagation.MANDATORY — failed in standalone test**
- **Found during:** Task 1 RED run — `SenderIdIT` calls `senderIdService.assign()` directly without an outer TX. `MANDATORY` propagation requires an active TX and threw `IllegalTransactionStateException`
- **Fix:** Changed `@Transactional(propagation = Propagation.MANDATORY)` to `@Transactional` (REQUIRED). `assign()` joins the outer TX from `VerificationFinalizerImpl` when called in that context, or starts its own TX when called standalone. Semantically equivalent for the TX-safety requirement.
- **Files modified:** `SenderIdService.java`
- **Commit:** `f4f481b`

**2. [Rule 2 - Missing critical] RabbitMQ Testcontainer not in AbstractIntegrationTest**
- **Found during:** Task 2 — Spring context load in integration tests would fail without a reachable RabbitMQ broker (AMQP auto-configure requires broker connectivity). `testcontainers-rabbitmq` was already on the classpath (added in 02-01) but never wired.
- **Fix:** Added `RabbitMQContainer("rabbitmq:3-management")` to `AbstractIntegrationTest` static initializer block with `@DynamicPropertySource` setting host/port/username/password.
- **Files modified:** `AbstractIntegrationTest.java`
- **Commit:** `3d64670`

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-02-03 (double credit grant) | Mitigated — idempotent guard in finalizeVerification; NoOpVerificationFinalizer displaced by transactionalVerificationFinalizer bean name |
| T-02-OB (lost/phantom credit event) | Mitigated — transactional outbox: outbox INSERT is in same TX as status flip; if TX rolls back, no event |
| T-02-SNDR (duplicate sender ID) | Mitigated — unique constraint on sender_id + collision-retry in SenderIdService.assign(); assign() is idempotent per userId |

## Known Stubs

| File | Stub detail | Resolved in plan |
|------|-------------|-----------------|
| `SenderIdService.java` | 6-digit numeric shortcode is a platform-internal placeholder | Phase 4, SNDR-02 (TCRA-provisioned alphanumeric ID) |
| `NoOpVerificationFinalizer.java` | Still present in codebase; displaced by bean name but not deleted | Can be removed in a cleanup plan; harmless |

## Threat Flags

None — no new network endpoints or auth paths beyond what was planned. The RabbitMQ topic exchange `identity.events` was already in the plan scope as the Phase 3 boundary.

## Self-Check: PASSED

- [x] `VerificationFinalizerImpl` annotated `@Transactional` and implements `VerificationFinalizer` — confirmed
- [x] `VerificationFinalizerImpl` has `@Service("transactionalVerificationFinalizer")` — displaces NoOp — confirmed
- [x] `OutboxRelay` annotated `@Scheduled` — confirmed
- [x] `OutboxRelay.relay()` uses `RabbitTemplate` and calls `markSent()` — confirmed
- [x] No `javax.` imports in any created source file — confirmed
- [x] No wallet/credit module import in any source file — confirmed (`grep wallet` finds nothing)
- [x] V2 contains "create table sender_ids" with unique constraint — confirmed
- [x] V3 contains "create table outbox" with unique event_id — confirmed
- [x] `SenderIdIT` 2/2 GREEN
- [x] `VerificationOutboxIT` 2/2 GREEN
- [x] Full suite `./gradlew :services:identity-service:test` — BUILD SUCCESSFUL
- [x] Commits: 72962bd (RED), f4f481b (Task 1 GREEN), 3d64670 (Task 2 GREEN)
