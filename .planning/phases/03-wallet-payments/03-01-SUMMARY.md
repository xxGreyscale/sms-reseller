---
phase: 03-wallet-payments
plan: "01"
subsystem: wallet-service, payment-service
tags: [test-infrastructure, build, testcontainers, jwt, rsa, wave-0]
dependency_graph:
  requires: [02-01]
  provides: [test-infra-03, build-deps-03, placeholder-ITs-03]
  affects: [03-02, 03-03, 03-04, 03-05]
tech_stack:
  added:
    - spring-boot-starter-security (BOM) — wallet-service, payment-service
    - spring-boot-starter-oauth2-resource-server (BOM) — wallet-service, payment-service
    - spring-boot-starter-validation (BOM) — wallet-service, payment-service
    - spring-boot-starter-data-redis (BOM) — wallet-service, payment-service
    - spring-boot-starter-amqp (BOM) — wallet-service, payment-service
    - spring-retry (BOM) — wallet-service, payment-service
    - resilience4j-spring-boot3 2.2.0 — wallet-service, payment-service
    - mapstruct 1.6.3 — wallet-service, payment-service
    - postgresql driver runtimeOnly (BOM) — wallet-service, payment-service
    - spring-boot-testcontainers (BOM) — wallet-service, payment-service
    - testcontainers-postgresql 1.21.2 — wallet-service, payment-service
    - testcontainers-rabbitmq 1.21.2 — wallet-service, payment-service
    - testcontainers-junit-jupiter 1.21.2 — wallet-service, payment-service
  patterns:
    - Testcontainers @ServiceConnection for Postgres 16 (wallet_test / payment_test)
    - Testcontainers GenericContainer + @DynamicPropertySource for Redis 7
    - Testcontainers RabbitMQContainer + @DynamicPropertySource for RabbitMQ 3
    - RSA-2048 test keypair copied per-module (same keys as Phase 2 identity-service)
    - Assumptions.abort for lightweight placeholder ITs (no Spring context, no container spin-up)
key_files:
  created:
    - services/wallet-service/build.gradle.kts (full Phase 3 dep set)
    - services/payment-service/build.gradle.kts (full Phase 3 dep set)
    - services/wallet-service/src/main/java/com/opendesk/wallet/WalletServiceApplication.java
    - services/payment-service/src/main/java/com/opendesk/payment/PaymentServiceApplication.java
    - services/wallet-service/src/main/resources/application.yml
    - services/payment-service/src/main/resources/application.yml
    - services/wallet-service/src/test/resources/application-test.yml
    - services/payment-service/src/test/resources/application-test.yml
    - services/wallet-service/src/test/java/com/opendesk/wallet/AbstractWalletIntegrationTest.java
    - services/payment-service/src/test/java/com/opendesk/payment/AbstractPaymentIntegrationTest.java
    - services/wallet-service/src/test/resources/test-keys/jwt-private.pem
    - services/wallet-service/src/test/resources/test-keys/jwt-public.pem
    - services/payment-service/src/test/resources/test-keys/jwt-private.pem
    - services/payment-service/src/test/resources/test-keys/jwt-public.pem
    - services/wallet-service/src/test/java/com/opendesk/wallet/BalanceIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/TransactionHistoryIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/CreditReservationIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/LowCreditAlertIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/ExpiryWarningIT.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/CreditLotExpiryTest.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/UserVerifiedConsumerIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/BundleCatalogIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/PaymentInitiationIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/PaymentTimeoutIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/CallbackProcessingIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/PaymentHistoryIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/RefundIT.java
    - services/payment-service/src/test/java/com/opendesk/payment/ReconciliationIT.java
  modified:
    - .planning/phases/03-wallet-payments/03-VALIDATION.md (verification map populated, nyquist_compliant=true)
decisions:
  - "RabbitMQ added to AbstractIntegrationTest base (vs identity-service which also has it) — payment-service needs it for outbox relay and callback processing tests; wallet-service needs it for UserVerifiedConsumer tests"
  - "RSA test keys copied verbatim from identity-service — same keypair works for all services since shared-security NimbusJwtDecoder only needs the public key to validate; no new keypair generated"
  - "application-test.yml sets spring.flyway.enabled=false + ddl-auto=create-drop — mirrors identity-service test config; Testcontainers PostgreSQLContainer provides a fresh DB per test run"
  - "No spring-boot-starter-mail added — Phase 5 owns notification delivery (CLAUDE.md constraint)"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-20"
  tasks_completed: 3
  tasks_total: 3
  files_created: 29
  files_modified: 3
---

# Phase 03 Plan 01: Test Infrastructure & Build Foundation Summary

**One-liner:** Testcontainers PG16 + Redis 7 + RabbitMQ base classes for wallet-service and payment-service, full Phase 3 dep set, and 14 placeholder ITs covering all 15 Phase 3 requirement IDs.

## What Was Built

Wave 0 test infrastructure for wallet-service and payment-service, mirroring Phase 2's 02-01 pattern exactly:

1. **Build deps + entrypoints + config (Task 1):** Full Phase 3 dependency set added to both `wallet-service/build.gradle.kts` and `payment-service/build.gradle.kts` — security, oauth2-resource-server, validation, data-redis, amqp, spring-retry, resilience4j-spring-boot3, mapstruct, postgresql driver, and all four testcontainers modules. `WalletServiceApplication` and `PaymentServiceApplication` with `@SpringBootApplication`, `@EnableScheduling`, `@EnableJpaAuditing`. `application.yml` files with virtual threads, Flyway, actuator health probes, JWT public-key-location, wallet low-credit-threshold (D-08), and payment timeout/stub/reconciliation keys (D-06).

