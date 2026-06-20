# Plan 03-03 Summary — Payment Foundation

**Plan:** 03-03 (Bundle catalog + payment state machine + stub gateway)
**Phase:** 03-wallet-payments · Wave 1
**Requirements:** PYMT-01
**Status:** Complete
**Tasks:** 2/2

## Self-Check: PASSED

## What was built

### Task 1 — Bundle catalog (PYMT-01) — committed `74731c3` (RED), `3bd1c19` (GREEN)
- `V1__create_sms_bundles.sql` + `V2__seed_sms_bundles.sql` — `sms_bundles` table seeded with the LOCKED MVP catalog as **raw TZS BIGINT** (D-11): Taster 50/0, Starter 200/3200, Growth 1000/14500, Pro 5000/65000, Scale 20000/240000.
- `SmsBundle` entity (`jakarta.*`, `price_tzs` as `long` — never BigDecimal/double, Pitfall 7), `BundleRepository`, `BundleController` (`GET /api/v1/bundles` — active bundles), `BundleDto`.
- `SecurityConfig` (resource-server only — STATELESS, CSRF disabled, validates Phase 2 JWTs via shared-security), `RabbitMqConfig` (topic exchange `payment.events`).
- `BundleCatalogIT` (converted from Wave 0 placeholder) asserts the catalog returns all 5 bundles with exact raw-TZS prices (Starter = 3200, NOT 320000).

### Task 2 — Payment state machine + single-pending + stub gateway — committed `49d9f23`
- `Payment` entity + `PaymentStatus` (PENDING → SUCCESS | EXPIRED | FAILED, D-06; EXPIRED→SUCCESS reachable via reconciliation D-04), `PaymentRepository`.
- `V3__create_payments.sql` — payments table; `amount_tzs` raw TZS BIGINT (D-11); `external_id UNIQUE` (Azampay idempotency key = payment UUID). **Single-pending enforcement via partial unique index `uq_payments_user_pending ON payments(user_id) WHERE status='PENDING'`** (D-05/D-13) — transactionally correct, no Redis lock.
- Mock-first `PaymentGateway` interface + `StubPaymentGateway` (configurable success/fail/timeout outcomes via magic-suffix, mirroring Phase 2 NIDA stub; `@Profile` mock-first per D-10), `StkPushRequest`, `StkPushResult`, `TransactionStatusResult`.

## API / contract surface for 03-05 (purchase flow)
- `PaymentGateway.initiateStkPush(StkPushRequest) → StkPushResult` and `queryStatus(...) → TransactionStatusResult` — implement purchase initiation + reconciliation against this interface.
- `Payment` state machine + `PaymentRepository`; single-pending is enforced at the DB layer (catch the unique-violation → reject second purchase).
- `BundleRepository` for amount/sms_count lookup at purchase time (copy amount into Payment.amount_tzs — no FK join, D-12).
- AMQP exchange `payment.events` (topic) ready for the `PaymentConfirmed` event 03-05 emits.

## Verification
- `./gradlew :services:payment-service:test` → BUILD SUCCESSFUL (BundleCatalogIT green; remaining placeholder ITs skip).
- `./gradlew :services:payment-service:compileJava` → BUILD SUCCESSFUL.

## Deviations
- None functional. Minor: a doc-comment wording tweak in `SmsBundle.java` (Pitfall 7 note).

## Notes
- Resumed and finalized after the original executor hit a session limit mid-Task-2; Task 2 code was already written and compiling — verified green, committed, summarized here.
