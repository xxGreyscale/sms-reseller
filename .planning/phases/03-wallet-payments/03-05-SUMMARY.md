---
phase: 03-wallet-payments
plan: "05"
subsystem: payment-service
tags: [payment-flow, azampay, idempotent-callback, transactional-outbox, tdd, wave-2]
dependency_graph:
  requires: [03-03]
  provides: [payment-initiation, idempotent-callback, payment-timeout-sweep, payment-outbox, payment-history]
  affects: [03-06]
tech_stack:
  added: []
  patterns:
    - TDD RED→GREEN per task (strict financial-correctness)
    - Transactional outbox (OutboxEntry/OutboxRepository/OutboxRelay copied from identity-service)
    - Idempotent callback guard — status==SUCCESS skip + outbox event_id UNIQUE constraint
    - PaymentTimeoutJob @Scheduled sweep with testable sweepExpiredPayments(Instant cutoff) hook
    - WebhookSignatureValidator interface + StubSignatureValidator (@Profile stub)
    - PaymentController userId from JWT subject (never request body — ASVS V4 IDOR prevention)
key_files:
  created:
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/PaymentService.java
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/PaymentController.java
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/PurchaseRequest.java
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/PaymentDto.java
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/PendingPaymentExistsException.java
    - services/payment-service/src/main/java/com/smsreseller/payment/payment/BundleNotPurchasableException.java
    - services/payment-service/src/main/java/com/smsreseller/payment/callback/CallbackController.java
    - services/payment-service/src/main/java/com/smsreseller/payment/callback/CallbackProcessor.java
    - services/payment-service/src/main/java/com/smsreseller/payment/callback/AzampayCallbackPayload.java
    - services/payment-service/src/main/java/com/smsreseller/payment/callback/WebhookSignatureValidator.java
    - services/payment-service/src/main/java/com/smsreseller/payment/callback/StubSignatureValidator.java
    - services/payment-service/src/main/java/com/smsreseller/payment/timeout/PaymentTimeoutJob.java
    - services/payment-service/src/main/java/com/smsreseller/payment/outbox/OutboxEntry.java
    - services/payment-service/src/main/java/com/smsreseller/payment/outbox/OutboxRepository.java
    - services/payment-service/src/main/java/com/smsreseller/payment/outbox/OutboxRelay.java
    - services/payment-service/src/main/java/com/smsreseller/payment/outbox/PaymentConfirmedEvent.java
    - services/payment-service/src/main/resources/db/migration/V4__create_outbox.sql
  modified:
    - services/payment-service/src/main/resources/application.yml (timeout-sweep-ms, timeout-max-per-run)
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentInitiationIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentHistoryIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/CallbackProcessingIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentTimeoutIT.java
decisions:
  - "PaymentConfirmedEvent contract: record(String eventId, UUID userId, UUID paymentId, int smsCount) — consumed by wallet-service Plan 06 to grant PURCHASED credit lot"
  - "Callback idempotency: dual-layer guard — (1) status==SUCCESS skip at service layer; (2) outbox event_id UNIQUE DB constraint prevents double-row on concurrent duplicate delivery (T-03-11)"
  - "EXPIRED→SUCCESS handled in CallbackProcessor (Pitfall 5, D-04) — skip only if SUCCESS, not EXPIRED; late callbacks after timeout sweep are a normal path"
  - "PaymentTimeoutJob exposes sweepExpiredPayments(Instant cutoff) for testability — production @Scheduled sweep uses now()-timeoutSeconds cutoff; tests pass future cutoff to fast-forward"
  - "BundleNotPurchasableException maps to 400; PendingPaymentExistsException maps to 409 via @ResponseStatus"
  - "StubSignatureValidator always-valid under stub profile; real HMAC validator deferred to merchant onboarding (Open Question 1)"
metrics:
  duration: "~35 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  tasks_total: 2
  files_created: 17
  files_modified: 5
---

# Phase 03 Plan 05: Payment Purchase Flow Summary

**One-liner:** Mock-first Azampay purchase lifecycle — STK push initiation with single-pending enforcement, idempotent callback (PENDING+EXPIRED→SUCCESS), 2-minute timeout sweep to EXPIRED, PaymentConfirmed transactional outbox for wallet grant, and JWT-scoped payment history.

