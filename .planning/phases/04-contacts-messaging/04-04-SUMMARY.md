---
phase: 04
plan: 04
subsystem: messaging-service
tags: [entities, migrations, jpa, rabbitmq, quorum-queue, dlx, sms-encoder, gsm7, ucs2, outbox, security, tdd]
dependency_graph:
  requires: [04-01]
  provides: [messaging-service-foundation, campaign-entities, outbound-message-entities, sms-encoder, sms-provider-stub, quorum-dlx-topology, outbox-infra, security-config]
  affects: [04-05, 04-06, 04-07, 04-08]
tech_stack:
  added: []
  patterns:
    - Transactional outbox (copy of identity-service pattern) — messaging.events exchange
    - @ElementCollection for campaign_groups join table (groupIds on Campaign entity)
    - QueueBuilder.quorum().deliveryLimit(3) for messaging.send and messaging.dead
    - Classic TTL-ladder retry queues (externalized TTL; 2s/4s/6s in test profile)
    - Mock-first @Profile triple: SmsProvider interface + StubSmsProvider(@Profile stub) + RealSmsProvider placeholder
    - ConcurrentHashMap DLR simulation sweep in StubSmsProvider with settable handler seam
key_files:
  created:
    - services/messaging-service/src/main/resources/db/migration/V1__create_campaigns.sql
    - services/messaging-service/src/main/resources/db/migration/V2__create_outbound_messages.sql
    - services/messaging-service/src/main/resources/db/migration/V3__create_sender_id_requests.sql
    - services/messaging-service/src/main/resources/db/migration/V4__create_outbox.sql
    - services/messaging-service/src/main/java/com/smsreseller/messaging/config/SecurityConfig.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/config/RabbitMqConfig.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/Campaign.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignStatus.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignRepository.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignController.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CreateCampaignRequest.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignResponse.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/OutboundMessage.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/MessageStatus.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/message/OutboundMessageRepository.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdRequest.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdStatus.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/outbox/OutboxEntry.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/outbox/OutboxRepository.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/outbox/OutboxRelay.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/sms/SmsEncoding.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/sms/SmsEncoder.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/sms/SmsProvider.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/sms/SmsResult.java
    - services/messaging-service/src/main/java/com/smsreseller/messaging/sms/StubSmsProvider.java
  modified:
    - services/messaging-service/src/test/java/com/smsreseller/messaging/SmsEncoderTest.java
    - services/messaging-service/src/test/java/com/smsreseller/messaging/CampaignIT.java
decisions:
  - RabbitMqConfig.EXCHANGE="messaging.events" — consumed by OutboxRelay, and 04-05/06 publishers; wallet-service binds passively in 04-07
  - RabbitMqConfig.SEND_QUEUE="messaging.send" with deliveryLimit(3) — D-06 quorum queue with 3-attempt cap
  - Retry queues classic (not quorum) — OQ#4: TTL universally available on classic queues; only send+dead need quorum durability
  - TTL externalized via app.messaging.retry.ttl-* — test profile sets 2s/4s/6s (vs 60s/300s/900s prod)
  - campaign_groups stored as ElementCollection (no FK to contact-service) — cross-service reference managed at application layer
  - StubSmsProvider.deliveryReceiptHandler is a settable BiConsumer seam — 04-06 wires a real handler without modifying stub
  - CampaignController added (not in plan file) to satisfy CampaignIT.createCampaignTargetingGroups GREEN (Rule 2: required for correctness)
metrics:
  duration: ~25 minutes
  completed: 2026-06-21
  tasks: 2
  files: 29
---

# Phase 04 Plan 04: Messaging Service Foundation Summary

**One-liner:** Campaign/OutboundMessage/SenderIdRequest JPA entities + 4 Flyway migrations + quorum send queue with deliveryLimit(3) DLX TTL-ladder + SmsEncoder GSM-7/UCS-2 part counter + StubSmsProvider mock-first triple + transactional outbox relay on messaging.events.

## What Was Built

### Schema (4 Flyway migrations)

| Migration | Table | Key Columns |
|-----------|-------|-------------|
| V1__create_campaigns.sql | campaigns | id, user_id, body, sender_id, status DEFAULT 'DRAFT', scheduled_at, dispatched_at |
| V1__create_campaigns.sql | campaign_groups | campaign_id FK, group_id UUID (no cross-service FK — application-level join) |
| V2__create_outbound_messages.sql | outbound_messages | id, campaign_id FK, user_id, phone_e164, **lot_id UUID NOT NULL**, status DEFAULT 'PENDING', external_id |
| V3__create_sender_id_requests.sql | sender_id_requests | id, user_id, sender_name VARCHAR(11), status, reject_reason, decided_at |
| V4__create_outbox.sql | outbox | id, event_id UNIQUE, aggregate_type, event_type, payload TEXT, sent BOOLEAN, partial unsent index |

### State Machine Enums

- **CampaignStatus**: DRAFT | SCHEDULED | QUEUED | SENDING | COMPLETED | CANCELLED
- **MessageStatus**: PENDING | SENT | DELIVERED | FAILED
- **SenderIdStatus**: REQUESTED | APPROVED | REJECTED

