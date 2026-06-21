---
phase: 04-contacts-messaging
plan: "07"
subsystem: wallet-service
tags: [per-lot-allocation, consume-release, amqp-consumer, idempotency, processed_events, tdd, mesg-10, d-13, d-15]
dependency_graph:
  requires: [04-01, 03-02, 03-04, 03-06]
  provides: [lot-allocation-contract-04, messaging-event-consumer-04, consume-release-ops-04]
  affects: [04-05, 04-06]
tech_stack:
  added: []
  patterns:
    - "LotAllocation record(UUID lotId, int count) — per-lot reservation count for D-13"
    - "ReservationResult extended with List<LotAllocation> allocations (legacy lotIds/reservedCount preserved back-compat)"
    - "@RabbitListener @QueueBinding passive bind to messaging.events — wallet does NOT redeclare messaging exchange"
    - "MessagingEventConsumer tryInsert(eventId) guard on all three handlers (T-04-08 idempotency)"
    - "@Transactional consumer: tryInsert + ledger mutation commit together or both roll back (T-04-09)"
key_files:
  created:
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/LotAllocation.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/MessageAccepted.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/MessageReleased.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/MessageRefundDue.java
    - services/wallet-service/src/main/java/com/opendesk/wallet/consumer/MessagingEventConsumer.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/ReservationAllocationTest.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/LotConsumeReleaseTest.java
    - services/wallet-service/src/test/java/com/opendesk/wallet/MessagingEventConsumerIT.java
  modified:
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/ReservationResult.java (added allocations field)
    - services/wallet-service/src/main/java/com/opendesk/wallet/reservation/ReservationService.java (captures per-lot take into allocations)
    - services/wallet-service/src/main/java/com/opendesk/wallet/lot/LotService.java (added consumeFromLot + releaseFromLot)
decisions:
  - "ReservationResult is a Java record — records are immutable and have fixed components; adding allocations as a third component requires callers to use three-arg constructor. The lotIds and reservedCount components remain at positions 0 and 1 — existing callers that destructure via canonical constructor must add the third arg. Any new callers use the three-arg constructor. This is a breaking change at the record level but no Phase 3 callers construct ReservationResult directly (only ReservationService does), so there is no regression."
  - "MessagingEventConsumer does NOT declare a messaging.events TopicExchange @Bean in wallet-service — the exchange is owned by messaging-service; @QueueBinding creates the wallet queues and binds them passively."
  - "MessageAccepted/MessageReleased/MessageRefundDue are local records in com.opendesk.wallet.consumer — service boundary respected (no com.opendesk.messaging import), same pattern as UserVerifiedEvent and PaymentConfirmedEvent."
  - "consumeFromLot/releaseFromLot apply a delta of 1 per call — each event corresponds to exactly one message/one credit; MessagingEventConsumer calls them once per event after idempotency guard."
metrics:
  duration: "~35 minutes"
  completed: "2026-06-21"
  tasks_completed: 2
  tasks_total: 2
  files_created: 8
  files_modified: 3
---

# Phase 04 Plan 07: Wallet MessagingEvent Consumer Summary

**One-liner:** Extends ReservationResult with per-lot LotAllocation list (D-13), adds consumeFromLot/releaseFromLot to LotService (D-15), and wires an idempotent MessagingEventConsumer applying CONSUME/RELEASE/REFUND from messaging.events — wallet side of MESG-10 complete.

## What Was Built

### Task 1: Per-lot allocation in ReservationResult + consumeFromLot/releaseFromLot (TDD RED→GREEN)

**RED commit:** `a4d8f28` — ReservationAllocationTest + LotConsumeReleaseTest failing (LotAllocation, allocations() method, consumeFromLot/releaseFromLot do not exist)

**GREEN commit:** `1eb528f` — implementation green, all Phase 3 tests still pass

**LotAllocation record:**
```java
public record LotAllocation(UUID lotId, int count) {}
```
Per-lot count from a reservation. Carries which lot and how many credits came from it, enabling messaging-service to assign each recipient to a specific lot ordered expiry-soonest-first.

