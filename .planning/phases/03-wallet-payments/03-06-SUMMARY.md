---
phase: 03-wallet-payments
plan: "06"
subsystem: wallet-service, payment-service
tags: [payment-confirmed-consumer, idempotent-refund, expiry-sweep, low-credit-alert, expiry-warning, reconciliation, azampay-prod-gateway, tdd, wave-3]
dependency_graph:
  requires: [03-02, 03-04, 03-05]
  provides: [payment-confirmed-consumer-03, idempotent-refund-03, expiry-sweep-03, alert-jobs-03, reconciliation-job-03, azampay-prod-gateway-03]
  affects: [phase-4]
tech_stack:
  added: []
  patterns:
    - "PaymentConfirmedConsumer @RabbitListener binding to payment.events (payment-service owns exchange; wallet binds passively)"
    - "Idempotent refund via processed_events 'refund:'+idempotencyKey (same guard as AMQP consumer)"
    - "LowCreditAlertJob + ExpiryWarningJob + ExpirySweepJob follow VerificationRetryJob pattern (bounded, per-item try/catch)"
    - "ReconciliationJob.reconcile(Instant cutoff) testable method — @Scheduled delegates (mirrors PaymentTimeoutJob.sweepExpiredPayments)"
    - "AzampayPaymentGateway @Profile('prod') + @CircuitBreaker(name='azampay') + @Retry(name='azampay') (mirrors RealNidaVerificationService)"
    - "Alert dedup: per-user/per-lot processed_events key — permanent MVP dedup (T-03-20 accept)"
key_files:
  created:
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/PaymentConfirmedConsumer.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/PaymentConfirmedEvent.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/refund/RefundService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/refund/RefundController.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/refund/RefundRequest.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/outbox/OutboxEntry.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/outbox/OutboxRepository.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/outbox/OutboxRelay.java
    - services/wallet-service/src/main/resources/db/migration/V4__create_outbox.sql
    - services/wallet-service/src/main/java/com/opendesk/wallet/sweep/LowCreditAlertJob.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/sweep/ExpiryWarningJob.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/sweep/ExpirySweepJob.java
    - services/payment-service/src/main/java/com/opendesk/payment/reconciliation/ReconciliationJob.java
    - services/payment-service/src/main/java/com/opendesk/payment/gateway/AzampayPaymentGateway.java
    - services/payment-service/src/main/java/com/opendesk/payment/gateway/AzampayTokenProvider.java
    - services/payment-service/src/main/java/com/opendesk/payment/gateway/AzampayTransientException.java
    - services/payment-service/src/main/java/com/opendesk/payment/config/Resilience4jConfig.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/RefundIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/LowCreditAlertIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/ExpiryWarningIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/ReconciliationIT.java
  modified:
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/CreditLotRepository.java (findExpiringBefore, findExpiredBefore, findUserIdsWithBalanceBelow)
    - services/wallet-service/src/main/java/com/opendesk/wallet/config/RabbitMqConfig.java (EXCHANGE + ROUTING_KEY_PREFIX constants)
    - services/wallet-service/src/main/resources/application.yml (alert/cron config keys)
    - services/payment-service/src/main/resources/application.yml (Azampay credentials, resilience4j config, reconciliation.max-per-run)
    - services/payment-service/src/test/java/com/opendesk/payment/ReconciliationIT.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/opendesk/wallet/ExpiryWarningIT.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/opendesk/wallet/LowCreditAlertIT.java (placeholder → real assertions)
decisions:
  - "PaymentConfirmedConsumer binds passively to payment.events — wallet-service does NOT redeclare the payment exchange (mirrors UserVerifiedConsumer/identity.events pattern from 03-04)"
  - "RefundService uses processed_events with 'refund:'+idempotencyKey prefix — reuses the same guard table as AMQP consumers; no separate idempotency table needed"
  - "LowCreditAlertJob dedup: permanent per-user processed_events key (MVP-simple). T-03-20 accepts residual duplication — the 'one alert per cycle' guarantee is best-effort at MVP"
  - "ExpiryWarningJob dedup: permanent per-lot processed_events key. Once a lot is warned, it will not be re-warned (intended — expiry date doesn't change)"
  - "ReconciliationJob.reconcile(Instant cutoff) exposed as testable method — @Scheduled delegates to it with now()-5min. Mirrors PaymentTimeoutJob.sweepExpiredPayments pattern from 03-05"
  - "AzampayPaymentGateway @Profile('prod'): compile-verified but never instantiated in tests (stub profile active). grep RestTemplate = 0 in code (1 count is in javadoc comment)"
  - "Resilience4jConfig is an empty @Configuration — Spring Boot auto-config reads from application.yml. No programmatic @Bean overrides needed at MVP"
