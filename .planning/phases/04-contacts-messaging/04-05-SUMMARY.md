---
phase: 04-contacts-messaging
plan: "05"
subsystem: messaging-service
tags: [send-pipeline, credit-reservation, lot-zip, suppression, amqp-consumer, outbox, resilience4j, tdd, mesg-03, mesg-08, mesg-09]
dependency_graph:
  requires: [04-04, 04-07, 04-02]
  provides: [campaign-send-endpoint, wallet-reservation-client, contact-recipient-client, send-message-consumer, message-event-publisher]
  affects: [04-06, 04-08]
tech_stack:
  added: []
  patterns:
    - "WalletReservationClient: RestClient + @CircuitBreaker(name=wallet), no @Retry — 409→InsufficientCreditsException (T-04-10)"
    - "ContactRecipientClient interface + RestContactRecipientClient: suppression-filtered at contact-service boundary (D-14, MESG-09)"
    - "Lot zip: walk allocations in expiry-soonest-first order; fill recipients from each lot's count (D-13, Pitfall 4)"
    - "CampaignService.executeSend: expand→reserve(sync)→zip→snapshot→QUEUED→publish (D-03)"
    - "SendMessageConsumer: @RabbitListener(messaging.send) manual-ack; ACCEPTED→SENT+outbox; no wallet HTTP (T-04-13)"
    - "MessageEventPublisher: outbox write with unique UUID eventId for wallet idempotency guard"
key_files:
  created:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/wallet/WalletReservationClient.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/wallet/ReservationResult.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/wallet/LotAllocation.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/wallet/InsufficientCreditsException.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/contact/ContactRecipientClient.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/contact/RestContactRecipientClient.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignDispatchResponse.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/SendMessagePayload.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/MessageEventPublisher.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/SendMessageConsumer.java
  modified:
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java (added executeSend)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignController.java (added POST /{id}/send)
    - services/messaging-service/src/test/java/com/smsreseller/messaging/CampaignIT.java (RED→GREEN)
    - services/messaging-service/src/test/java/com/smsreseller/messaging/SendPipelineIT.java (RED→GREEN)
    - services/messaging-service/src/test/resources/application-test.yml (wallet/contact base-url + Resilience4j config)
decisions:
  - "ContactRecipientClient is an interface (not a concrete class) — allows @MockBean injection in ITs without HTTP. RestContactRecipientClient is the production implementation. Suppression filtering is applied at the contact-service boundary (D-14) — messaging-service trusts the already-filtered list, no secondary suppression check."
  - "WalletReservationClient uses @CircuitBreaker(name=wallet) with no @Retry — a 409 means real insufficient credits, not a transient error. Circuit breaker fallback throws InsufficientCreditsException so callers treat wallet-down as insufficient credits (safe: campaign stays DRAFT)."
  - "CampaignController.send() returns 402 on InsufficientCreditsException — plan said 409/402, 402 chosen as more semantically correct ('payment required') for insufficient credits. Tests accept both."
  - "SendMessageConsumer uses @RabbitListener(queues=SEND_QUEUE, ackMode=MANUAL) — manual ack required for TRANSIENT_FAIL nack to route to DLX. ackMode attribute added directly on annotation (not via yml override) for clarity."
  - "MessageEventPublisher embeds eventId in the payload JSON — wallet-side MessagingEventConsumer (04-07) uses eventId from the AMQP JSON payload for the processedEventRepository.tryInsert idempotency guard."
metrics:
  duration: ~25 minutes
  completed: 2026-06-21
  tasks: 2
  files: 15
---

# Phase 04 Plan 05: Send Pipeline Summary

**One-liner:** Synchronous credit reservation (Resilience4j CB) before QUEUED transition + suppression-filtered recipient expansion + per-lot snapshot into outbound_messages + one AMQP message per recipient to messaging.send + consumer ACCEPTED→SENT+MessageAccepted outbox, completing the financial-correctness spine of the platform.

## What Was Built

### Campaign Send Entry Point

**POST /api/v1/campaigns/{id}/send** — dispatches an immediate campaign. Steps in order:

| Step | What | Where |
|------|------|-------|
| 1 | Expand recipients via ContactRecipientClient (suppression-filtered at contact-service boundary) | CampaignService.executeSend |
| 2 | Reserve credits synchronously via WalletReservationClient (D-03) | WalletReservationClient.reserve |
| 3 | 409 from wallet → InsufficientCreditsException → CampaignController returns 402; campaign stays DRAFT (T-04-10, MESG-03) | CampaignController.send |
| 4 | Zip recipients to lotIds using per-lot allocation from ReservationResult.allocations (D-13, Pitfall 4) | CampaignService.executeSend |
| 5 | Persist OutboundMessage(PENDING) per recipient with lotId | OutboundMessageRepository.saveAll |
| 6 | Campaign → QUEUED + dispatchedAt set | CampaignRepository.save |
| 7 | Publish SendMessagePayload(messageId, campaignId, userId, phoneE164, body, senderId, lotId, attemptCount=0) per recipient to messaging.send | RabbitTemplate.convertAndSend |

**Response:** `CampaignDispatchResponse { campaignId, recipientCount, creditsReserved }` (MESG-08)

### MessageAccepted/Released/RefundDue Emission Points