### RabbitMQ Topology (RabbitMqConfig.java)

| Resource | Type | Key Properties |
|----------|------|----------------|
| `messaging.events` | TopicExchange | durable=true — outbox events, AMQP publish events |
| `messaging.retry.dlx` | DirectExchange | durable=true — routes nacked messages to retry ladder |
| `messaging.send` | quorum queue | deliveryLimit(3), DLX=messaging.retry.dlx, routingKey=messaging.retry.1m |
| `messaging.retry.1m` | classic queue | TTL=60000ms (2s in test), DLX back to messaging.send |
| `messaging.retry.5m` | classic queue | TTL=300000ms (4s in test), DLX back to messaging.send |
| `messaging.retry.15m` | classic queue | TTL=900000ms (6s in test), DLX back to messaging.send |
| `messaging.dead` | quorum queue | Bound to DLX with routingKey=messaging.dead; DeadLetterConsumer (04-06) listens here |

### SmsProvider Interface Triple (D-12)

- **SmsProvider** interface: `SmsResult send(phoneE164, body, senderId)`
- **SmsResult** record: `Outcome{ACCEPTED, HARD_FAIL, TRANSIENT_FAIL}` + externalId + static factories
- **StubSmsProvider** `@Profile("stub")`: magic suffix 0001→HARD_FAIL, 0002→TRANSIENT_FAIL, else ACCEPTED; ConcurrentHashMap DLR simulation; settable `BiConsumer<String,String> deliveryReceiptHandler` seam for 04-06

### SmsEncoder (MESG-02)

- `detect(text)` → GSM7 if all chars in GSM-7 basic+extended set; UCS2 otherwise
- `charCount(text, enc)` → UCS2=text.length(); GSM7=1 per basic char, 2 per extended char ({,},[,],~,\,|,€,^)
- `partCount(charCount, enc)` → 1 if ≤160(GSM7)/70(UCS2); ceil(charCount/153|67) for multipart

### Outbox Infrastructure

OutboxEntry, OutboxRepository, OutboxRelay — copy of identity-service pattern with exchange `messaging.events` and routing key prefix `messaging.`. Publishes SenderIdDecided (04-08); 04-05/06 add MessageAccepted/RefundDue entries.

### Security Config

- `/actuator/health/**`, `/error` → permitAll
- `/api/v1/internal/**` → hasRole("ADMIN") (T-04-05: sender-ID approval bypass prevention)
- All other requests → authenticated (JWT resource server)

## TDD Gate Compliance

- RED commit: `37f344c` — SmsEncoderTest + CampaignIT.createCampaignTargetingGroups failing (classes missing)
- GREEN commit: `482b3f0` (Task 1) + `2aed25a` (Task 2) — all assertions pass

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 37f344c | test | RED — real assertions for SmsEncoderTest and CampaignIT create |
| 482b3f0 | feat | Task 1 — entities + migrations + SecurityConfig + outbox infra |
| 2aed25a | feat | Task 2 — SmsEncoder + RabbitMqConfig quorum/DLX + SmsProvider stub |

## Deviations from Plan

### Auto-added Critical Functionality

**1. [Rule 2 - Missing Critical] CampaignController + CampaignService + DTOs added**
- **Found during:** GREEN phase for CampaignIT.createCampaignTargetingGroups
- **Issue:** Plan listed CampaignRepository but not CampaignController/Service/DTOs. Without a REST endpoint, the IT cannot post to /api/v1/campaigns.
- **Fix:** Added CampaignController (POST /api/v1/campaigns → 201), CampaignService.create(), CreateCampaignRequest record, CampaignResponse record.
- **Files added:** CampaignController.java, CampaignService.java, CreateCampaignRequest.java, CampaignResponse.java
- **Commit:** 482b3f0

## Known Stubs

- `StubSmsProvider.deliveryReceiptHandler` is a no-op BiConsumer until 04-06 wires the real handler. DLR callback fires correctly; handler is a logged no-op.
- `CampaignService.executeSend()` (04-05) not yet implemented — send pipeline is 04-05 scope.

## Threat Flags

None beyond the plan's threat model. All three T-04-05/06/07 mitigations are in place:
- T-04-05: SecurityConfig `/api/v1/internal/**` hasRole("ADMIN") applied
- T-04-06: quorum queue + deliveryLimit(3) + default-requeue-rejected=false (04-01 yml)
- T-04-07: .quorum() explicit on messaging.send and messaging.dead; retry queues classic-with-TTL

## Self-Check: PASSED

- V2 lot_id: `grep -c lot_id V2__create_outbound_messages.sql` = 2 (column def + comment)
- SecurityConfig /api/v1/internal/**: FOUND
- CampaignStatus DRAFT: FOUND in CampaignStatus.java
- RabbitMqConfig quorum count: 11 occurrences (send queue, dead queue, class name, constants, Javadoc)
- deliveryLimit(3): FOUND in RabbitMqConfig.sendQueue()
- SmsEncoderTest: BUILD SUCCESSFUL
- CampaignIT: BUILD SUCCESSFUL (createCampaignTargetingGroups passed; other two aborted as expected)
- Commits 37f344c, 482b3f0, 2aed25a: verified in git log
