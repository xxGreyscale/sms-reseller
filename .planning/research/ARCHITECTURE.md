# Architecture Research

**Domain:** Multi-tenant bulk SMS reseller platform (Tanzania)
**Researched:** 2026-06-18
**Confidence:** HIGH — architecture is locked; research validates patterns for each critical flow

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                    │
│  ┌──────────────────────────┐   ┌──────────────────────────┐            │
│  │  web (Next.js 14)        │   │  admin-panel (Next.js 14) │            │
│  │  Customer-facing UI      │   │  Internal ops UI          │            │
│  └────────────┬─────────────┘   └────────────┬──────────────┘            │
└───────────────┼───────────────────────────────┼────────────────────────-─┘
                │                               │
┌───────────────▼───────────────────────────────▼──────────────────────────┐
│                     INGRESS / GATEWAY LAYER                               │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Traefik (DOKS ingress)                                             │  │
│  │  JWT validation → forward X-User-Id, X-Tenant-Id claims            │  │
│  └──────────────────────────────┬─────────────────────────────────────┘  │
└─────────────────────────────────┼─────────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────────────┐
│                        SERVICE LAYER (Spring Boot 3 / Java 21)             │
│                                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ identity │  │  wallet  │  │ payment  │  │ catalog  │  │ contact  │  │
│  │          │  │          │  │          │  │          │  │          │  │
│  │ NIDA KYC │  │ ledger + │  │ Azampay  │  │ bundle   │  │contacts, │  │
│  │ sessions │  │ reserves │  │ webhooks │  │ pricing  │  │ groups,  │  │
│  │ JWT mint │  │ balances │  │ polling  │  │ catalog  │  │ CSV import│  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────────┘  └──────────┘  │
│       │             │             │                                        │
│  ┌────┴─────┐  ┌────┴──────────────────────────────────────────────────┐  │
│  │messaging │  │  notification                                          │  │
│  │          │  │                                                        │  │
│  │campaigns │  │ Consumes events from all services.                    │  │
│  │messages  │  │ Writes notification log. Sends via SMS/email/push.    │  │
│  │sender IDs│  └───────────────────────────────────────────────────────┘  │
│  │upstream  │                                                              │
│  │provider  │  ┌────────────────────────────────────────────────────────┐  │
│  └──────────┘  │  admin                                                  │  │
│                │  Cross-service read views, audit log, user management   │  │
│                └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────────────┐
│                          ASYNC LAYER                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  RabbitMQ (CloudAMQP managed)                                        │  │
│  │  Events: payment.confirmed, nida.verified, campaign.submitted,       │  │
│  │          message.dispatched, message.delivered, credit.low           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼─────────────────────────────────────────┐
│                        DATA + CACHE LAYER                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────────────────┐  │
│  │ PostgreSQL  │  │   Redis     │  │  External APIs                   │  │
│  │ (DO Managed │  │ (DO Managed)│  │  - NIDA identity API             │  │
│  │  per-service│  │ cache/locks/│  │  - Azampay payment gateway       │  │
│  │  logical DB)│  │  OTP store  │  │  - Upstream SMS provider         │  │
│  └─────────────┘  └─────────────┘  └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

| Component | Owns | Communicates with | Build order |
|-----------|------|-------------------|-------------|
| **identity** | Users, sessions, NIDA verification state, JWT issuance, OTPs | Redis (OTP), NIDA external API, publishes `nida.verified` event | **1st — blocks everything** |
| **catalog** | Bundle definitions, pricing, expiry rules | Consumed by payment, wallet | **2nd — data reference** |
| **wallet** | Append-only ledger entries, reservation holds, balance view, credit expiry | Consumes `payment.confirmed`, `nida.verified`; publishes `credit.low` | **3rd — core business invariant** |
| **payment** | Azampay order creation, webhook ingestion, polling recovery, idempotency table | Calls catalog (bundle price), publishes `payment.confirmed`; calls wallet to release hold | **4th — depends on catalog + wallet** |
| **contact** | Contacts, contact groups, suppressions, CSV import, dedup | Consumed by messaging | **3rd/4th (parallel with payment)** |
| **messaging** | Campaigns, message records, sender IDs, templates, upstream provider dispatch | Calls wallet (reserve credits), calls contact (recipient list); publishes `campaign.submitted`, `message.dispatched`, `message.delivered` | **5th — depends on wallet + contact** |
| **notification** | Notification log, fan-out to SMS/email/push | Subscribes to all domain events | **6th (parallel, pure consumer)** |
| **admin** | Admin accounts, audit log, cross-service read views, sender ID approval | Reads from all services via their APIs | **Last — reads from everything** |

