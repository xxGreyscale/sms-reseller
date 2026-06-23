---
phase: 05
plan: 06
subsystem: notification-service
tags: [notification, amqp, consumers, idempotency, feed-api, push-channel, tdd, NOTF-01, NOTF-02, NOTF-03, NOTF-04, NOTF-05, NOTF-06]
dependency_graph:
  requires: [05-01, 05-02]
  provides:
    - notification-service: 4 passive idempotent AMQP consumers (6 routing keys)
    - In-app notification log (notifications table)
    - JWT-scoped feed API GET /api/v1/notifications
    - NotificationChannel interface + StubPushChannel (@Profile stub)
    - Flyway V1 (processed_events) + V2 (notifications) migrations
  affects:
    - 05-07 and later plans (notification log is queryable for admin panels)
    - Phase 6 (real FCM implementation via NotificationChannel seam)
tech_stack:
  added:
    - ProcessedEvent/ProcessedEventRepository: idempotency gate pattern (ON CONFLICT DO NOTHING)
    - Jackson2JsonMessageConverter in RabbitMqConfig (consumer-only, no exchange declaration)
    - JwtTestHelper + TestKeys in notification-service test (shared RSA keypair, same as other services)
  patterns:
    - Passive @QueueBinding with ignoreDeclarationExceptions="true" on all 4 upstream exchanges
    - Events.java local records mirror upstream payloads (service-boundary isolation)
    - @Profile("stub") StubPushChannel records pushes in-memory for test assertions
    - JwtAuthenticationToken method injection in controller (no SecurityContextHolder thread-local)
key_files:
  created:
    - services/notification-service/src/main/java/com/smsreseller/notification/config/RabbitMqConfig.java
    - services/notification-service/src/main/java/com/smsreseller/notification/config/SecurityConfig.java
    - services/notification-service/src/main/java/com/smsreseller/notification/idempotency/ProcessedEvent.java
    - services/notification-service/src/main/java/com/smsreseller/notification/idempotency/ProcessedEventRepository.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/Notification.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationType.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationRepository.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationService.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationController.java
    - services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationDto.java
    - services/notification-service/src/main/java/com/smsreseller/notification/push/NotificationChannel.java
    - services/notification-service/src/main/java/com/smsreseller/notification/push/StubPushChannel.java
    - services/notification-service/src/main/java/com/smsreseller/notification/consumer/Events.java
    - services/notification-service/src/main/java/com/smsreseller/notification/consumer/IdentityEventConsumer.java
    - services/notification-service/src/main/java/com/smsreseller/notification/consumer/PaymentEventConsumer.java
    - services/notification-service/src/main/java/com/smsreseller/notification/consumer/WalletEventConsumer.java
    - services/notification-service/src/main/java/com/smsreseller/notification/consumer/MessagingEventConsumer.java
    - services/notification-service/src/main/resources/db/migration/V1__create_processed_events.sql
    - services/notification-service/src/main/resources/db/migration/V2__create_notifications.sql
    - services/notification-service/src/test/java/com/smsreseller/notification/TestKeys.java
    - services/notification-service/src/test/java/com/smsreseller/notification/JwtTestHelper.java
  modified:
    - services/notification-service/src/main/java/com/smsreseller/notification/NotificationServiceApplication.java (added @EnableJpaAuditing)
    - services/notification-service/src/test/java/com/smsreseller/notification/notification/NotificationFeedIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/UserVerifiedConsumerIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/PaymentConfirmedConsumerIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/LowCreditAlertConsumerIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/ExpiryWarningConsumerIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/CampaignCompletedConsumerIT.java (RED → GREEN)
    - services/notification-service/src/test/java/com/smsreseller/notification/consumer/SenderIdDecidedConsumerIT.java (RED → GREEN)
decisions:
  - "Events.java local records duplicate upstream event shapes — service boundary isolation: no cross-service class imports"
  - "WalletEventConsumer uses two separate @RabbitListener methods on two queue names (notification.wallet.LowCreditAlert + notification.wallet.ExpiryWarning) instead of a shared queue — simpler and avoids @RabbitListeners container annotation complexity"
  - "RabbitMqConfig in notification-service declares only Jackson2JsonMessageConverter (no exchange bean) — notification-service is a pure consumer, declaring upstream exchanges would violate single-owner topology"
  - "JwtTestHelper created in test scope (same RSA keypair pattern established in Phase 2) rather than exposing JwtEncoder as a Spring bean — avoids Spring Security bootstrap issues with encoder/decoder on same keypair"
  - "ignoreDeclarationExceptions='true' on all 4 @Exchange annotations — T-05-15 mitigation, prevents PRECONDITION_FAILED if exchange topology differs"
metrics:
  duration: "25m"
  completed: "2026-06-22"
  tasks: 2
  files: 29
---

# Phase 05 Plan 06: Notification-Service Core Summary

**One-liner:** Four passive idempotent AMQP consumers fan-out 6 upstream events into an in-app notification log with JWT-scoped feed API and a stubbed push channel seam.

---

## What Was Built

### Task 1 (TDD GREEN): Notification Log + Feed API + Push Channel Seam

**Idempotency gate:**
- `ProcessedEvent` entity + `ProcessedEventRepository.tryInsert()` — `INSERT ... ON CONFLICT DO NOTHING` (T-05-14)
- `V1__create_processed_events.sql` — `processed_events` table (PK=event_id)