## What Was Built

### Task 1 — Purchase initiation + single-pending + payment history + outbox infra

**RED commit:** `4de8c60` — PaymentInitiationIT + PaymentHistoryIT failing assertions
**GREEN commit:** `db8b90d` — implementation green

- **PaymentService.initiate():** Application-layer single-pending check + DB partial unique index backstop (`uq_payments_user_pending`); loads bundle + validates `isPurchasable`; creates Payment(PENDING, externalId=paymentId); calls gateway.initiateStkPush. Catches `DataIntegrityViolationException` → `PendingPaymentExistsException` (409) for concurrent race.
- **PaymentController:** `POST /api/v1/payments` (returns `PaymentDto` with `timeoutSeconds=120`, PYMT-03 countdown); `GET /api/v1/payments` (paginated, JWT-scoped). UserId extracted from `auth.getToken().getSubject()` — never from request body (IDOR prevention).
- **PaymentDto:** `paymentId, bundleId, amountTzs, smsCount, status, createdAt, timeoutSeconds` — 120s timeout hint for UI countdown.
- **PurchaseRequest:** `bundleId, msisdn, provider` with `@NotNull`/`@NotBlank` validation.
- **Exceptions:** `PendingPaymentExistsException` → 409; `BundleNotPurchasableException` → 400.
- **OutboxEntry/OutboxRepository/OutboxRelay:** Verbatim copy from identity-service, package changed to `com.smsreseller.payment.outbox`, exchange constant → `payment.events`.
- **PaymentConfirmedEvent record:** `(String eventId, UUID userId, UUID paymentId, int smsCount)` — contract for wallet-service Plan 06 consumer.
- **V4__create_outbox.sql:** Outbox table with `CONSTRAINT uq_outbox_event_id UNIQUE (event_id)` and unsent partial index.

Tests GREEN: POST creates PENDING with correct amount/smsCount/externalId; second POST = 409; Taster = 400; history JWT-scoped with IDOR protection; 401 without auth.

### Task 2 — Idempotent callback (PENDING+EXPIRED→SUCCESS) + EXPIRED timeout sweep

**RED commit:** `9566056` — CallbackProcessingIT + PaymentTimeoutIT failing assertions
**GREEN commit:** `eeab44d` — implementation green

- **CallbackProcessor.processCallback():** `@Transactional` — loads payment by `utilityRef`; if status==SUCCESS → return (idempotent guard, PYMT-06); if PENDING or EXPIRED + transactionStatus=="success" → flip to SUCCESS + set operatorReference + write PaymentConfirmedEvent outbox row in same TX (T-03-14); if "fail" → flip to FAILED (no outbox). EXPIRED→SUCCESS path explicitly handled (D-04, Pitfall 5 — only skip if SUCCESS, never skip EXPIRED).
- **AzampayCallbackPayload:** record with `utilityRef` (our externalId), `transactionStatus` (success/fail), `reference` (operator ref).
- **WebhookSignatureValidator interface + StubSignatureValidator:** Always-valid under `@Profile("stub")`; HMAC implementation deferred to merchant onboarding (Open Question 1).
- **CallbackController:** `POST /api/v1/payments/callback` — public (permitAll), no JWT param, validates signature then delegates to processor; returns 200 always (prevents Azampay retrying on 4xx).
- **PaymentTimeoutJob:** `@Scheduled(fixedDelayString)` sweep marks PENDING→EXPIRED after 120s. Exposes `sweepExpiredPayments(Instant cutoff)` for test fast-forward. Bounded by `timeout-max-per-run` (100). Per-item try/catch mirrors VerificationRetryJob.

Tests GREEN:
- Success callback: PENDING→SUCCESS + exactly 1 PaymentConfirmed outbox row (PYMT-04)
- Duplicate callback: exactly 1 outbox row after 2 deliveries (PYMT-06, T-03-11 — outbox event_id UNIQUE)
- Late success on EXPIRED: EXPIRED→SUCCESS + 1 outbox row (D-04, Pitfall 5)
- Fail callback: PENDING→FAILED + 0 outbox rows
- Timeout sweep: PENDING→EXPIRED when cutoff in future (PYMT-03/07, T-03-13)
- EXPIRED visible in payment history (no infinite PENDING)

