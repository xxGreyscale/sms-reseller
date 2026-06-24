---
phase: 04-contacts-messaging
plan: "06"
subsystem: messaging-service
tags: [dlx-retry, dead-letter, refund, delivery-tracking, campaign-aggregate, tdd, mesg-06, mesg-07, mesg-10]
dependency_graph:
  requires: [04-04, 04-05]
  provides: [dead-letter-consumer, delivery-receipt-service, dlr-webhook, campaign-aggregate-status, per-message-status]
  affects: [04-07, 04-08]
tech_stack:
  added: []
  patterns:
    - "SendMessageConsumer Approach A: explicit republish+ack (not nack-based DLX) — HARD_FAIL routes to dead queue immediately; TRANSIENT_FAIL advances through 1m→5m→15m ladder by incrementing attemptCount on payload; exhausted retries route to dead queue (T-04-14)"
    - "DeadLetterConsumer: @RabbitListener(messaging.dead) @Transactional — sets FAILED + writes MessageRefundDue outbox; single emit site (T-04-15)"
    - "DeliveryReceiptService: @Transactional handleDeliveryReceipt(externalId, status) — SENT→DELIVERED/FAILED; checkCampaignCompletion → COMPLETED when all terminal"
    - "SmsProviderConfig @Profile(stub) ApplicationRunner: wires StubSmsProvider.deliveryReceiptHandler seam to DeliveryReceiptService (D-12 seam closed)"
    - "CampaignResponse: aggregate counts (totalCount/sentCount/deliveredCount/failedCount) in single-campaign GET; zero counts in list endpoint"
key_files:
  created:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/DeadLetterConsumer.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/DeliveryReceiptService.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/DlrController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/MessageView.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/config/SmsProviderConfig.java
  modified:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/SendMessageConsumer.java (Approach A ladder)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/OutboundMessageRepository.java (findByExternalId, findByEventType)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignResponse.java (aggregate count fields)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignController.java (GET /{id}/messages, aggregate counts)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java (toCampaignResponseWithCounts, getMessages)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/config/SecurityConfig.java (permit /api/v1/messaging/dlr)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/outbox/OutboxRepository.java (findByEventType)
    - services/messaging-service/src/test/java/com/smsreseller/messaging/DlxRetryIT.java (RED→GREEN)
    - services/messaging-service/src/test/java/com/smsreseller/messaging/DeliveryTrackingIT.java (RED→GREEN)
    - services/messaging-service/src/test/java/com/smsreseller/messaging/CampaignStatusIT.java (RED→GREEN)
decisions:
  - "Approach A (explicit republish+ack) over nack-based DLX: the nack path routes all failures to messaging.retry.1m (first retry queue) — even HARD_FAIL. Approach A gives explicit routing: HARD_FAIL → messaging.dead immediately (no TTL wait), TRANSIENT_FAIL → correct rung of the ladder. This resolves an idempotency gap where HARD_FAIL marked FAILED would ack (no-op) on subsequent retry-queue re-deliveries and never reach the dead queue."
  - "SmsProviderConfig as ApplicationRunner (not constructor injection): avoids circular dependency between StubSmsProvider and DeliveryReceiptService. The handler is set after both beans are fully initialized."
  - "DLR webhook POST /api/v1/messaging/dlr is permit-all in SecurityConfig: real DLR callbacks from SMS providers do not carry JWTs. T-04-16 (Spoofing) accepted at MVP — signature verification deferred to production provider onboarding."
  - "findByEventType added to OutboxRepository: OutboxRelay marks entries sent=true quickly; cross-test isolation in DlxRetryIT requires querying all rows (not just unsent). The test filters by payload messageId to avoid cross-test contamination from prior ITs' dead-queue residue."
  - "CampaignResponse aggregate counts are 0 in list endpoints (cheap) and computed from DB in single-campaign GET (acceptable N+1 at MVP scale)."
metrics:
  duration: ~40 minutes
  completed: 2026-06-21
  tasks: 2
  files: 13
---

# Phase 04 Plan 06: DLX Retry + Refund + Delivery Tracking Summary

**One-liner:** Approach A DLX ladder (explicit republish to retry queues, HARD_FAIL direct to dead queue) + DeadLetterConsumer (FAILED + MessageRefundDue) + DeliveryReceiptService (stub DLR SENT→DELIVERED/FAILED + campaign COMPLETED) + per-message status and aggregate campaign counts APIs, closing the credit invariant and delivery visibility.

## What Was Built

### DLX Retry Ladder — Approach A (SendMessageConsumer revised)

The original `SendMessageConsumer` used `basicNack(requeue=false)` which relies on the broker's DLX routing. This approach has a critical flaw: even HARD_FAIL is routed to the first retry queue (`messaging.retry.1m`) because the `sendQueue` has `deadLetterRoutingKey=messaging.retry.1m`. A HARD_FAIL message would bounce through all three retry queues (waiting 2+4+6=12s in test) before reaching the dead queue.

**Approach A fix:** Explicit `amqpTemplate.convertAndSend(DLX, routingKey, payload)` + `ack`:

| Outcome | Behavior |
|---------|----------|
| ACCEPTED | message→SENT, MessageAccepted outbox, ack |
| TRANSIENT_FAIL (attempt 0) | republish to `messaging.retry.1m` with attemptCount=1, ack |
| TRANSIENT_FAIL (attempt 1) | republish to `messaging.retry.5m` with attemptCount=2, ack |
| TRANSIENT_FAIL (attempt 2 = max) | publish to `messaging.dead` with attemptCount=3, ack |
| HARD_FAIL | publish directly to `messaging.dead`, ack (no TTL wait) |

