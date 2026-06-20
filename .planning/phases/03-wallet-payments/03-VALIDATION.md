---
phase: 3
slug: wallet-payments
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-20
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers 1.21.2 (Postgres 16 + Redis 7 + RabbitMQ via `@ServiceConnection`), Spring Boot Test |
| **Config file** | `services/wallet-service/src/test/.../AbstractWalletIntegrationTest.java` + `services/payment-service/.../AbstractPaymentIntegrationTest.java` (Wave 0 installed, mirroring Phase 2 `identity-service` base) |
| **Quick run command** | `./gradlew :services:wallet-service:test :services:payment-service:test --tests "*UnitTest"` |
| **Full suite command** | `./gradlew :services:wallet-service:test :services:payment-service:test` |
| **Estimated runtime** | ~60–120 seconds (Testcontainers spin-up dominated) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (unit tests for touched module)
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

> Populated by Wave 0 (03-01). Every requirement ID maps to at least one automated row.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| T3.1 | 03-01 | W0 | WLET-01 | T-03-01 | Derived balance excludes expired lots | placeholder-IT | `./gradlew :services:wallet-service:test --tests "BalanceIT"` | ✅ | ⬜ pending |
| T3.2 | 03-01 | W0 | WLET-02 | — | Transaction history is paginated and user-scoped | placeholder-IT | `./gradlew :services:wallet-service:test --tests "TransactionHistoryIT"` | ✅ | ⬜ pending |
| T3.3 | 03-01 | W0 | WLET-03 | — | Reservation uses FIFO expiry-soonest-first with pessimistic lock | placeholder-IT | `./gradlew :services:wallet-service:test --tests "CreditReservationIT"` | ✅ | ⬜ pending |
| T3.4 | 03-01 | W0 | WLET-04 | — | LowCreditAlertJob emits event when balance < threshold (20) | placeholder-IT | `./gradlew :services:wallet-service:test --tests "LowCreditAlertIT"` | ✅ | ⬜ pending |
| T3.5 | 03-01 | W0 | WLET-05 | — | ExpirySweepJob emits warning for lots expiring within 7 days | placeholder-IT | `./gradlew :services:wallet-service:test --tests "ExpiryWarningIT"` | ✅ | ⬜ pending |
| T3.6a | 03-01 | W0 | WLET-06 | — | Purchased lots expire after 12 months; bonus after 30 days | placeholder-IT | `./gradlew :services:wallet-service:test --tests "CreditLotExpiryTest"` | ✅ | ⬜ pending |
| T3.6b | 03-01 | W0 | WLET-07 | — | Expired lots excluded from balance and reservation queries | placeholder-IT | `./gradlew :services:wallet-service:test --tests "CreditLotExpiryTest"` | ✅ | ⬜ pending |
| T3.7 | 03-01 | W0 | WLET-01 (cross) | T-03-01 | UserVerified event grants 50 bonus credits idempotently | placeholder-IT | `./gradlew :services:wallet-service:test --tests "UserVerifiedConsumerIT"` | ✅ | ⬜ pending |
| T3.8 | 03-01 | W0 | PYMT-01 | — | Bundle catalog returns all active seeded bundles | placeholder-IT | `./gradlew :services:payment-service:test --tests "BundleCatalogIT"` | ✅ | ⬜ pending |
| T3.9 | 03-01 | W0 | PYMT-02 | T-03-01 | Payment initiation creates PENDING record and triggers gateway | placeholder-IT | `./gradlew :services:payment-service:test --tests "PaymentInitiationIT"` | ✅ | ⬜ pending |
| T3.10a | 03-01 | W0 | PYMT-03 | — | Payment times out (EXPIRED) if no callback within 2 minutes | placeholder-IT | `./gradlew :services:payment-service:test --tests "PaymentTimeoutIT"` | ✅ | ⬜ pending |
| T3.10b | 03-01 | W0 | PYMT-07 | — | Expired payment cannot be reactivated | placeholder-IT | `./gradlew :services:payment-service:test --tests "PaymentTimeoutIT"` | ✅ | ⬜ pending |
| T3.11a | 03-01 | W0 | PYMT-04 | T-03-SC | Successful callback transitions PENDING→COMPLETED and emits credit event | placeholder-IT | `./gradlew :services:payment-service:test --tests "CallbackProcessingIT"` | ✅ | ⬜ pending |
| T3.11b | 03-01 | W0 | PYMT-06 | T-03-SC | Duplicate callback is idempotent — no double credit grant | placeholder-IT | `./gradlew :services:payment-service:test --tests "CallbackProcessingIT"` | ✅ | ⬜ pending |
| T3.12 | 03-01 | W0 | PYMT-05 | — | Payment history returns paginated records for authenticated user | placeholder-IT | `./gradlew :services:payment-service:test --tests "PaymentHistoryIT"` | ✅ | ⬜ pending |
| T3.13 | 03-01 | W0 | PYMT-08 | — | Failed payment creates REFUND credit lot for user | placeholder-IT | `./gradlew :services:payment-service:test --tests "RefundIT"` | ✅ | ⬜ pending |
| T3.14 | 03-01 | W0 | PYMT-03+PYMT-04 (cross) | — | ReconciliationJob processes late-success callback for EXPIRED payment | placeholder-IT | `./gradlew :services:payment-service:test --tests "ReconciliationIT"` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `wallet-service` + `payment-service` Gradle modules + `AbstractIntegrationTest` bases (PG16 + Redis + RabbitMQ Testcontainers), mirroring Phase 2 `02-01`
- [x] Placeholder failing IT per requirement ID (WLET-01..07, PYMT-01..08) so the validation map is non-empty
- [ ] Shared AMQP test fixture for the inbound `UserVerified` event + outbound payment/credit events

*Shared AMQP fixture deferred to 03-04 (UserVerifiedConsumerIT implementation).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real Azampay STK push on a physical handset | PYMT-02 | Requires live merchant credentials + a real phone (Phase 0 dependency); stub covers success/fail/timeout in CI | When merchant account arrives: switch to `@Profile("prod")`, initiate a Starter purchase, confirm STK prompt + credit on success |

*All other phase behaviors have automated verification via the stub gateway + Testcontainers.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** Wave 0 green — 14 placeholder ITs report skipped, BUILD SUCCESSFUL