## PaymentConfirmed Event Contract (for Plan 06 wallet consumer)

```
Exchange: payment.events (topic)
Routing key: payment.PaymentConfirmed
Payload (JSON):
{
  "eventId": "<UUID>",          // idempotency key for wallet consumer processed_events
  "userId": "<UUID>",           // user to credit
  "paymentId": "<UUID>",        // payment record reference
  "smsCount": 200               // SMS credits to grant as PURCHASED lot (12-month expiry)
}
```

The wallet-service Plan 06 consumer binds a durable queue to `payment.events` and uses `processed_events ON CONFLICT DO NOTHING` on `eventId` to ensure exactly-once credit grant.

## TDD Gate Compliance

| Gate | Status | Commits |
|------|--------|---------|
| RED (Task 1) | PASSED | `4de8c60` — test(03-05) failing assertions |
| GREEN (Task 1) | PASSED | `db8b90d` — feat(03-05) implementation |
| RED (Task 2) | PASSED | `9566056` — test(03-05) failing assertions |
| GREEN (Task 2) | PASSED | `eeab44d` — feat(03-05) implementation |

## Deviations from Plan

### Auto-added: BundleNotPurchasableException

**Rule 2 — Missing critical validation:** The plan named the exception class but didn't mention a separate 400 response mapping. Added `BundleNotPurchasableException` with `@ResponseStatus(HttpStatus.BAD_REQUEST)` to avoid a 500 on non-purchasable bundle purchases.

### Auto-added: sweepExpiredPayments(Instant cutoff) public method

**Rule 1 — Testability bug fix:** `PaymentTimeoutJob.sweep()` uses `Instant.now()` internally, which makes testing impossible without sleeping 120 seconds. Exposed `sweepExpiredPayments(Instant cutoff)` as a package-accessible method. The `@Scheduled` method calls it with the real cutoff. Tests call it directly with a future cutoff to fast-forward time. This is the standard pattern for @Scheduled jobs in the codebase (mirrors VerificationRetryJob).

## Threat Surface Scan

No new threat surface beyond the plan's STRIDE register. All mitigations implemented:

| Threat | Status |
|--------|--------|
| T-03-11 Duplicate callback double-emit | MITIGATED — status==SUCCESS guard + outbox event_id UNIQUE |
| T-03-12 Forged success callback | MITIGATED — StubSignatureValidator + utilityRef DB existence check |
| T-03-13 Infinite PENDING | MITIGATED — PaymentTimeoutJob → EXPIRED at 120s |
| T-03-14 Lost PaymentConfirmed on crash | MITIGATED — outbox written in same TX as SUCCESS flip |
| T-03-15 Two concurrent PENDING payments | MITIGATED — existsByUserIdAndStatus check + uq_payments_user_pending index |

## Known Stubs

- **StubSignatureValidator**: always-valid (stub profile). Real HMAC validator not implemented — deferred to merchant onboarding when Azampay documents their signature scheme (Open Question 1). Tracked in 03-RESEARCH.md.

## Self-Check: PASSED

- [x] `./gradlew :services:payment-service:test` BUILD SUCCESSFUL (all 4 target ITs + BundleCatalogIT green)
- [x] PaymentConfirmedEvent outbox row count asserted to exactly 1 after duplicate callback
- [x] EXPIRED→SUCCESS path tested (lateSuccessCallbackOnExpiredPaymentStillSucceeds)
- [x] Outbox event_id UNIQUE constraint in V4__create_outbox.sql
- [x] CallbackController has no JwtAuthenticationToken param (public endpoint)
- [x] StubSignatureValidator annotated @Profile("stub")
- [x] No javax.* imports in any created file
- [x] PaymentDto.timeoutSeconds = 120 (PYMT-03 countdown contract)
- [x] Commits: 4de8c60 (RED1), db8b90d (GREEN1), 9566056 (RED2), eeab44d (GREEN2)
