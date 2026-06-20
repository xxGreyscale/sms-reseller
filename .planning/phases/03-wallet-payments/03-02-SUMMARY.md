---
phase: 03-wallet-payments
plan: "02"
subsystem: wallet-service
tags: [ledger, lot-based, append-only, pessimistic-lock, select-for-update, tdd, financial-correctness]
dependency_graph:
  requires: [03-01]
  provides: [ledger-core-03, lot-grant-api-03, reservation-api-03, balance-api-03]
  affects: [03-03, 03-04, 03-05, 03-06]
tech_stack:
  added: []
  patterns:
    - "@Lock(LockModeType.PESSIMISTIC_WRITE) on JPQL query — expiry-soonest-first SELECT FOR UPDATE"
    - "Append-only CreditTransaction (no @Setter, immutable after save)"
    - "Derived balance via JPQL COALESCE SUM — no stored balance column"
    - "@Transactional(isolation=READ_COMMITTED) on ReservationService.reserve()"
    - "Consistent lock order (expires_at ASC) for deadlock avoidance"
    - "Partial index WHERE (granted - consumed - reserved) > 0 on credit_lots"
key_files:
  created:
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/CreditLot.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/LotType.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/CreditLotRepository.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/LotService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/transaction/CreditTransaction.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/transaction/TxnType.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/transaction/CreditTransactionRepository.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/balance/BalanceService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/ReservationService.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/ReservationResult.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/InsufficientCreditsException.java
    - services/wallet-service/src/main/resources/db/migration/V1__create_credit_lots.sql
    - services/wallet-service/src/main/resources/db/migration/V2__create_credit_transactions.sql
  modified:
    - services/wallet-service/src/test/java/com/opendesk/wallet/CreditLotExpiryTest.java (placeholder → real assertions)
    - services/wallet-service/src/test/java/com/opendesk/wallet/CreditReservationIT.java (placeholder → real assertions)
    - services/wallet-service/src/main/resources/application.yml (fixed duplicate spring: key)
decisions:
  - "grantPurchased derives expiresAt as createdAt.plus(365d) AFTER first save so @CreatedDate is already populated — two-save pattern ensures test assertion on lot.createdAt + 365d holds"
  - "CreditLotExpiryTest is NOT an IT (no @SpringBootTest annotation in class) — extends AbstractWalletIntegrationTest which carries @SpringBootTest; class is a unit-style integration test without the IT suffix to match 03-01 plan naming"
  - "Flyway migrations V1/V2 written for production; test profile uses ddl-auto=create-drop (Flyway disabled) so entity annotations drive the test schema automatically — no Testcontainers migration path needed at this stage"
  - "ReservationService page size capped at 50 lots per call — bounds the SELECT FOR UPDATE result set and protects against unbounded lock acquisition"
metrics:
  duration: "~25 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  tasks_total: 2
  files_created: 13
  files_modified: 3
---

# Phase 03 Plan 02: Wallet Ledger Core Summary

**One-liner:** Append-only lot-based credit ledger with derived balance, 12mo/30d expiry windows, and expiry-soonest-first pessimistic `SELECT FOR UPDATE` reservation proven by concurrency test (no negative balance possible).

## What Was Built

### Task 1: CreditLot/CreditTransaction entities + migrations + derived balance (TDD RED→GREEN)

**RED:** `CreditLotExpiryTest` converted from `Assumptions.abort` placeholder to 3 real assertions covering expiry exclusion, 12-month purchased expiry, 30-day bonus expiry, and multi-lot balance sum. Compilation failed (classes did not exist) — RED gate confirmed.

**GREEN implementation:**

1. **V1__create_credit_lots.sql** — `credit_lots` table: `id UUID PK`, `user_id UUID`, `lot_type VARCHAR(20)`, `granted INT`, `consumed INT DEFAULT 0`, `reserved INT DEFAULT 0`, `expires_at TIMESTAMPTZ`, `payment_id UUID nullable`, `created_at TIMESTAMPTZ DEFAULT now()`. Partial index `idx_credit_lots_user_expires` on `(user_id, expires_at) WHERE (granted - consumed - reserved) > 0`. COMMENT ON COLUMN for all notable fields. No `balance` column.

2. **V2__create_credit_transactions.sql** — `credit_transactions` table: `id UUID PK`, `user_id UUID`, `lot_id UUID REFERENCES credit_lots(id)`, `txn_type VARCHAR(20)`, `delta INT`, `reference_id UUID nullable`, `created_at TIMESTAMPTZ`. Append-only (no UPDATE ever issued).

3. **CreditLot** `@Entity` — `jakarta.*`, Lombok `@Getter @Setter @Builder`, `@Enumerated(EnumType.STRING)` lotType, `@CreatedDate` createdAt. Mutable `consumed` and `reserved` fields for in-place reservation updates under pessimistic lock.

4. **CreditTransaction** `@Entity` — NO `@Setter` (fully immutable after save). All-args constructor for creation. `@CreatedDate` for audit trail.

5. **LotType** enum: `PURCHASED | BONUS | REFUND`. **TxnType** enum: `GRANT | RESERVE | CONSUME | RELEASE | EXPIRE | REFUND`.