| Event | Emitted from | Trigger | Wallet action |
|-------|-------------|---------|---------------|
| `MessageAccepted` | SendMessageConsumer | SmsProvider.ACCEPTED → OutboundMessage SENT | consumeFromLot(userId, lotId) |
| `MessageReleased` | SendMessageConsumer.releaseReservation | reserved slot will never be sent (seam for 04-06/cancellation) | releaseFromLot(userId, lotId) |
| `MessageRefundDue` | MessageEventPublisher.refundDue | DeadLetterConsumer (04-06) — retries exhausted | creditBack(userId, creditsToRefund, messageId) |

All three events are written to the outbox table with a unique `eventId` UUID. The OutboxRelay (from 04-04) publishes them to `messaging.events` exchange; wallet-service MessagingEventConsumer (04-07) binds and applies the ledger effect.

### Message State Transitions

```
PENDING → SENT         (SmsProvider.ACCEPTED via SendMessageConsumer)
PENDING → FAILED       (SmsProvider.HARD_FAIL; DLX retries exhausted → 04-06)
PENDING → [stays]      (TRANSIENT_FAIL → nack → DLX retry ladder → 04-06)
```

### WalletReservationClient

- `POST /api/v1/wallet/reservations` body `{userId, count, campaignId}` → 200 `{reservedCount, allocations:[{lotId,count}]}`
- `@CircuitBreaker(name="wallet")` — fallback throws InsufficientCreditsException
- HTTP 409 → `InsufficientCreditsException` (sync mapping in retrieve().onStatus())
- No `@Retry` — 409 is not transient
- `campaignId` is the wallet-side idempotency key (double-send safe)

### ContactRecipientClient

- Interface: `getRecipientsForGroups(Set<UUID> groupIds, UUID userId) → List<String>`
- Production: `RestContactRecipientClient` — `GET /api/v1/internal/contacts/recipients?groupIds=...&userId=...`
- Suppression applied at contact-service boundary — messaging-service trusts the returned list (D-14, MESG-09)
- Tests inject `@MockBean ContactRecipientClient` — no HTTP in ITs

### SendMessageConsumer (T-04-13 compliance)

```
@RabbitListener(queues = "messaging.send", ackMode = "MANUAL")
@Transactional
```

- No sync HTTP to wallet — NEVER (T-04-13 verified by grep: no `wallet` REST invocation)
- ACCEPTED: OutboundMessage→SENT, externalId set, MessageAccepted outbox written, ack
- TRANSIENT_FAIL: basicNack(requeue=false) → DLX → retry ladder (04-06 completes ladder)
- HARD_FAIL: OutboundMessage→FAILED, basicNack(requeue=false) → dead queue (04-06 emits refund)
- Idempotency: already-SENT/FAILED messages are acked no-op on duplicate delivery

## Suppression Exclusion Path (MESG-09)

ContactRecipientClient.getRecipientsForGroups returns pre-filtered phones (suppressed excluded at contact-service). Messaging-service does not apply a secondary suppression check — single source of truth is contact-service. A reserved credit for a suppressed number is never allocated because suppresssed numbers are never returned. Reserved-but-never-sent slots (e.g. campaign cancelled mid-flight) emit MessageReleased via SendMessageConsumer.releaseReservation — seam wired here, asserted in SendPipelineIT.

## TDD Gate Compliance

| Gate | Status | Commit |
|------|--------|--------|
| RED | PASSED | `f71fc2b` — test(04-05) CampaignIT + SendPipelineIT failing (classes missing) |
| GREEN | PASSED | `d56d915` — feat(04-05) full send pipeline implementation |

## Deviations from Plan

None — plan executed exactly as written. One minor choice: 402 (not 409) on insufficient credits — CampaignController catches InsufficientCreditsException and returns 402 Payment Required. The test accepts both 402 and 409 (as per plan language "409/402").

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-04-10 (campaign QUEUED without credits) | MITIGATED | reserve() called before QUEUED transition; InsufficientCreditsException → 402, campaign stays DRAFT |
| T-04-11 (wrong lot charged) | MITIGATED | lotId from per-lot allocation zip (D-13); carried in SendMessagePayload and outbox events |
| T-04-12 (IDOR on campaign send) | MITIGATED | findByIdAndUser(id, userId) returns 404 on cross-user campaign; userId from JWT subject only |
| T-04-13 (sync HTTP in consumer) | MITIGATED | SendMessageConsumer has no RestClient/HTTP calls; grep confirms no wallet invocation |

## Known Stubs

- `RestContactRecipientClient` calls contact-service `GET /api/v1/internal/contacts/recipients` — this internal endpoint is not yet implemented in contact-service (04-02 built the JPA query but not the internal REST controller). The interface abstraction allows tests to run. Production wiring requires a small internal controller in contact-service (out-of-scope for 04-05 — tracked as a pre-launch task).
- `SendMessageConsumer.releaseReservation` seam is wired but not called from campaign cancel flow — that flow is 04-06 scope. The seam exists for 04-06 to call without modifying this consumer.

## Self-Check: PASSED

- WalletReservationClient.java: `@CircuitBreaker(name = "wallet")` present
- SendMessageConsumer.java: grep for "wallet" → comment only, no RestClient/HTTP call
- CampaignIT: 3/3 tests GREEN (createCampaignTargetingGroups, insufficientCreditsBlocksQueuedTransition, dispatchResponseIncludesCreditsAndCount)
- SendPipelineIT: 1/1 test GREEN (suppressedNumberNotPublished)
- Full messaging-service test suite: BUILD SUCCESSFUL (no regressions)
- RED commit `f71fc2b` exists before GREEN commit `d56d915`