---

## Critical Data Flows

### Flow 1: Credit Reservation → Send → Delivery Webhook

This is the most financially critical path in the system. Every step must be transactionally safe.

```
[User triggers campaign]
         │
         ▼
messaging-service: create Campaign record (status=DRAFT)
         │
         ▼
messaging-service: calculate total_messages (contact group size × recipients)
         │
         ▼
messaging-service → wallet-service (REST): POST /reservations
         │         { campaignId, userId, amount: total_messages }
         │
         ▼
wallet-service:
  BEGIN TRANSACTION
    SELECT balance FROM wallets WHERE user_id=? FOR UPDATE  ← PESSIMISTIC LOCK
    IF balance < amount → reject (HTTP 402)
    INSERT INTO ledger (user_id, type=RESERVATION, amount=-N, ref=campaignId)
    UPDATE wallet_view SET reserved = reserved + N
  COMMIT
         │
         ▼
messaging-service: Campaign status → QUEUED
         │
         ▼
messaging-service: publish to RabbitMQ exchange (campaign.submitted)
         │
         ▼
messaging-worker (same service, separate listener):
  For each recipient chunk:
    POST to upstream SMS provider API
    On 2xx: INSERT message record (status=DISPATCHED)
    On failure: NACK → DLQ
         │
         ▼
upstream SMS provider → delivery webhook callback:
  messaging-service receives delivery report
  INSERT/UPDATE message record (status=DELIVERED|FAILED)
  Publish message.delivered event
         │
         ▼
wallet-service (consumes message.delivered event):
  For DELIVERED:  close reservation (ledger entry type=DEBIT, final cost)
  For FAILED:     release reservation (ledger entry type=RESERVATION_RELEASE)
  → balance is never in an unknown state
```

### Flow 2: Payment → Credit Top-up

```
[User selects bundle and pays via Azampay]
         │
         ▼
payment-service: GET /catalog/{bundleId} → price, credit_amount
         │
         ▼
payment-service: POST to Azampay API → returns order_id, checkout_url
         │         INSERT payment_orders (order_id, status=PENDING, idempotency_key=UUID)
         ▼
[User pays on mobile money — Azampay sends webhook]
         │
         ▼
payment-service POST /webhooks/azampay:
  1. Look up idempotency_key in payment_orders — IF already CONFIRMED → 200 OK, return
  2. Validate Azampay signature
  3. BEGIN TRANSACTION
       UPDATE payment_orders SET status=CONFIRMED WHERE order_id=?
       INSERT outbox_events (event=payment.confirmed, payload=...)
     COMMIT
         │
         ▼ (outbox relay publishes to RabbitMQ)
wallet-service (consumes payment.confirmed):
  BEGIN TRANSACTION
    INSERT INTO ledger (user_id, type=CREDIT, amount=+N, source=payment, ref=orderId)
    UPDATE wallet_view SET available = available + N
    INSERT INTO expiry_schedule (credit_amount=N, expires_at=now+12months)
  COMMIT
         │
         ▼
notification-service (consumes payment.confirmed):
  INSERT notification record
  Send SMS/push to user: "Your account has been credited with N SMS credits"
```

### Flow 3: NIDA Verification → Free Credits