**ReservationResult extended (back-compat preserved):**
```java
public record ReservationResult(List<UUID> lotIds, int reservedCount, List<LotAllocation> allocations) {}
```
The legacy `lotIds` and `reservedCount` components remain at positions 0 and 1. `allocations` is the new third component. No Phase 3 caller constructs `ReservationResult` directly (only `ReservationService` does), so no regression.

**ReservationService.reserve() change** (inside the expiry-soonest-first loop):
```java
List<LotAllocation> allocations = new ArrayList<>();
// inside loop:
allocations.add(new LotAllocation(lot.getId(), take));
// at end:
return new ReservationResult(lotIds, count, allocations);
```

**LotService additions:**
- `consumeFromLot(userId, lotId)`: `reserved--`, `consumed++`, writes `CONSUME CreditTransaction`
- `releaseFromLot(userId, lotId)`: `reserved--`, consumed unchanged, writes `RELEASE CreditTransaction`
- Both `@Transactional`, both load lot via `findById().orElseThrow()`, same pattern as `creditBack`

**ReservationAllocationTest GREEN: 2/2 tests:**
- `reservationExposesPerLotAllocationsOrderedByExpiryAscending` — reserve 6 across 2 lots (lotA=3, lotB=5); allocations = [{lotA,3},{lotB,3}]; sum=6; legacy lotIds=[lotA,lotB]
- `singleLotReservationAllocationsHasOneEntry` — reserve 4 from single lot with 10 credits

**LotConsumeReleaseTest GREEN: 2/2 tests:**
- `consumeDecrementsReservedIncrementsConsumed` — after reserve 2, consume 1: reserved→1, consumed→1, CONSUME txn written
- `releaseDecrementsReservedLeavesConsumedUnchanged` — after reserve 2, release 1: reserved→1, consumed stays 0, RELEASE txn written

### Task 2: Idempotent MessagingEventConsumer (TDD RED→GREEN)

**RED commit:** `abfb5dd` — MessagingEventConsumerIT failing (MessagingEventConsumer does not exist)

**GREEN commit:** `39d9064` — implementation green

**Event records (local copies, service boundary respected):**

| Record | Payload | Routing Key |
|--------|---------|-------------|
| `MessageAccepted` | eventId, messageId, userId, lotId | `messaging.MessageAccepted` |
| `MessageReleased` | eventId, messageId, userId, lotId | `messaging.MessageReleased` |
| `MessageRefundDue` | eventId, messageId, userId, lotId, creditsToRefund | `messaging.MessageRefundDue` |

**MessagingEventConsumer — three handlers:**

```java
// MessageAccepted → CONSUME
@RabbitListener(bindings = @QueueBinding(
    value = @Queue(name = "wallet.messaging.MessageAccepted", durable = "true"),
    exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true"),
    key = "messaging.MessageAccepted"
))
@Transactional
public void onMessageAccepted(MessageAccepted event) {
    if (!processedEventRepository.tryInsert(event.eventId())) return;
    lotService.consumeFromLot(event.userId(), event.lotId());
}

// MessageReleased → RELEASE
// MessageRefundDue → creditBack(userId, creditsToRefund, messageId)
```

All three handlers:
1. Guard via `processedEventRepository.tryInsert(eventId)` — returns early on duplicate (T-04-08)
2. Apply the ledger mutation in the same `@Transactional` (T-04-09)
3. Bind passively — no `messaging.events` TopicExchange `@Bean` declared in wallet-service

**MessagingEventConsumerIT GREEN: 3/3 tests (including idempotency replay):**
- `messageAcceptedConsumesLot` — CONSUME applied; replay same eventId → no-op
- `messageReleasedReleasesLot` — RELEASE applied; replay → no-op
- `messageRefundDueCreditsBack` — REFUND lot created; replay → still only 1 refund lot

## Contract Published for 04-05 / 04-06