metrics:
  duration: "~75 minutes"
  completed: "2026-06-21"
  tasks_completed: 2
  tasks_total: 2
  files_created: 21
  files_modified: 7
---

# Phase 03 Plan 06: PaymentConfirmed Consumer + Refund + Alert/Sweep Jobs + Reconciliation Summary

**One-liner:** Completes Phase 3 — PaymentConfirmed AMQP consumer grants PURCHASED credits exactly once (idempotent, 12-month expiry), idempotent ledger refund ready for Phase 4, low-credit/expiry-warning outbox events, expiry sweep, and Azampay reconciliation job rescuing late-confirmed payments behind a prod gateway with circuit breaker.

## What Was Built

### Task 1 — PaymentConfirmed consumer + idempotent refund + wallet outbox (TDD RED→GREEN)

**RED commit:** `966c27a` — RefundIT failing (RefundService/RefundRequest packages missing)
**GREEN commit:** `60b3f29` — implementation green

**Wallet Outbox Infrastructure (new — plan 03-04 only added processed_events):**
- `OutboxEntry` / `OutboxRepository` / `OutboxRelay` — verbatim copy from identity-service, package changed to `com.opendesk.wallet.outbox`, exchange constant → `RabbitMqConfig.EXCHANGE` ("wallet.events"). OutboxRelay publishes to `wallet.events` TopicExchange.
- `V4__create_outbox.sql` — copy of identity V3 (`CONSTRAINT uq_outbox_event_id UNIQUE(event_id)`, unsent partial index).
- `RabbitMqConfig` — added `EXCHANGE = "wallet.events"` and `ROUTING_KEY_PREFIX = "wallet."` constants.

**PaymentConfirmedConsumer:**
- `@RabbitListener` binding: queue `wallet.payment.PaymentConfirmed` (durable) on exchange `payment.events` (TOPIC, durable), key `payment.PaymentConfirmed`.
- `@Transactional onPaymentConfirmed`: `processedEventRepository.tryInsert(eventId)` → if false return (duplicate); else `lotService.grantPurchased(userId, smsCount, paymentId)` (12-month expiry, D-03).
- `PaymentConfirmedEvent` record — local mirror (`com.opendesk.wallet.consumer`); NO import from `com.opendesk.payment` (service boundary enforced).

**RefundService / RefundController / RefundRequest:**
- `RefundService.refund(userId, credits, referenceId, idempotencyKey)` — validates `credits > 0`, guards via `processed_events` with key `"refund:" + idempotencyKey`, calls `lotService.creditBack` → REFUND lot + REFUND txn. Idempotent.
- `RefundRequest record(UUID userId, @Positive int credits, UUID referenceId, String idempotencyKey)` — Jakarta validation.
- `RefundController POST /api/v1/wallet/refunds` — JWT-authenticated (service-to-service), returns 200 always (new or duplicate).

**RefundIT GREEN: 6/6 tests:**
- `paymentConfirmedGrantsPurchasedLot` — AMQP event triggers PURCHASED lot with 12-month expiry
- `paymentConfirmedIsIdempotentOnRedelivery` — duplicate eventId → balance stays at smsCount
- `refundServiceCreatesRefundLotAndCreditsWallet` — REFUND lot created, balance credited
- `refundIsIdempotentForSameIdempotencyKey` — same idempotencyKey → single credit
- `refundRejectsNonPositiveCredits` — 0 and -10 throw IllegalArgumentException
- `refundEndpointIsReachableAndIdempotent` — HTTP POST succeeds; duplicate no-op

### Task 2 — Alert/sweep jobs + reconciliation + Azampay prod gateway (TDD RED→GREEN)

**RED commit:** `9684a45` — LowCreditAlertIT + ExpiryWarningIT + ReconciliationIT failing (sweep/reconciliation packages missing)
**GREEN commit:** `a410fe7` — implementation green

**CreditLotRepository additions:**
- `findExpiringBefore(now, cutoff, lotType, pageable)` — finds lots expiring in [now, cutoff)
- `findExpiredBefore(cutoff, pageable)` — finds all lots with expiresAt < cutoff
- `findUserIdsWithBalanceBelow(threshold, now)` — JPQL GROUP BY userId HAVING SUM < threshold