MAX_ATTEMPTS = 3 matches the `deliveryLimit(3)` on the send quorum queue.

### DeadLetterConsumer (MESG-10)

```java
@RabbitListener(queues = "messaging.dead")
@Transactional
public void onDeadLetter(SendMessagePayload payload) {
    // set OutboundMessage → FAILED
    // messageEventPublisher.refundDue(messageId, userId, lotId, 1)
}
```

- Single emit site for `MessageRefundDue` (T-04-15)
- Idempotent: if message already FAILED, still emits refund (wallet dedup via eventId prevents double-credit)

### DeliveryReceiptService (MESG-07)

```java
@Transactional
public void handleDeliveryReceipt(String externalId, String dlrStatus) {
    // find by externalId → OutboundMessage
    // if SENT: → DELIVERED or FAILED per dlrStatus
    // checkCampaignCompletion(campaignId)
}
```

Two call sites:
1. `SmsProviderConfig` ApplicationRunner wires `StubSmsProvider.deliveryReceiptHandler` → real handler (closed seam from 04-04)
2. `DlrController` POST `/api/v1/messaging/dlr` → stub webhook for future real provider

### Campaign Aggregate Status (MESG-06)

`CampaignService.toCampaignResponseWithCounts` runs `OutboundMessageRepository.countByStatusForCampaign` and populates `CampaignResponse.totalCount/sentCount/deliveredCount/failedCount`.

Campaign `COMPLETED` transition: `DeliveryReceiptService.checkCampaignCompletion` checks if all messages are DELIVERED or FAILED — if so, sets campaign COMPLETED.

### New Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/campaigns/{id}` | JWT | Campaign detail + aggregate counts (MESG-06) |
| GET | `/api/v1/campaigns/{id}/messages` | JWT | Per-message status (MESG-07, IDOR-safe) |
| POST | `/api/v1/messaging/dlr` | none | DLR webhook stub (T-04-16 accepted) |

## TDD Gate Compliance

| Gate | Status | Commit |
|------|--------|--------|
| RED | PASSED | `c53ae9b` — DlxRetryIT + DeliveryTrackingIT + CampaignStatusIT failing (DeadLetterConsumer/DeliveryReceiptService missing) |
| GREEN | PASSED | `93b0d50` — all three ITs pass; full 30-test suite green |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Approach A instead of nack-based DLX routing**
- **Found during:** GREEN phase when DlxRetryIT showed HARD_FAIL taking 12s instead of immediate
- **Issue:** The `sendQueue` has `deadLetterRoutingKey=messaging.retry.1m`, so nacking sends ALL failures (including HARD_FAIL) to the first retry queue first. Even with the idempotency guard acking no-op, HARD_FAIL would reach dead queue only after traversing all 3 TTL ramps.
- **Fix:** Replaced `basicNack` with Approach A: explicit `amqpTemplate.convertAndSend(DLX, routingKey, payload)` + `basicAck`. HARD_FAIL routes directly to `messaging.dead`; TRANSIENT_FAIL advances via ladder rung based on `attemptCount`.
- **Files modified:** SendMessageConsumer.java
- **Commit:** 93b0d50

**2. [Rule 2 - Missing] findByExternalId + findByEventType on OutboundMessageRepository/OutboxRepository**
- **Found during:** DeliveryReceiptService (needs findByExternalId to match DLR to message) + DlxRetryIT cross-test isolation (needs findByEventType to query all outbox entries, not just unsent)
- **Fix:** Added both query methods
- **Commit:** 93b0d50

## Known Stubs

- `DlrController` POST `/api/v1/messaging/dlr` accepts any JSON with `externalId` + `status` — no signature verification (T-04-16 accepted at MVP). Real provider DLR format adapter deferred to production provider onboarding.
- `StubSmsProvider` DLR fires "DELIVERED" for all accepted messages — no way to simulate a DLR FAILED outcome without changing the stub. DLR FAILED test uses HARD_FAIL → dead queue path instead of DLR FAILED path.

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-04-14 (DoS: infinite retry loop) | MITIGATED | Approach A: MAX_ATTEMPTS=3 check before republish; exhausted → dead queue; no infinite loop possible |
| T-04-15 (double refund) | MITIGATED | DeadLetterConsumer is single emit site; each refund entry has unique UUID eventId; wallet-side processedEventRepository dedup (04-07) |
| T-04-16 (DLR webhook spoofing) | ACCEPTED (MVP) | Stub webhook with no signature check; manual row in 04-VALIDATION for pre-launch real-provider wiring |

## Self-Check: PASSED

- DeadLetterConsumer.java: `messaging.dead` annotation FOUND, `refundDue` call FOUND
- DeliveryReceiptService.java: `handleDeliveryReceipt` FOUND
- SmsProviderConfig.java: `setDeliveryReceiptHandler` FOUND
- DlxRetryIT: `permanentFailureEmitsRefundDueEvent` GREEN
- DeliveryTrackingIT: `perMessageStatusTransitions` GREEN
- CampaignStatusIT: `aggregateStatusCountsAreCorrect` GREEN
- Full 30-test suite: BUILD SUCCESSFUL (two separate --rerun runs)
- RED commit `c53ae9b` exists before GREEN commit `93b0d50`