### Extended ReservationResult contract

```java
record ReservationResult(
    List<UUID> lotIds,          // legacy — distinct lot IDs (back-compat)
    int reservedCount,          // total credits reserved
    List<LotAllocation> allocations  // NEW — per-lot counts, expiry-soonest-first
)

record LotAllocation(UUID lotId, int count)
// allocations.stream().mapToInt(LotAllocation::count).sum() == reservedCount
```

**Usage in 04-05 (CampaignService.executeSend):** Walk `allocations` to assign each recipient to the correct lot. Example: allocations=[{lotA,3},{lotB,3}] for 6 recipients — first 3 get lotA, next 3 get lotB. This lotId is embedded in each `MessageAccepted`/`MessageReleased`/`MessageRefundDue` event so wallet-service can route the per-lot delta.

### AMQP events wallet consumes (from messaging-service)

| Event | Exchange | Routing Key | Wallet Action |
|-------|----------|-------------|---------------|
| `MessageAccepted` | `messaging.events` | `messaging.MessageAccepted` | `consumeFromLot(userId, lotId)` |
| `MessageReleased` | `messaging.events` | `messaging.MessageReleased` | `releaseFromLot(userId, lotId)` |
| `MessageRefundDue` | `messaging.events` | `messaging.MessageRefundDue` | `creditBack(userId, creditsToRefund, messageId)` |

### New LotService methods

| Method | Signature | Ledger Effect |
|--------|-----------|---------------|
| `consumeFromLot` | `(UUID userId, UUID lotId) → void` | reserved--, consumed++, CONSUME txn |
| `releaseFromLot` | `(UUID userId, UUID lotId) → void` | reserved--, RELEASE txn |

## Deviations from Plan

None — plan executed exactly as written.

## TDD Gate Compliance

| Gate | Status | Commits |
|------|--------|---------|
| RED (Task 1) | PASSED | `a4d8f28` — test(04-07) ReservationAllocationTest + LotConsumeReleaseTest failing |
| GREEN (Task 1) | PASSED | `1eb528f` — feat(04-07) per-lot allocation + consume/release |
| RED (Task 2) | PASSED | `abfb5dd` — test(04-07) MessagingEventConsumerIT failing |
| GREEN (Task 2) | PASSED | `39d9064` — feat(04-07) MessagingEventConsumer implementation |

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-04-08 (duplicate CONSUME/RELEASE/REFUND events) | MITIGATED | `processedEventRepository.tryInsert(eventId)` on all three handlers; `MessagingEventConsumerIT` replay assertions confirm no double-effect |
| T-04-09 (concurrent reserve vs consume on same lot) | MITIGATED | `consumeFromLot`/`releaseFromLot` run `@Transactional`; `findById` loads the row with JPA managed entity; mutation is isolated per handler |

## Known Stubs

None.

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. The AMQP consumer binding to `messaging.events` is the only new trust boundary, and it is in scope per the plan's threat model (T-04-08, T-04-09).

## Self-Check: PASSED

- [x] `LotAllocation.java` exists at correct path; contains `lotId` and `count` components
- [x] `ReservationResult.java` has three components: `lotIds`, `reservedCount`, `allocations`
- [x] `ReservationService.java` builds `allocations` list in the take loop; returns three-arg constructor
- [x] `LotService.java` contains `consumeFromLot` and `releaseFromLot` methods
- [x] `MessagingEventConsumer.java` contains `tryInsert` in all three handlers
- [x] No `messaging.events` TopicExchange `@Bean` in `wallet-service/.../config/RabbitMqConfig.java`
- [x] No `com.opendesk.messaging` import in any wallet-service source file
- [x] No `javax.*` imports in any created file (all use `jakarta.*` where applicable)
- [x] `./gradlew :services:wallet-service:test` BUILD SUCCESSFUL (all tests GREEN, no Phase 3 regression)
- [x] RED commits (`a4d8f28`, `abfb5dd`) exist before GREEN commits (`1eb528f`, `39d9064`)