```
[User submits National ID number + date of birth]
         │
         ▼
identity-service:
  Check Redis: key nida:verified:{userId} → already verified? reject
  POST to NIDA API (external, slow, unreliable)
    → Circuit breaker + timeout (10s hard limit)
    → On timeout: status=PENDING, schedule retry
    → On NIDA error: status=FAILED, surface error to user
    → On success: status=VERIFIED
         │
         ▼
identity-service (on VERIFIED):
  BEGIN TRANSACTION
    UPDATE users SET nida_status=VERIFIED, nida_nin=hash(nin)
    INSERT outbox_events (event=nida.verified, payload={userId, bonus_credits: 50})
  COMMIT
  SET Redis key nida:verified:{userId} (prevents replay)
         │
         ▼
wallet-service (consumes nida.verified):
  BEGIN TRANSACTION
    INSERT INTO ledger (user_id, type=BONUS_CREDIT, amount=+50, source=nida_bonus)
    INSERT INTO expiry_schedule (credit_amount=50, expires_at=now+30days)
    UPDATE wallet_view SET available = available + 50
  COMMIT
```

---

## Architectural Patterns

### Pattern 1: Append-Only Ledger with Reservation

**What:** The wallet never stores a mutable balance field as the source of truth. Every credit event is an immutable INSERT into `ledger_entries`. The balance is the SUM of all entries. A separate denormalized `wallet_view` table caches available/reserved/total for read performance.

**When to use:** Any financial system where the audit trail is as important as the current balance — telecom credits, prepaid wallets, token systems.