**LowCreditAlertJob** `@Scheduled(fixedDelay 5min)`:
- Queries `findUserIdsWithBalanceBelow(threshold, now)`.
- Per-user dedup via `processed_events` key `"low-credit-alert:" + userId` — permanent MVP dedup (T-03-20).
- Emits `LowCreditAlert` outbox entry if user not already alerted.
- LowCreditAlertIT GREEN: 3/3 tests (below threshold, above threshold, dedup on second run).

**ExpiryWarningJob** `@Scheduled(cron daily 08:00)`:
- Queries `findExpiringBefore(now, now+7d, PURCHASED)` (bounded 100).
- Per-lot dedup via `processed_events` key `"expiry-warning:" + lotId`.
- Emits `ExpiryWarning` outbox entry with `{ lotId, userId, expiresAt, remainingCredits }` payload.
- ExpiryWarningIT GREEN: 3/3 tests (expiring within 7d, expiring later, dedup).

**ExpirySweepJob** `@Scheduled(cron daily 02:00)`:
- Queries `findExpiredBefore(now)` (bounded 200).
- Writes EXPIRE `CreditTransaction(delta=remainingCredits)` per expired lot.
- Idempotent: skips lot if EXPIRE transaction already exists.
- ExpiryWarningIT GREEN: `expirySweepJobWritesExpireTransactionForPastLot`.

**ReconciliationJob** (payment-service):
- `@Scheduled → scheduleReconcile()` delegates to `reconcile(Instant cutoff)` for testability.
- `reconcile(cutoff)`: finds PENDING/EXPIRED payments older than cutoff (bounded 50); calls `paymentGateway.queryTransactionStatus`; on success → drives `callbackProcessor.processCallback` (idempotent late-success path, D-04, T-03-18).
- ReconciliationIT GREEN: 3/3 tests (EXPIRED→SUCCESS+outbox, idempotent second run, TIMEOUT stays PENDING).

**AzampayPaymentGateway** `@Profile("prod")`:
- `RestClient` with bearer token from `AzampayTokenProvider` (no `RestTemplate` in code).
- `@CircuitBreaker(name="azampay")` + `@Retry(name="azampay")` on both `initiateStkPush` and `queryTransactionStatus` (T-03-19).
- `initiateStkPush`: POST `/azampay/mobileCheckout` (amount as String, no decimals — CLAUDE.md Azampay integration).
- `queryTransactionStatus`: GET `/azampay/transactionStatus?pgReferenceId={externalId}`.
- Fallback methods throw `AzampayTransientException` (circuit breaker open signal).
- Compile-verified; never instantiated in tests (stub profile active).

**AzampayTokenProvider** `@Profile("prod")`:
- Fetches bearer token from `/azampay/token/GenerateToken`; caches with 60s pre-expiry refresh.
- Thread-safe via `synchronized getToken()`.

**Resilience4jConfig**: Documents "azampay" circuit breaker configuration; auto-config reads from `application.yml` (50% failure threshold, 10-call window, 30s open state, 3 retry attempts).

## TDD Gate Compliance

| Gate | Status | Commits |
|------|--------|---------|
| RED (Task 1) | PASSED | `966c27a` — test(03-06) RefundIT failing |
| GREEN (Task 1) | PASSED | `60b3f29` — feat(03-06) implementation |
| RED (Task 2) | PASSED | `9684a45` — test(03-06) LowCreditAlertIT+ExpiryWarningIT+ReconciliationIT failing |
| GREEN (Task 2) | PASSED | `a410fe7` — feat(03-06) implementation |

## Deviations from Plan

### Auto-added: AzampayTransientException

**Rule 2 — Missing critical functionality:** `@CircuitBreaker` and `@Retry` annotations require a typed exception to trigger retry on transient failures. Added `AzampayTransientException extends RuntimeException` as the trigger class for the Resilience4j retry/circuit breaker configuration. Without it, only generic unchecked exceptions would trigger retry — too broad.

### Auto-fixed: RefundIT message publishing pattern

**Rule 1 — Bug:** Initial version of RefundIT used `rabbitTemplate.convertAndSend("payment.events", "payment.PaymentConfirmed", payload)` with a raw String. Jackson2JsonMessageConverter wraps this as a String message with wrong content type, causing deserialization failure in the consumer. Fixed to use `MessageBuilder.withBody(payload.getBytes())` with `contentType=application/json` — matching the pattern from `UserVerifiedConsumerIT` (03-04). No separate file created.