2. **AbstractIntegrationTest bases + RSA keys + test config (Task 2):** `AbstractWalletIntegrationTest` and `AbstractPaymentIntegrationTest` as exact copies of identity's `AbstractIntegrationTest` — PostgreSQL 16 `@ServiceConnection`, Redis 7 `GenericContainer` + `@DynamicPropertySource`, RabbitMQ 3 `RabbitMQContainer` + `@DynamicPropertySource`. Containers started once in a static block (Ryuk cleanup). `@ActiveProfiles({"stub","test"})`. RSA-2048 test keypair PEM files copied verbatim into each module's `src/test/resources/test-keys/` so shared-security `NimbusJwtDecoder` resolves a public key in ITs. `application-test.yml` with Flyway disabled, `ddl-auto=create-drop`, and `public-key-location: classpath:test-keys/jwt-public.pem`.

3. **Placeholder ITs + validation map (Task 3):** 14 placeholder test classes — 7 for wallet-service (WLET-01..07) and 7 for payment-service (PYMT-01..08 with cross-requirements). Each uses `Assumptions.abort("placeholder — implemented in 03-NN")` — no `@SpringBootTest`, no Spring context, no container spin-up. Reports as skipped, not failed. `03-VALIDATION.md` populated with 17 rows covering all 15 requirement IDs, `nyquist_compliant` set to `true`.

## Deviations from Plan

None — plan executed exactly as written. All three tasks completed without auto-fix deviations.

## Threat Model Notes

- **T-03-01 (accepted):** RSA test keypair committed to `src/test/resources/test-keys/` in both services. Keys are test-only (same pair as identity-service, clearly labelled); prod keys injected via K8s Secret (INFR-05). Files are in `src/test/` scope and excluded from production artifacts.
- **T-03-SC (mitigated):** All new deps are Spring BOM / Maven Central, already vetted in Phase 2 research. No new third-party registries. `testcontainers-rabbitmq` is from `org.testcontainers` group (same as all other testcontainers deps, already in catalog from 02-01).

## Known Stubs

All 14 ITs are intentional placeholder stubs (by design for Wave 0):

| File | Stub method | Resolved in plan |
|------|-------------|-----------------|
| BalanceIT.java | derivedBalanceSumsNonExpiredLots | 03-02 |
| TransactionHistoryIT.java | transactionHistoryReturnsPaginatedResults | 03-02 |
| CreditReservationIT.java | reservationUsesExpiryOrderedFifoWithPessimisticLock | 03-03 |
| LowCreditAlertIT.java | alertJobEmitsEventWhenBalanceBelowThreshold | 03-03 |
| ExpiryWarningIT.java | expirySweepJobEmitsWarningForLotsDueSoon | 03-03 |
| CreditLotExpiryTest.java | purchasedLotsExpireAfter12MonthsBonusAfter30Days | 03-03 |
| CreditLotExpiryTest.java | expiredLotsExcludedFromBalanceAndReservation | 03-03 |
| UserVerifiedConsumerIT.java | userVerifiedEventGrantsFiftyCreditBonusLotIdempotently | 03-04 |
| BundleCatalogIT.java | bundleCatalogReturnsAllActiveSeededBundles | 03-05 |
| PaymentInitiationIT.java | initiatePaymentCreatesPendingRecordAndTriggersGateway | 03-05 |
| PaymentTimeoutIT.java | paymentMarkedExpiredWhenNoCallbackWithinTwoMinutes | 03-05 |
| CallbackProcessingIT.java | successCallbackTransitionsPendingToCompletedAndEmitsCreditEvent | 03-05 |
| PaymentHistoryIT.java | paymentHistoryReturnsPaginatedRecordsForAuthenticatedUser | 03-05 |
| RefundIT.java | failedPaymentCallbackCreatesRefundCreditLotForUser | 03-05 |
| ReconciliationIT.java | reconciliationJobProcessesLateSuccessCallbackForExpiredPayment | 03-05 |

## Self-Check: PASSED

- [x] WalletServiceApplication.java contains @SpringBootApplication, @EnableScheduling, @EnableJpaAuditing
- [x] PaymentServiceApplication.java contains @SpringBootApplication, @EnableScheduling, @EnableJpaAuditing
- [x] Both build.gradle.kts contain libs.spring.boot.starter.oauth2.resource.server and libs.testcontainers.rabbitmq
- [x] payment-service application.yml contains app.payment.timeout-seconds and app.reconciliation.fixed-delay-ms
- [x] AbstractWalletIntegrationTest contains literal "postgres:16" and "rabbitmq:3-management"
- [x] AbstractPaymentIntegrationTest contains literal "postgres:16" and "rabbitmq:3-management"
- [x] Both bases annotated @ActiveProfiles({"stub", "test"})
- [x] jwt-public.pem exists in both services' src/test/resources/test-keys/
- [x] 14 test classes exist with the exact names from the plan
- [x] No @SpringBootTest in any placeholder IT
- [x] 03-VALIDATION.md has 17 rows covering all 15 requirement IDs
- [x] nyquist_compliant set true in 03-VALIDATION.md frontmatter
- [x] No javax.* imports in any created file
- [x] `./gradlew :services:wallet-service:test :services:payment-service:test` BUILD SUCCESSFUL
- [x] Commits: 94dbb34 (deps+entrypoints), b394bc3 (test bases+keys), c1c05ab (placeholder ITs)