**Critical implementation detail:** Use `SELECT ... FOR UPDATE` (pessimistic lock via `@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the wallet_view row when making a reservation. Optimistic locking (`@Version`) is insufficient here: if two campaign submissions compete, optimistic lock will cause one to retry, and the retry re-reads the now-locked balance — but with high concurrency you get a thundering herd of retries. For the MVP (1 replica, low concurrency), pessimistic lock on wallet_view is simpler and correct.

**Reservation lifecycle:**

```
RESERVATION created     → ledger: type=HOLD, amount=-N
Campaign DELIVERED      → ledger: type=DEBIT, amount=-(actual_cost - N) or +refund_delta
Campaign FAILED         → ledger: type=HOLD_RELEASE, amount=+N
Campaign PARTIAL        → ledger: type=PARTIAL_DEBIT + type=PARTIAL_RELEASE
```

**Why the reservation must precede dispatch:** If credits are only debited after the SMS is sent, a user with exactly 100 credits can trigger 3 concurrent campaigns totalling 300 credits. The reservation is the guard.

**Double-spend prevention checklist:**
- `SELECT ... FOR UPDATE` on wallet_view before writing any HOLD entry
- HOLD entry written in same transaction as lock acquisition
- Campaign cannot transition to QUEUED until reservation succeeds
- All ledger entries include `idempotency_key` (UUID generated by caller) — duplicate INSERT returns existing row, does not double-credit

### Pattern 2: Transactional Outbox for Reliable Event Publishing

**What:** Services never publish directly to RabbitMQ inside a business transaction. Instead, they INSERT an event into an `outbox_events` table within the same database transaction. A separate relay process (scheduled `@Scheduled` poller or CDC via Debezium) reads unprocessed outbox entries and publishes to RabbitMQ, then marks them processed.

**When to use:** Any time you need "publish this event if and only if this DB write committed." Without outbox: the DB write commits but the app crashes before the RabbitMQ publish — event is silently lost. This is the most common reliability failure in microservices.

**Implementation in Spring Boot 3:**

```java
// Inside @Transactional business method:
ledgerRepository.save(ledgerEntry);
outboxRepository.save(OutboxEvent.of("payment.confirmed", payload));
// Both writes in same transaction — atomic

// Separate @Scheduled(fixedDelay = 1000) relay:
List<OutboxEvent> pending = outboxRepo.findByPublishedFalse();
for (OutboxEvent e : pending) {
    rabbitTemplate.convertAndSend(e.getExchange(), e.getRoutingKey(), e.getPayload());
    e.setPublished(true);
    outboxRepo.save(e); // mark as published
}
```

**At-least-once implication:** The outbox produces at-least-once delivery. All consumers MUST be idempotent (see Pattern 4).

**Services that require outbox:** payment, identity, messaging, wallet (for credit.low alert). The notification and admin services are pure consumers — they do not publish business events.

### Pattern 3: Dead Letter Queue with Retry for SMS Dispatch

**What:** The messaging worker dispatches batches of SMS to the upstream provider. Transient failures (provider rate limits, network blips) should retry with backoff. Permanent failures (invalid number, blocked sender) must not block the queue. RabbitMQ's DLX + DLQ pattern separates these.

**Queue topology:**

```
Exchange: sms.dispatch (direct)
    │
    ├── Queue: sms.dispatch.work
    │      x-dead-letter-exchange: sms.dispatch.dlx
    │      x-dead-letter-routing-key: sms.dispatch.dead
    │      x-message-ttl: 30000 (30s per attempt)
    │
    └── Exchange: sms.dispatch.dlx (direct)
           │
           └── Queue: sms.dispatch.dead  ← manual inspection + replayed by admin
```

**Spring Boot configuration:**
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 2s
          multiplier: 2.0
          max-interval: 30s
        default-requeue-rejected: false  # exhausted retries → DLQ, not infinite loop
```

**Critical:** `default-requeue-rejected: false` — without this, any thrown exception causes RabbitMQ to requeue indefinitely, creating a poison-message loop. After retry exhaustion, `RejectAndDontRequeueRecoverer` sends the message to DLX automatically.

**Use Quorum Queues** for all production queues (not classic queues). Quorum queues are always durable and provide at-least-once guarantees even under broker restarts. Classic durable queues can lose messages during unclean shutdown.

**Publisher confirms** must be enabled on `RabbitTemplate` so the messaging service knows the message reached the broker before marking the campaign as QUEUED:
```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
```

### Pattern 4: Idempotent Event Consumer

**What:** Because the outbox + RabbitMQ combination delivers at-least-once, every consumer must be safe to receive the same event multiple times. The standard implementation uses a `processed_events` table per service with a unique constraint on `event_id`.

**Implementation:**

```java
@RabbitListener(queues = "payment.confirmed.wallet")
@Transactional
public void onPaymentConfirmed(PaymentConfirmedEvent event) {
    if (processedEventRepo.existsByEventId(event.getEventId())) {
        return; // already processed — idempotent return
    }
    // ... apply credit to ledger ...
    processedEventRepo.save(new ProcessedEvent(event.getEventId()));
}
```

**The `processed_events` table has a UNIQUE constraint on `event_id`.** If two concurrent threads receive the same event, the second INSERT will throw a `DataIntegrityViolationException` which the container will handle via the retry/DLQ path. Do NOT catch this exception and swallow it — let it propagate so RabbitMQ knows the message was not successfully processed.

**Apply this pattern to:** wallet consuming `payment.confirmed`, wallet consuming `nida.verified`, notification consuming any event, messaging consuming delivery webhook callbacks.

### Pattern 5: Idempotent Webhook Processing (Azampay)

**What:** Payment gateway webhooks are delivered with at-least-once semantics and may arrive out of order or multiple times. Idempotency is enforced at the HTTP handler level, not the business logic level.

**Implementation:**

```sql
CREATE TABLE payment_orders (
    id UUID PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,  -- Azampay's order reference
    idempotency_key UUID UNIQUE NOT NULL,  -- our internal key, sent in checkout request
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ...
);
```

```java
@PostMapping("/webhooks/azampay")
@Transactional
public ResponseEntity<Void> handleWebhook(@RequestBody AzampayWebhook payload) {
    PaymentOrder order = paymentOrderRepo.findByOrderId(payload.getOrderId())
        .orElseThrow(() -> new NotFoundException());
    if (order.getStatus() == PaymentStatus.CONFIRMED) {
        return ResponseEntity.ok().build();  // idempotent: already processed
    }
    // Validate HMAC signature from Azampay
    validateSignature(payload);
    // Update status + write outbox event — atomic
    order.setStatus(CONFIRMED);
    outboxRepo.save(OutboxEvent.of("payment.confirmed", ...));
    paymentOrderRepo.save(order);
    return ResponseEntity.ok().build();
}
```

**Missed webhook recovery (polling):** Azampay may fail to deliver the webhook if the service is down. The payment service must run a `@Scheduled` job that polls Azampay's transaction status API for all orders in `PENDING` state older than 5 minutes. This is the reconciliation path.

```java
@Scheduled(fixedDelay = 300_000) // every 5 minutes
public void reconcilePendingOrders() {
    List<PaymentOrder> stale = paymentOrderRepo
        .findByStatusAndCreatedAtBefore(PENDING, Instant.now().minus(5, MINUTES));
    for (PaymentOrder order : stale) {
        AzampayStatus status = azampayClient.checkStatus(order.getOrderId());
        if (status == SUCCESS) {
            confirmOrder(order); // same logic as webhook handler
        } else if (status == FAILED) {
            order.setStatus(FAILED);
            paymentOrderRepo.save(order);
        }
    }
}
```

### Pattern 6: External API Isolation (NIDA)

**What:** NIDA is an external government API with unknown SLAs, high latency, and potential for extended outages. The identity service must shield users from NIDA failures.

**Implementation requirements:**

1. **Timeout hard limit:** Never wait more than 10 seconds on a NIDA call. Configure `RestClient`/`WebClient` with `connectTimeout(3s)` + `readTimeout(10s)`.

2. **Async verification with status tracking:** NIDA calls run in a background thread. The HTTP response to the user is immediate: `{ status: "PENDING" }`. A webhook or polling endpoint lets the frontend poll for completion.

3. **Circuit breaker (Resilience4j):** After 5 consecutive NIDA timeouts, open the circuit for 60 seconds. Return a user-friendly error: "Identity verification is temporarily unavailable. We'll retry automatically."

4. **Idempotency on NIDA calls:** Store the NIDA NIN (hashed) in `users` when verification starts. If the user retries, check if a PENDING verification exists before calling NIDA again — do not call NIDA twice for the same person.

5. **Replay protection:** After successful verification, set a Redis key `nida:verified:{userId}` with no TTL. The wallet's `nida.verified` consumer checks `processed_events` before crediting the 50 bonus SMS — prevents re-crediting if the event is replayed.

```java
// Resilience4j circuit breaker on the NIDA client
@CircuitBreaker(name = "nida", fallbackMethod = "nidaFallback")
@TimeLimiter(name = "nida")
public CompletableFuture<NidaResult> verifyIdentity(String nin, String dob) {
    return CompletableFuture.supplyAsync(() -> nidaApiClient.verify(nin, dob));
}

public CompletableFuture<NidaResult> nidaFallback(String nin, String dob, Exception ex) {
    return CompletableFuture.completedFuture(NidaResult.unavailable());
}
```

### Pattern 7: Multi-Tenant Data Isolation

**What:** All services share the DOKS cluster but each service has its own logical PostgreSQL database. Within each service database, tenant isolation is enforced by `user_id` / `tenant_id` columns on every table, with JWT claims propagating the identity through the service layer.

**JWT claim propagation:**

1. Traefik validates the JWT signature against Identity service's public key.
2. Traefik forwards `X-User-Id` and `X-Roles` headers to the downstream service.
3. Each service reads these headers in a `SecurityContextFilter` — never trusts a `userId` in the request body.
4. All repository queries include `WHERE user_id = :currentUserId` from the security context.

**Why not a per-tenant schema approach:** With PostgreSQL's schema-per-tenant pattern you get strong isolation but lose the ability to do cross-tenant admin queries and make schema migrations complex. For this MVP with a single team, row-level isolation is correct. If the platform ever adds a "reseller of resellers" model, revisit.

**Critical:** The `admin` service is the only service that can query across `user_id` values. Admin service routes require `ROLE_ADMIN` in the JWT. No other service should expose cross-tenant query endpoints.

---

## Recommended Project Structure

```
sms-reseller/
├── services/
│   ├── identity/
│   │   └── src/main/java/tz/smsreseller/identity/
│   │       ├── api/          # REST controllers
│   │       ├── domain/       # Entities, repositories
│   │       ├── application/  # Use cases (service classes)
│   │       ├── infrastructure/
│   │       │   ├── nida/     # NIDA API client + circuit breaker
│   │       │   └── outbox/   # Outbox relay
│   │       └── events/       # Published event payloads
│   ├── wallet/
│   │   └── src/main/java/tz/smsreseller/wallet/
│   │       ├── api/
│   │       ├── domain/
│   │       │   ├── Ledger.java          # Append-only entry
│   │       │   ├── WalletView.java      # Denormalized balance
│   │       │   └── Reservation.java
│   │       ├── application/
│   │       └── events/       # Consumers: payment.confirmed, nida.verified
│   ├── payment/
│   │   └── src/main/java/tz/smsreseller/payment/
│   │       ├── api/
│   │       │   └── WebhookController.java  # Azampay webhooks
│   │       ├── domain/
│   │       │   └── PaymentOrder.java
│   │       ├── application/
│   │       │   └── ReconciliationJob.java  # @Scheduled polling
│   │       └── infrastructure/
│   │           └── azampay/   # Azampay client
│   ├── messaging/
│   │   └── src/main/java/tz/smsreseller/messaging/
│   │       ├── api/
│   │       ├── domain/
│   │       │   ├── Campaign.java
│   │       │   └── Message.java
│   │       ├── application/
│   │       │   └── CampaignDispatchWorker.java  # RabbitMQ consumer
│   │       └── infrastructure/
│   │           └── sms/       # Upstream SMS provider client
│   ├── catalog/
│   ├── contact/
│   ├── notification/
│   └── admin/
├── shared/
│   ├── shared-domain/     # Value objects: UserId, CreditAmount, etc.
│   ├── shared-events/     # Event schema: PaymentConfirmedEvent, etc.
│   ├── shared-security/   # JWT filter, SecurityContext utilities
│   └── shared-observability/  # OpenTelemetry config, MDC setup
├── frontend/
│   ├── web/
│   └── admin-panel/
└── k8s/
    ├── base/
    └── overlays/
        ├── dev/
        ├── staging/
        └── prod/
```

---

## Integration Points

### External Services

| Service | Integration Pattern | Critical Notes |
|---------|---------------------|----------------|
| NIDA API | REST (async + circuit breaker + timeout) | Async call only — never block HTTP thread; hashed NIN stored, never plain |
| Azampay | REST (checkout initiation) + webhook (callback) + polling (reconciliation) | Validate HMAC on every webhook; idempotency on `order_id`; polling job is safety net |
| Upstream SMS provider | REST (batch dispatch) | Publisher confirms before marking campaign QUEUED; delivery webhook → message status |
| CloudAMQP (RabbitMQ) | AMQP 0-9-1 via Spring AMQP | Quorum queues for all work queues; DLX on all work queues; publisher confirms on |

### Internal Service Communication

| Boundary | Pattern | Direction | Notes |
|----------|---------|-----------|-------|
| messaging → wallet | REST (sync) | Request/Response | Reserve credits before dispatch; wallet returns 402 if insufficient |
| payment → wallet | Async event (`payment.confirmed`) | Event | wallet is never called sync by payment |
| identity → wallet | Async event (`nida.verified`) | Event | 50 bonus credits on verification |
| messaging → contact | REST (sync, read-only) | Request/Response | Fetch recipient list for campaign |
| messaging → upstream provider | REST | Request/Response | Batch send; retry with backoff |
| upstream provider → messaging | Webhook (inbound) | Inbound callback | Delivery status updates |
| Azampay → payment | Webhook (inbound) | Inbound callback | Idempotency + signature check |
| all services → notification | Async events | Event fan-out | notification is pure consumer |
| admin → all services | REST (read-only) | Request/Response | Admin API calls internal service endpoints with ROLE_ADMIN |

---

## Build Order and Blocking Dependencies

The build order is not arbitrary — some services cannot function without others being deployed first.

```
Phase 1 — Foundation (nothing else can proceed without these):
  shared-domain, shared-events, shared-security, shared-observability
  identity (JWT issuance blocks everything — no authenticated calls without it)
  catalog  (bundle definitions needed by payment)

Phase 2 — Core financial services (wallet blocks all credit-related features):
  wallet   (credit reservation blocks messaging send)
  payment  (purchases require catalog + wallet events)

Phase 3 — Content and messaging (requires wallet + contact):
  contact  (recipient management — needed before campaigns can be created)
  messaging (campaigns require wallet reservation + contact list)

Phase 4 — Supporting services (pure consumers and read aggregators):
  notification (consumes events, no other service waits for it)
  admin        (cross-service reads, last to add)

Phase 5 — Frontends:
  web (customer UI — all backend services must exist)
  admin-panel (after admin service)
```

**What blocks what:**
- `identity` must be first — all other services validate JWTs; no authenticated endpoint works without it
- `wallet` must precede `messaging` — campaign dispatch requires a credit reservation call
- `catalog` must precede `payment` — payment needs to look up bundle price and credit amount
- `contact` must precede `messaging` — campaigns need a recipient list to dispatch
- `payment` events feed `wallet` — but wallet can be deployed before payment with event consumers standing by
- `notification` and `admin` are safe to deploy last; no other service has a runtime dependency on them

---

## Scaling Considerations

| Scale | Adjustment |
|-------|------------|
| MVP (0–500 users, 1M SMS/month) | 1 replica per service; single CloudAMQP instance; DO Managed Postgres smallest tier; Redis single-node |
| Growth (5K users, 10M SMS/month) | HPA on messaging workers (the dispatch workers are CPU-bound); Add read replicas for contact and admin services; Increase CloudAMQP instance |
| Scale (50K users, 100M SMS/month) | Partition campaign dispatch by shard (userId mod N); Redis cluster; Consider Debezium CDC instead of polling outbox relay; Wallet service may need its own connection pool scaling |

**First bottleneck:** The messaging dispatch worker — it is both I/O bound (upstream SMS provider) and volume-heavy. HPA on CPU + custom metrics (queue depth) is the right lever. This is why messaging workers are in a separate Kubernetes Deployment from the messaging HTTP API.

**Second bottleneck:** The wallet `SELECT ... FOR UPDATE` row lock on high-concurrency sends. At MVP this is fine. At scale, consider sharding wallet_view by user_id mod N across multiple rows and a compare-and-swap approach.

---

## Anti-Patterns

### Anti-Pattern 1: Synchronous Credit Deduction After SMS Dispatch

**What people do:** Send the SMS first, deduct credits after successful delivery confirmation.

**Why it's wrong:** A user with 100 credits can fire 10 concurrent 100-credit campaigns before any deduction completes. Results in -900 credit balance with no recovery path.

**Do this instead:** Reserve credits synchronously BEFORE dispatching. This is the entire point of the reservation pattern.

### Anti-Pattern 2: Publishing RabbitMQ Events Inside a DB Transaction

**What people do:** Call `rabbitTemplate.convertAndSend(...)` inside a `@Transactional` method, assuming the event will only be published if the transaction commits.

**Why it's wrong:** The RabbitMQ publish happens immediately when the line executes, before the transaction commits. If the transaction rolls back, the event is already in RabbitMQ and will be consumed, causing phantom credits, phantom deliveries, or phantom confirmations.

**Do this instead:** Use the transactional outbox pattern. Write the event to an `outbox_events` table inside the transaction. A relay thread publishes after commit.

### Anti-Pattern 3: Mutable Balance Field as Source of Truth

**What people do:** Store `available_credits INT` on the user record and decrement/increment it directly.

**Why it's wrong:** Under concurrent updates, UPDATE-based balance changes lose history, make auditing impossible, and are vulnerable to race conditions even with optimistic locking (retry storms).

**Do this instead:** Append-only ledger. Balance is always derived from SUM of ledger entries. Denormalized view for reads is fine — but the view must be kept consistent with the ledger.

### Anti-Pattern 4: Processing Webhooks Without Idempotency Guard

**What people do:** Process Azampay webhook → credit user → return 200. If Azampay retries, the user gets double credits.

**Why it's wrong:** All payment providers send webhooks with at-least-once delivery. Duplicate webhook = duplicate credit top-up = financial loss.

**Do this instead:** Check `payment_orders.status` on every webhook. If already CONFIRMED, return 200 immediately without re-processing.

### Anti-Pattern 5: Calling NIDA API Synchronously in HTTP Handler

**What people do:** User submits National ID, handler calls NIDA API, waits for response, returns result.

**Why it's wrong:** NIDA may be slow (5–30 seconds), causing HTTP client timeouts in the browser. User retries, creating duplicate NIDA calls. Under load, thread pool exhaustion shuts down the service.

**Do this instead:** Async verification. HTTP handler returns `{ status: "PENDING" }` immediately. Verification runs in background. Frontend polls a status endpoint. On NIDA timeout, retry with backoff (not by user action).

### Anti-Pattern 6: Using Classic Queues Instead of Quorum Queues

**What people do:** Declare RabbitMQ queues with `durable=true` using classic queue type.

**Why it's wrong:** Classic durable queues can lose messages during an unclean broker restart. CloudAMQP may restart brokers during maintenance.

**Do this instead:** Declare all work queues as quorum queues (`x-queue-type: quorum`). Quorum queues are always durable and provide at-least-once delivery under restarts.

---

## Sources

- RabbitMQ Dead Letter Exchange documentation: https://github.com/rabbitmq/rabbitmq-website/blob/main/versioned_docs/version-4.3/dlx.md (HIGH confidence — official)
- RabbitMQ At-Least-Once Dead Lettering (Quorum Queues): https://github.com/rabbitmq/rabbitmq-website/blob/main/blog/2022-03-29-at-least-once-dead-lettering/index.md (HIGH confidence — official)
- RabbitMQ Publisher Confirms: https://github.com/rabbitmq/rabbitmq-website/blob/main/tutorials/tutorial-seven-php.md (HIGH confidence — official)
- RabbitMQ Consumer Acknowledgements: https://github.com/rabbitmq/rabbitmq-website/blob/main/versioned_docs/version-4.3/confirms.md (HIGH confidence — official)
- Spring Boot AMQP Retry Configuration: https://docs.spring.io/spring-boot/3.5/reference/messaging/amqp.html (HIGH confidence — official)
- Spring Data JPA Locking: https://github.com/spring-projects/spring-data-jpa/blob/main/src/main/antora/modules/ROOT/pages/jpa/locking.adoc (HIGH confidence — official)
- Spring Boot Security / JWT Resource Server: https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html (HIGH confidence — official)
- Transactional outbox pattern (training knowledge, HIGH confidence — well-established pattern, widely documented)
- Reservation pattern for distributed credits (training knowledge, HIGH confidence — standard fintech pattern)

---

*Architecture research for: multi-tenant bulk SMS reseller platform (sms-reseller)*
*Researched: 2026-06-18*