### Auto-added: reconcile(Instant cutoff) testable method

**Rule 1 — Testability:** `ReconciliationJob.scheduleReconcile()` uses `Instant.now()-5min` which cannot be controlled in tests. Exposed `reconcile(Instant cutoff)` as the actual implementation (mirrors `PaymentTimeoutJob.sweepExpiredPayments(Instant cutoff)` from 03-05). ReconciliationIT passes a future cutoff to include freshly-created payments.

### Scope note: RefundIT in wallet-service (not payment-service)

The existing `payment-service/src/test/.../RefundIT.java` was a placeholder for a different concern (payment-service failure path). The plan for 03-06 creates a NEW `wallet-service/src/test/.../RefundIT.java` covering both the PaymentConfirmed consumer and the RefundService — consolidating both assertions in one test class per the plan's `<action>` instruction ("include the PaymentConfirmed consumer assertion as a test method INSIDE RefundIT"). The payment-service placeholder remains as-is (still Assumptions.abort — it covers a different scope).

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-03-16 (duplicate PaymentConfirmed double-grant) | MITIGATED | `processed_events.tryInsert(eventId)` in `PaymentConfirmedConsumer`; `RefundIT.paymentConfirmedIsIdempotentOnRedelivery` asserts single lot |
| T-03-17 (refund double-credit / negative refund) | MITIGATED | `credits > 0` guard + `processed_events` on `"refund:"+idempotencyKey`; `RefundIT` asserts both |
| T-03-18 (reconciliation double-credits) | MITIGATED | `CallbackProcessor` status==SUCCESS guard + outbox `event_id UNIQUE`; `ReconciliationIT.reconciliationJobIsIdempotentForAlreadySuccessPayment` asserts single outbox event |
| T-03-19 (Azampay API slow/down stalls reconciliation) | MITIGATED | `@CircuitBreaker + @Retry` on `AzampayPaymentGateway` methods; `AzampayTransientException` as trigger |
| T-03-20 (alert/expiry spam) | ACCEPTED | Dedup via `processed_events` per-user/per-lot key limits to one alert; residual duplication low-impact at MVP |

## Known Stubs

- **AzampayPaymentGateway** `@Profile("prod")`: compile-verified but never instantiated in tests or CI (stub profile active). Becomes active only when deployed with `--spring.profiles.active=prod` and real Azampay credentials (`AZAMPAY_CLIENT_ID`, `AZAMPAY_CLIENT_SECRET` env vars). Merchant onboarding deferred to pre-launch.
- **payment-service RefundIT placeholder**: `payment-service/src/test/.../RefundIT.java` remains as `Assumptions.abort()` — it was a placeholder for a different scope (payment-service failure path + REFUND credit lot via outbox event). The wallet-service `RefundIT.java` fully covers the 03-06 requirements. The payment-service placeholder can be filled if Phase 4 adds a payment-service refund flow.

## Threat Surface Scan

No new trust boundaries beyond the plan's STRIDE register. `POST /api/v1/wallet/refunds` is a new endpoint but was in scope per the plan's threat model (T-03-17). It requires JWT authentication.

## Self-Check: PASSED

- [x] `./gradlew :services:wallet-service:test :services:payment-service:test` BUILD SUCCESSFUL
- [x] `RefundIT` GREEN: 6/6 (PaymentConfirmed consumer + refund assertions in single test class)
- [x] `LowCreditAlertIT` GREEN: 3/3
- [x] `ExpiryWarningIT` GREEN: 4/4 (warning + sweep)
- [x] `ReconciliationIT` GREEN: 3/3
- [x] `PaymentConfirmedConsumer` uses `payment.events` exchange (not wallet.events)
- [x] `PaymentConfirmedEvent` has no `com.opendesk.payment` import
- [x] `RefundService` uses `processed_events` with `"refund:"` prefix
- [x] `V4__create_outbox.sql` contains `CONSTRAINT uq_outbox_event_id UNIQUE (event_id)`
- [x] `AzampayPaymentGateway` annotated `@Profile("prod")`
- [x] `grep RestTemplate AzampayPaymentGateway.java` — 0 hits in code (1 in javadoc comment only)
- [x] No `javax.*` imports in any created file
- [x] No `com.opendesk.payment` import in any wallet-service source file
- [x] Commits: 966c27a (RED1), 60b3f29 (GREEN1), 9684a45 (RED2), a410fe7 (GREEN2)