**Notification log:**
- `Notification` entity: `id` (UUID), `userId`, `type` (enum), `title`, `body`, `payload` (JSONB), `read` (boolean default false), `createdAt` (@CreatedDate via JPA auditing)
- `NotificationType` enum: `NIDA_VERIFIED`, `PAYMENT_CONFIRMED`, `LOW_CREDIT`, `EXPIRY_WARNING`, `CAMPAIGN_COMPLETED`, `SENDER_ID_DECIDED`
- `NotificationRepository`: `findByUserIdOrderByCreatedAtDesc` (paged), `findByUserId` (list for ITs), `countByUserIdAndReadFalse`
- `V2__create_notifications.sql` — `notifications` table + `idx_notifications_user_created(user_id, created_at DESC)` index

**Feed API:**
- `GET /api/v1/notifications?page=0&size=20` — JWT-scoped via `JwtAuthenticationToken.getToken().getSubject()` (T-05-16 IDOR prevention)
- Response: Spring `Page<NotificationDto>` (id, userId, type, title, body, payload, read, createdAt)
- Size clamped to max 100

**Push channel seam:**
- `NotificationChannel` interface: `push(Notification)` — the seam for Phase 6 FCM
- `StubPushChannel`: `@Profile("stub")` — records pushes in-memory, no real FCM dep

**Security:**
- `SecurityConfig`: CSRF disabled, STATELESS, anyRequest authenticated (no admin paths in notification-service)
- `jwtAuthenticationConverter()` reads `roles` claim → `SimpleGrantedAuthority`

### Task 2 (TDD GREEN): 4 Passive Idempotent Consumers

**Consumer → Exchange / Routing Key → NotificationType mapping:**

| Consumer | Queue | Exchange | Routing Key | NotificationType |
|----------|-------|----------|-------------|-----------------|
| `IdentityEventConsumer` | notification.identity.UserVerified | identity.events | identity.UserVerified | NIDA_VERIFIED |
| `PaymentEventConsumer` | notification.payment.PaymentConfirmed | payment.events | payment.PaymentConfirmed | PAYMENT_CONFIRMED |
| `WalletEventConsumer` | notification.wallet.LowCreditAlert | wallet.events | wallet.LowCreditAlert | LOW_CREDIT |
| `WalletEventConsumer` | notification.wallet.ExpiryWarning | wallet.events | wallet.ExpiryWarning | EXPIRY_WARNING |
| `MessagingEventConsumer` | notification.messaging.CampaignCompleted | messaging.events | messaging.CampaignCompleted | CAMPAIGN_COMPLETED |
| `MessagingEventConsumer` | notification.messaging.SenderIdDecided | messaging.events | messaging.SenderIdDecided | SENDER_ID_DECIDED |

**Per-consumer pattern:**
1. `@RabbitListener` with `@QueueBinding` / `@Exchange(ignoreDeclarationExceptions="true")` (T-05-15)
2. `@Transactional` on handler method
3. `processedEventRepository.tryInsert(eventId)` — return early on duplicate (T-05-14)
4. `notificationService.create(...)` — persist notification row
5. `notificationChannel.push(...)` — stub invoked, no sync HTTP

**CampaignCompleted payload** from 05-02: `{eventId, campaignId, userId, totalCount, deliveredCount, failedCount}`

### TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED | 19cfb08 | `test(05-06): RED — 7 real failing assertions` |
| GREEN | df4d2fb | `feat(05-06): GREEN — notification-service core` |
| REFACTOR | — | No structural cleanup needed |

---

## Feed API Contract (for admin-web / future Flutter)

```
GET /api/v1/notifications?page=0&size=20
Authorization: Bearer <JWT>

200 OK
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "type": "NIDA_VERIFIED",
      "title": "Identity Verified",
      "body": "Your identity has been successfully verified.",
      "payload": null,
      "read": false,
      "createdAt": "2026-06-22T00:00:00Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 20
}

401 Unauthorized (no/invalid JWT)
```

---

## Deviations from Plan

None — plan executed exactly as written.

---

## Known Stubs

- `StubPushChannel` is intentional: real FCM integration deferred to Phase 6. No data is lost — all notifications are persisted to the `notifications` table. The `NotificationChannel` seam means Phase 6 can add a `FcmPushChannel` (@Profile("production")) without changing any consumer code.

---

## Threat Flags

No new threat surface beyond what the plan's threat model covers (T-05-14, T-05-15, T-05-16 all mitigated).

---

## Self-Check

| Check | Result |
|-------|--------|
| ProcessedEventRepository.java exists | PASSED |
| Notification.java exists | PASSED |
| NotificationController.java exists | PASSED |
| NotificationChannel.java + StubPushChannel.java exist | PASSED |
| V1__create_processed_events.sql exists | PASSED |
| V2__create_notifications.sql contains CREATE TABLE notifications | PASSED |
| V2 contains idx_notifications_user_created index | PASSED |
| 4 consumer classes exist (Identity, Payment, Wallet, Messaging) | PASSED |
| ignoreDeclarationExceptions present in all 4 @Exchange annotations (grep count >= 6) | PASSED |
| RED commit 19cfb08 exists | PASSED |
| GREEN commit df4d2fb exists | PASSED |
| ./gradlew :services:notification-service:test — all 9 tests GREEN | PASSED |

## Self-Check: PASSED