6. **CreditLotRepository** — `sumAvailableCredits(userId, now)` JPQL: `COALESCE(SUM(granted-consumed-reserved), 0) WHERE expiresAt > now`. Also `findAvailableByUserIdOrderByExpiresAtAsc` with `@Lock(PESSIMISTIC_WRITE)` (Task 2).

7. **LotService** — `grantBonus(userId, credits, expiresAt)`, `grantPurchased(userId, credits, paymentId)` (12-month expiry = `createdAt.plus(365d)`), `creditBack(userId, credits, referenceId)` (30-day REFUND lot). Each writes lot + GRANT/REFUND `CreditTransaction` in one `@Transactional`.

8. **BalanceService** — `getBalance(userId)` delegates to `sumAvailableCredits(userId, now)`. Never touches a stored column.

### Task 2: Expiry-soonest-first reservation with SELECT FOR UPDATE (TDD RED→GREEN)

**RED:** `CreditReservationIT` converted from placeholder to 4 real assertions (exact-balance success, over-request guard, soonest-first ordering, two-thread concurrency). Compilation failed — RED gate confirmed (same commit as Task 1 RED since both test files converted together).

**GREEN implementation:**

9. **ReservationService** — `reserve(userId, count, referenceId)` `@Transactional(READ_COMMITTED)`: acquires pessimistic write locks on available lots ordered `expiresAt ASC` (page 50), walks list taking `min(available, remaining)` per lot, increments `reserved`, writes `RESERVE CreditTransaction` per lot, throws `InsufficientCreditsException` (rollback) if remaining > 0 after all lots.

10. **ReservationResult** record — `List<UUID> lotIds, int reservedCount`.

11. **InsufficientCreditsException** — extends `RuntimeException` (triggers `@Transactional` rollback).

## Downstream API Surface (for plans 03-04, 03-05, 03-06)

| Method | Signature | Used by |
|--------|-----------|---------|
| `LotService.grantBonus` | `(UUID userId, int credits, Instant expiresAt) → CreditLot` | 03-04 UserVerifiedConsumer |
| `LotService.grantPurchased` | `(UUID userId, int credits, UUID paymentId) → CreditLot` | 03-05 payment callback |
| `LotService.creditBack` | `(UUID userId, int credits, UUID referenceId) → CreditLot` | 03-06 refund |
| `ReservationService.reserve` | `(UUID userId, int count, UUID referenceId) → ReservationResult` | Phase 4 campaign dispatch |
| `BalanceService.getBalance` | `(UUID userId) → int` | 03-03 WalletController, LowCreditAlertJob |
| `CreditLotRepository.sumAvailableCredits` | `(UUID userId, Instant now) → int` | 03-03 LowCreditAlertJob |
| `CreditTransactionRepository.findByUserIdOrderByCreatedAtDesc` | `(UUID userId, Pageable) → Page<CreditTransaction>` | 03-03 WalletController history endpoint |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed duplicate `spring:` key in wallet-service application.yml**
- **Found during:** Task 1 GREEN (tests failed with `DuplicateKeyException` from SnakeYAML)
- **Issue:** `application.yml` had two top-level `spring:` blocks — one at line 1 and one at line 29; SnakeYAML rejected the document entirely
- **Fix:** Merged both `spring:` blocks into a single block
- **Files modified:** `services/wallet-service/src/main/resources/application.yml`
- **Commit:** 4613914

## TDD Gate Compliance

- [x] `test(03-02):` RED commit exists: `a19b84f`
- [x] `feat(03-02):` GREEN commit exists after RED: `4613914`
- [x] REFACTOR not needed — implementation was clean on first pass

## Known Stubs

None — all production-relevant primitives are fully implemented and tested.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries introduced. All financial-correctness threats from the plan's threat model are mitigated:

| Threat | Mitigation | Verified by |
|--------|------------|-------------|
| T-03-02: Concurrent reservation double-spend | `@Lock(PESSIMISTIC_WRITE)` + rollback on insufficient | `CreditReservationIT.concurrentReservationsCannotProduceNegativeBalance` |
| T-03-03: Expired lots in balance | `expiresAt > now` filter on every balance/reservation query | `CreditLotExpiryTest.expiredLotsExcludedFromBalanceAndReservation` |
| T-03-04: Deadlock from inconsistent lock order | `ORDER BY l.expiresAt ASC` — consistent lock ordering | Code review + concurrency test passing |

## Self-Check: PASSED

- [x] CreditLot.java uses `jakarta.*` — no `javax.*` import
- [x] CreditTransaction.java has no `@Setter` annotation
- [x] V1__create_credit_lots.sql contains `credit_lots` and partial index `WHERE (granted - consumed - reserved) > 0`
- [x] Non-comment SQL in V1/V2 contains no `balance` column: `grep -iv '^--' V1.sql | grep -ic 'balance'` returns 0
- [x] CreditLotRepository contains `@Lock(LockModeType.PESSIMISTIC_WRITE)` and `ORDER BY l.expiresAt ASC`
- [x] BalanceService.getBalance derives via `sumAvailableCredits(userId, Instant.now())`
- [x] `./gradlew :services:wallet-service:test` BUILD SUCCESSFUL (7 new tests GREEN + all placeholders still skipped)
- [x] RED commit a19b84f exists before GREEN commit 4613914
