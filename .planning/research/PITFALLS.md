# Pitfalls Research

**Domain:** Bulk SMS reseller platform — prepaid credit wallet, mobile money payments, NIDA KYC, microservices
**Researched:** 2026-06-18
**Confidence:** HIGH (technical pitfalls verified against Spring Boot 3.5, RabbitMQ, and Kubernetes official documentation; Tanzania-specific pitfalls from domain knowledge)

---

## Critical Pitfalls

### Pitfall 1: Credit Double-Spend via Race Condition on Concurrent Sends

**What goes wrong:**
Two concurrent campaign dispatches from the same user both read the wallet balance, both see sufficient credits, both deduct, and the wallet goes negative. This is the canonical lost-update problem. At 1 replica per service this risk is lower, but HPA scale-up and retry storms can still produce concurrent writes to the same wallet row.

**Why it happens:**
`SELECT` balance → application-layer check → `UPDATE balance = balance - N` is not atomic. Any gap between the read and the write allows another transaction to interleave. Developers often believe `@Transactional` alone prevents this — it does not. Transaction isolation prevents dirty reads but not this form of lost update unless the lock is held during the SELECT.

**How to avoid:**
Use a **reservation pattern** (the architecture already mandates this). Never debit directly. Flow:
1. `SELECT ... FOR UPDATE` on the wallet row via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository method.
2. Credit check inside the same transaction.
3. Insert a `PENDING` reservation row with the amount.
4. Return reservation ID.
5. On campaign completion (or SMS dispatch confirmation), convert reservation to a `DEBIT` ledger entry and release.
6. On failure, insert a `VOID` entry to cancel the reservation.

The wallet service must only compute available balance as `(confirmed credits) - (pending reservations)`. The append-only ledger is the source of truth — never store a mutable balance column.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM WalletLedger w WHERE w.userId = :userId")
Optional<WalletLedger> findByUserIdForUpdate(@Param("userId") UUID userId);
```

**Warning signs:**
- Wallet balance goes negative in production logs.
- Two campaign dispatch records referencing the same credit block.
- `SELECT balance` in service layer code outside a `FOR UPDATE` context.
- Missing `PENDING` reservation state — ledger only has `CREDIT` and `DEBIT`.

**Phase to address:** Wallet service implementation (Phase: Core Platform / Credit Ledger)

---

### Pitfall 2: Azampay Callback Credited Twice (Payment Idempotency Failure)

**What goes wrong:**
Azampay sends a POST callback to your `/payment/callback` endpoint. Your server processes it but returns a 500 before Azampay receives the response (network timeout, pod restart, GC pause). Azampay retries the callback. Your service processes it a second time, inserting a second `CREDIT` ledger entry. The user receives double credits. This is a direct financial loss.

**Why it happens:**
Developers treat the callback endpoint as a simple "insert payment + add credits" handler with no deduplication. The assumption is that Azampay only calls once. In practice all payment gateways retry on non-2xx or network failure, and Tanzania mobile money STK push flows have higher latency than card payments, increasing the window for timeout-then-retry.

**How to avoid:**
The payment service must enforce idempotency on every callback:
1. Extract the Azampay `transactionId` (or equivalent unique reference) from the callback payload.
2. Attempt an `INSERT INTO payments (transaction_id, ...) ON CONFLICT (transaction_id) DO NOTHING` — or use a unique constraint on `transaction_id`.
3. Check the affected row count. If 0, the payment was already processed — return `200 OK` immediately without publishing any event.
4. Only if the INSERT succeeded (row count = 1), publish `PaymentConfirmed` event to RabbitMQ.
5. The wallet service consumes `PaymentConfirmed` and inserts the `CREDIT` ledger entry — also with its own idempotency check on the payment event ID.

The double-layer idempotency (payment service + wallet consumer) is required because the RabbitMQ publish after the DB insert is not atomic. See Pitfall 6 on the outbox pattern.

**Warning signs:**
- No `UNIQUE` constraint on `payments.transaction_id` in schema.
- Callback handler performs credit top-up in the same HTTP request handler without an idempotency check.
- No test for "what happens if callback is called twice with the same transaction_id."
- Wallet balance unexpectedly higher than purchased bundles.

**Phase to address:** Payment service implementation (Phase: Payments / Azampay Integration)

---

### Pitfall 3: RabbitMQ Message Loss on Service Restart

**What goes wrong:**
A message is published to RabbitMQ. The consuming service (e.g., wallet, notification) receives it and starts processing. The pod is OOMKilled or rolled out mid-processing. The message is nacked or the channel closes without an ack. Classic queues lose unacknowledged messages on broker restart if queues are not durable or messages are not persistent. Even with durable queues, if the consumer acks before its own DB write completes, the operation is silently lost.

**Why it happens:**
Three independent failure modes are often conflated:
- Queue declared as non-durable (lost on broker restart).
- Message published without `deliveryMode=2` (persistent) — lost on broker restart even in durable queue.
- Consumer acks the message before its own DB write commits — "at-most-once" instead of "at-least-once."

**How to avoid:**
1. Declare all queues and exchanges as `durable=true` in `RabbitAdmin` bean definitions.
2. Publish all messages with `MessageProperties.PERSISTENT_TEXT_PLAIN` or `deliveryMode=2`.
3. Use **quorum queues** for all business-critical queues (wallet credits, payment events, campaign status). Quorum queues guarantee confirmed messages are not lost unless a majority of nodes are permanently lost — superior to classic durable queues. CloudAMQP supports quorum queues.
4. Use **manual acknowledgements** on all consumers (`spring.rabbitmq.listener.simple.acknowledge-mode=manual`). Ack only after DB write commits.
5. Enable **publisher confirms** on the publishing side to detect broker-side failures.
6. Configure a **Dead Letter Exchange (DLX)** for all business queues. Set `x-dead-letter-exchange` on queue declaration. Messages that exceed retry count route to a DLQ for manual inspection.

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        default-requeue-rejected: false  # failed messages go to DLQ, not infinite requeue
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
          multiplier: 2
          max-interval: 10000ms
```

**Warning signs:**
- `acknowledge-mode: auto` (the default) in consumer configuration.
- Queues declared without `durable: true`.
- No Dead Letter Exchange configured on business queues.
- Service restart causes wallet credit or campaign status events to silently disappear.
- `default-requeue-rejected: true` causing infinite retry loop on poison messages.

**Phase to address:** Shared infrastructure setup / RabbitMQ topology definition (Phase: Infrastructure Bootstrap)

---

### Pitfall 4: Transactional Outbox Gap — DB Committed, Event Not Published

**What goes wrong:**
The payment service inserts a confirmed payment row in PostgreSQL (`@Transactional` commits), then attempts to `rabbitTemplate.convertAndSend(...)`. Before the publish call completes, the pod dies (OOMKill, deployment rollout, SIGTERM). The DB row exists as `CONFIRMED` but the `PaymentConfirmed` event is never published. The wallet service never credits the user. The user paid but has no SMS credits. This is a silent data inconsistency.

**Why it happens:**
Developers treat the DB commit + MQ publish as a single logical unit when they are two separate I/O operations with no atomic guarantee between them. `@Transactional` governs the DB transaction. It has no knowledge of RabbitMQ. If the application crashes between DB commit and MQ publish, the event is lost.

**How to avoid:**
Implement the **Transactional Outbox Pattern**:
1. Within the same DB transaction that writes the payment record, also insert a row into an `outbox_events` table (`event_type`, `payload`, `published_at=NULL`, `created_at`).
2. A separate scheduled job (or Debezium CDC if CDC is later added) polls `outbox_events WHERE published_at IS NULL`, publishes to RabbitMQ with publisher confirms, then marks `published_at = now()`.
3. On confirmed publish, mark the outbox row as published.
4. If publish fails, the row remains unpublished and the poller retries on the next cycle.

For MVP without CDC, a simple scheduled `@Scheduled(fixedDelay = 5000)` relay in the payment service is sufficient. The poller must also be idempotent: consumers must handle duplicate events (publisher confirms guarantee at-least-once, not exactly-once). This connects to Pitfall 2's idempotency requirement.

**Warning signs:**
- `rabbitTemplate.convertAndSend(...)` called inside or immediately after an `@Transactional` method with no outbox table.
- No `outbox_events` table in the payment service schema.
- No test simulating "service crashes between DB write and MQ publish."
- User reports "I paid but no SMS credits appeared."

**Phase to address:** Payment service implementation (Phase: Payments / Azampay Integration)

---

### Pitfall 5: NIDA API Unavailability Blocking User Registration

**What goes wrong:**
NIDA's verification API is slow (2–10 second response times are common for Tanzanian government APIs) or temporarily unavailable. If the registration flow performs a synchronous NIDA call inside the HTTP request handler, users see a spinning loader, requests time out, and some registrations never complete. Worse: if the verification result is not persisted before the timeout, the user re-submits, and the same NIN is looked up multiple times unnecessarily (and NIDA may rate-limit or charge per lookup).

**Why it happens:**
The simplest implementation is synchronous: user submits NIN → call NIDA → return result. This works in demos but fails in production when NIDA is slow. Tanzania government API SLAs are not published. Developers underestimate the failure rate of external dependencies.

**How to avoid:**
1. **Decouple verification from registration.** Registration creates a user in `PENDING_VERIFICATION` state immediately and returns success to the user. NIDA verification is dispatched as an async job.
2. **Cache NIDA results.** Once a NIN is successfully verified, store the result (name, DOB, photo hash) in the identity service DB. Never call NIDA twice for the same NIN.
3. **Set explicit timeouts on NIDA HTTP client.** `connectTimeout=5s`, `readTimeout=15s`. Fail fast rather than holding a thread.
4. **Implement retry with exponential backoff** for transient NIDA failures. Use Resilience4j `@Retry` + `@CircuitBreaker` on the NIDA client bean.
5. **Surface a clear status UI.** Users see "Verification in progress — you will be notified" rather than a spinning indefinite loader.
6. **Validate NIN format client-side** before hitting the API to avoid wasted calls on malformed input. Tanzania NIN format is well-defined (20 alphanumeric chars).

**Warning signs:**
- NIDA call is synchronous inside a REST controller or `@Transactional` service method with no timeout.
- No `PENDING_VERIFICATION` state in the user entity.
- No circuit breaker around the NIDA HTTP client.
- Registration endpoint p99 latency > 5s.
- Free 50 SMS granted before NIDA confirmation — a user could claim free credits without completing verification.

**Phase to address:** Identity service / NIDA integration (Phase: Identity & Onboarding)

---

### Pitfall 6: JVM OOMKill from Ignoring Non-Heap Memory in Kubernetes Limits

**What goes wrong:**
A container memory limit of `512Mi` is set. The JVM is configured with `-Xmx512m`. The container is OOMKilled repeatedly. This happens because the Kubernetes limit governs the total process RSS (resident set size), which includes heap + Metaspace + CodeHeap + direct buffers + thread stacks + native libraries. Setting `-Xmx` equal to the container limit leaves no room for non-heap memory, which for a Spring Boot 3 + Java 21 application typically consumes 100–200 MB.

**Why it happens:**
Developers set `-Xmx` to match the container limit because "the JVM should use what it's given." They forget that JVM memory consumption is `heap + non-heap`. For Spring Boot apps with many beans, Metaspace alone can exceed 80–120 MB.

**How to avoid:**
Use `-XX:MaxRAMPercentage=75` instead of a fixed `-Xmx`. This flag (available since Java 10, well-supported in Java 21) sets the heap as a percentage of container-visible RAM. Java 21 respects `UseContainerSupport=true` (enabled by default) so it correctly reads the cgroup memory limit set by Kubernetes.

Rule of thumb for Spring Boot 3 microservices on this stack:
- Container limit: `768Mi`–`1Gi` per service
- JVM flags: `-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport`
- At 768Mi limit, heap gets ~576Mi; non-heap (~192Mi) fits comfortably

For the wallet, payment, and messaging services (highest memory risk), start at `1Gi` and reduce after observing `jvm.memory.*` metrics in Grafana.

**Warning signs:**
- `kubectl describe pod <name>` shows `Reason: OOMKilled` and `Exit Code: 137`.
- Container memory limit set equal to or less than `-Xmx` value.
- Service restarts repeatedly during load testing or after deploying new campaign batches.
- `jvm.memory.used{area=nonheap}` in Prometheus approaching container limit minus `-Xmx`.

**Phase to address:** Kubernetes deployment manifests (Phase: Infrastructure Bootstrap / Kustomize Overlays)

---

### Pitfall 7: Readiness Probe Fails During Slow Spring Boot Startup

**What goes wrong:**
Spring Boot 3 with 8 services, each loading Hibernate, Flyway migrations, RabbitMQ connection pools, and Spring Security context takes 15–30 seconds to start in a container (longer on first startup in a new cluster with cold JVM). Kubernetes default readiness probe starts checking at `initialDelaySeconds=0` with `failureThreshold=3` and `periodSeconds=10`. The pod is killed and restarted before it ever becomes healthy. This creates a crash loop that blocks deployments.

**Why it happens:**
Developers copy probe configurations from examples that assume fast startup (native images, simple apps). Spring Boot enterprise services with Flyway + multiple datasources + RabbitMQ + Spring Security startup time is 3–5x longer than a minimal app.

**How to avoid:**
Use a `startupProbe` to separate the "is the app done booting" question from "is the app alive" question. Spring Boot's Actuator exposes `/actuator/health/liveness` and `/actuator/health/readiness` as distinct endpoints when `management.endpoint.health.probes.enabled=true`.

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30      # allows up to 30 * 10s = 5 minutes to start
  periodSeconds: 10

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  periodSeconds: 5
```

Also configure `spring.lifecycle.timeout-per-shutdown-phase=30s` and a `preStop: sleep: seconds: 10` hook to prevent traffic from hitting a pod that has started shutting down.

**Warning signs:**
- Pods cycling in `CrashLoopBackOff` during deployments.
- `kubectl logs` shows the app started fine but Kubernetes killed it anyway.
- No `startupProbe` in deployment manifests — only `livenessProbe` and `readinessProbe`.
- `initialDelaySeconds` hardcoded as a guess rather than measured.

**Phase to address:** Kubernetes deployment manifests (Phase: Infrastructure Bootstrap / Kustomize Overlays)

---

### Pitfall 8: Mobile Money STK Push Timeout Confusion

**What goes wrong:**
User initiates an SMS bundle purchase. Azampay sends an STK push (USSD prompt) to the user's phone. The user does not respond within Azampay's timeout window (typically 60–120 seconds). The user's browser is still showing "Processing payment..." indefinitely. The user either:
(a) Pays again thinking the first attempt failed → double payment, double credits (see Pitfall 2).
(b) Abandons the purchase → lost conversion.
(c) Calls support confused about whether they were charged.

Mobile money STK push is fundamentally asynchronous. Azampay calls back your endpoint when the user acts (approve or cancel) or when the timeout expires. The callback may arrive 30 seconds or 3 minutes after initiation.

**Why it happens:**
Developers build payment flow assuming synchronous response ("send request → wait → get result"). STK push is event-driven. The initial push request returns a reference ID immediately. The actual result arrives via callback. Many implementations poll on the server side or hold an HTTP connection open, neither of which scales.

**How to avoid:**
1. **Immediately** after Azampay confirms the STK push was sent (not the payment), return a response to the frontend: "USSD prompt sent to your phone. Please approve within 2 minutes."
2. Store the payment in `PENDING_STK` state with the Azampay reference and a timestamp.
3. Frontend polls `/payment/{id}/status` with a 5-second interval, showing a countdown timer (2 minutes is a reasonable UX timeout).
4. When Azampay callback arrives (success/failure), update payment state and publish event.
5. Frontend receives status update on next poll. If success → show credits added. If timeout/failure → show "Payment was not completed. Try again."
6. On the backend, a scheduled job marks `PENDING_STK` payments older than 5 minutes as `EXPIRED` (defensive, in case Azampay never calls back).
7. Implement Azampay polling recovery: for `PENDING_STK` payments 2+ minutes old, proactively call Azampay's status-check API to confirm the transaction state.

**Warning signs:**
- Frontend shows a spinner with no timeout or countdown.
- No `PENDING_STK` / `EXPIRED` states in the payment state machine.
- No Azampay status-poll recovery job.
- No idempotency on the "try again" button (user can pay twice for the same order).

**Phase to address:** Payment service + frontend payment flow (Phase: Payments / Azampay Integration)

---

### Pitfall 9: Upstream SMS Provider Charging for Failed Sends (No Deduplication)

**What goes wrong:**
A campaign dispatches 1,000 SMS. The messaging service sends batches to the upstream provider. A batch of 100 times out (network issue). The messaging service retries the entire batch. The provider received the first attempt but the response was lost in transit. The user is charged (in upstream credits) for 1,100 sends. The upstream provider shows 1,100 sent; your ledger shows 1,000 deducted. Discrepancy accumulates over time, eroding your margin.

Alternatively: the upstream provider's delivery receipt (DLR) callback is slow or missing. Your system has no way to distinguish "sent successfully, DLR pending" from "failed, should retry."

**Why it happens:**
HTTP retries at the application layer treat the upstream provider as idempotent when it may not be. Delivery status tracking is bolted on after the core send path is working, instead of being designed in from the start.

**How to avoid:**
1. **Generate a unique message reference per send attempt** (UUID), pass it to the upstream provider as their `clientRef` or equivalent parameter. If the provider supports idempotency keys, always use them.
2. **Never retry with the same reference ID.** Generate a new reference for each retry attempt. This lets you query the provider: "did reference X succeed?" before retrying.
3. **Design the messaging service state machine from day one:**
   - `QUEUED` → `SUBMITTED` (sent to provider) → `DELIVERED` / `FAILED` / `EXPIRED`
   - `SUBMITTED` messages with no DLR after N minutes should be queried against the provider status API before being retried.
4. **Deduct credits at `SUBMITTED` state, not at `DELIVERED`.** Credits are consumed when we make a billable API call to the upstream provider, regardless of delivery outcome (the upstream charges for submission).
5. Confirm upstream provider's retry and idempotency semantics during provider onboarding — this is one of the Pre-Implementation Blockers in PROJECT.md.

**Warning signs:**
- No `clientRef` / external reference tracked per message send.
- Retry logic retransmits the entire batch payload without checking which messages already succeeded.
- Credits deducted only on delivery receipt — leaves gap between submission and DLR.
- No DLR timeout handler — messages stuck in `SUBMITTED` forever.

**Phase to address:** Messaging service / upstream provider integration (Phase: Messaging / Campaign Dispatch)

---

### Pitfall 10: Flyway Migration Lock Deadlock on Multi-Instance Startup

**What goes wrong:**
Two service pods start simultaneously (during a rolling update or HPA scale-up). Both attempt to run Flyway migrations on startup. Flyway uses a lock in the `flyway_schema_history` table. With PostgreSQL, Flyway uses an advisory lock. If both pods acquire the lock concurrently, one proceeds with migration while the other waits. If the waiting pod has a shorter `startupProbe` failure threshold than the migration duration, Kubernetes kills the waiting pod mid-migration. In some cases, this leaves migrations in a partially applied state.

**Why it happens:**
At 1 replica per service in MVP this is low risk, but HPA and rolling updates with `maxSurge=1` can produce exactly this scenario. Developers assume Flyway is inherently safe in Kubernetes because it uses locks — it is, but only if your probe configuration gives it enough time.

**How to avoid:**
1. Set `spring.flyway.postgresql.transactional-lock=true` (available via `FlywayProperties.Postgresql`) to use PostgreSQL transactional advisory locks which are more reliable than session-level locks.
2. Set Flyway lock retry count: `spring.flyway.lock-retry-count=50` (default is 50; confirm it's sufficient for your migration duration).
3. Size the `startupProbe.failureThreshold` to cover migration time + application startup time. 30 × 10s = 5 minutes is a safe upper bound.
4. Alternatively, run Flyway migrations as a Kubernetes **init container** or **Job** that completes before the main service pod starts. This fully eliminates the concurrent migration problem and is the recommended pattern for production.

**Warning signs:**
- `FlywayException: Unable to acquire Flyway advisory lock` in logs.
- Pods failing health checks during rolling updates.
- Deployment gets stuck with one pod in `CrashLoopBackOff` and one `Running`.

**Phase to address:** Database schema setup / Kubernetes manifests (Phase: Infrastructure Bootstrap)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Mutable balance column in wallet instead of append-only ledger | Simpler queries, less storage | Impossible to audit, race conditions, no history | Never — ledger is the core differentiator |
| Synchronous NIDA call in registration handler | Less code | Registration timeouts, poor UX, retry storms on NIDA outages | Never in production; fine for local dev mocking |
| Auto-ack RabbitMQ consumers | Less boilerplate | Silent message loss on pod restart | Never for financial events; acceptable for notification service only if notifications are best-effort |
| Skipping the outbox pattern in payment service | Simpler code | Silent credit loss on crash between DB commit and MQ publish | Never for payment confirmation events |
| Fixed `-Xmx` instead of `-XX:MaxRAMPercentage` | Predictable JVM | OOMKill when Metaspace grows | Never on Kubernetes — always use MaxRAMPercentage |
| Classic RabbitMQ queues instead of quorum queues | Simpler setup | Message loss on single-node restart | Acceptable for low-stakes notification queues; not for wallet/payment queues |
| Polling `/payment/status` with no timeout on frontend | Simple client code | User stuck in infinite loading if callback never arrives | Never — always set a client-side timeout of 3–4 minutes |
| Storing NIDA full response without hashing PII | Simpler data model | GDPR/privacy risk — unnecessary PII retention | Never — store only name, verified boolean, NIN hash |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Azampay callbacks | Trusting Azampay calls your endpoint exactly once | Enforce idempotency via `UNIQUE` constraint on `transaction_id`; return 200 on duplicate |
| Azampay callbacks | Returning 500 on business logic errors (e.g., user not found) causing infinite Azampay retries | Return 200 for any callback that arrives for an unknown/invalid user — log and alert, but don't trigger Azampay retries |
| NIDA API | Blocking thread on NIDA call with no timeout | Configure `connectTimeout=5s`, `readTimeout=15s`; wrap in Resilience4j `@CircuitBreaker` |
| NIDA API | Calling NIDA multiple times for the same NIN | Cache verified NINs permanently in identity service DB; check cache before calling NIDA |
| Upstream SMS provider | Retrying a timed-out batch with the same payload | Generate unique `clientRef` per attempt; query provider status before retrying |
| Upstream SMS provider | Assuming DLR callbacks always arrive | DLR callback rate is typically 85–95% in Tanzania; implement timeout-based status polling for the rest |
| CloudAMQP | Declaring queues as classic when quorum queues are available | Explicitly declare business queues as quorum queues via `x-queue-type: quorum` argument |
| DO Managed PostgreSQL | Connecting without connection pooling | Use PgBouncer or HikariCP with `maximumPoolSize` tuned per service; DO Managed PG has connection limits |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Loading all contacts for a group into memory before dispatch | `java.lang.OutOfMemoryError` on large campaigns; OOMKill on messaging service pod | Stream contacts in pages (`Pageable`) via Spring Data JPA; never `findAll()` on contacts table | Campaigns >5,000 contacts; earlier with 512Mi limit |
| Synchronous HTTP calls to upstream SMS provider per message | Campaign dispatch blocks for minutes; thread pool exhaustion | Use async dispatch with RabbitMQ work queue; messaging service consumers pull from queue and batch to provider | Campaigns >100 messages |
| N+1 queries on campaign delivery status dashboard | Dashboard page loads 10–30 seconds | Use `JOIN FETCH` or projection DTOs; never load entity per message in a loop | Campaigns >200 messages |
| CSV import loading entire file into memory | OutOfMemoryError on large imports | Stream CSV rows with Apache Commons CSV `CSVParser`; process in chunks of 500 | CSV files >10,000 rows / >2MB |
| Unbounded RabbitMQ consumer prefetch | One slow consumer starves others; memory pressure from large in-flight batch | Set `spring.rabbitmq.listener.simple.prefetch=10` for message-intensive queues | 100+ concurrent message consumers |
| Missing PostgreSQL indexes on `messages(campaign_id)` and `ledger(user_id, created_at)` | Slow campaign status queries; slow credit history loads | Define indexes in Flyway migrations from day one | 1,000+ messages per campaign |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| No Azampay webhook signature verification | Attacker forges a callback to top up wallet without paying | Implement HMAC-SHA256 verification on every Azampay callback using the shared secret; reject unsigned callbacks with 401 |
| Exposing raw NIDA response including full national ID data in API responses | PII data leak; regulatory violation (Tanzania PDPA 2022) | Strip all PII from API responses; return only `verified: true/false` and display name |
| JWT tokens without short expiry or refresh rotation | Stolen token gives permanent access | Use 15-minute access tokens + 7-day refresh tokens; implement refresh token rotation |
| Sender ID spoofing — no validation that the sender ID belongs to the authenticated user | User sends SMS with another user's approved sender ID | Enforce FK constraint: `sender_id.user_id = authenticated_user_id` at query layer, not just business logic |
| Admin endpoints not separated from customer API | Privilege escalation if JWT is compromised | Admin service on separate internal port/namespace with separate JWT claim check; not routable from internet |
| Storing Azampay shared secret in environment variables without secret management | Secret leaked via `env` dump or log output | Use Kubernetes Secrets (encrypted at rest on DOKS); never log secrets; reference via `valueFrom.secretKeyRef` |
| No rate limiting on NIDA verification endpoint | Attacker enumerates NINs via registration flow | Rate-limit verification endpoint per IP (Redis token bucket via Traefik middleware); max 5 attempts per NIN per hour |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Payment "Processing..." spinner with no timeout or fallback | Users pay a second time or abandon; support load increases | Show countdown timer (2 minutes); at timeout show "Payment may be processing — check your transaction history" with a link |
| Verification rejected but no clear reason | Users with valid NINs abandon registration; they don't know if they should retry | Show specific message: "Name mismatch" vs "NIN not found" vs "Service temporarily unavailable — try again in 5 minutes" |
| Credit balance shown as a raw number (e.g., "1000") | Users confused about what a credit represents | Label consistently as "1,000 SMS credits" — the currency is the unit |
| Campaign sent with 0 recipients due to silent filter | User thinks campaign went out; nobody received it | Block dispatch if effective recipient count = 0 after suppression/dedup filter; show "No valid recipients" with actionable guidance |
| Low-credit alert appears after the last credit is consumed | User attempts a campaign, it fails, they see the alert | Trigger low-credit alert at 20% of last purchased bundle remaining, not at 0 |
| Bulk import shows "Import complete" but silently skipped invalid rows | User thinks all 500 contacts were imported; 50 were dropped | Show import result summary: "450 imported, 50 skipped (invalid phone format)" with downloadable error report |
| No indication of campaign scheduling timezone | Scheduled campaigns fire at unexpected times for users in EAT (UTC+3) | Store all timestamps in UTC; display in EAT with timezone label; confirm "Send at 10:00 AM EAT" not "10:00 UTC" |

---

## "Looks Done But Isn't" Checklist

- [ ] **Credit deduction:** Reservation is created — verify the reservation is also voided on campaign dispatch failure, not just on success.
- [ ] **Payment callback:** Endpoint returns 200 — verify it also handles duplicate `transaction_id` without double-crediting.
- [ ] **RabbitMQ queues:** Queues declared — verify they are `durable`, messages are `persistent`, and a DLX is configured for each business queue.
- [ ] **NIDA verification:** Registration succeeds — verify free 50 SMS credits are only granted after NIDA confirmation, not at registration time.
- [ ] **SMS dispatch:** Campaign shows "Sent" — verify delivery status is being tracked per message, not just per campaign.
- [ ] **CSV import:** File uploads — verify duplicate phone numbers within the same import are deduplicated before insert.
- [ ] **Scheduled campaigns:** Scheduler shows campaign queued — verify campaigns scheduled while service was down are still dispatched after restart.
- [ ] **Sender ID approval:** Admin can approve sender IDs — verify approved sender IDs cannot be used by a different user.
- [ ] **Credit expiry:** Ledger has expiry timestamps — verify expired credits are excluded from available balance calculation, not just flagged.
- [ ] **Graceful shutdown:** Service stops cleanly — verify in-flight RabbitMQ messages are acknowledged before shutdown and active HTTP requests complete before pod terminates.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Credit double-spend discovered | HIGH | Audit `ledger` table for negative balances; identify affected users; issue manual correction entries with `ADJUSTMENT` type; notify users; add `FOR UPDATE` lock retroactively |
| Duplicate Azampay callback credited twice | MEDIUM | Query payments table for duplicate `transaction_id`; identify double-credited ledger entries; reverse with `DEBIT_CORRECTION` entries; reconcile against Azampay transaction report |
| RabbitMQ message loss (payment event lost) | MEDIUM | Run reconciliation query: payments in `CONFIRMED` state with no corresponding ledger `CREDIT` entry; replay events from payment records; add unique constraint to prevent future duplicates |
| OOMKill crash loop on deployment | LOW | Increase container memory limit in Kustomize overlay; change to `-XX:MaxRAMPercentage=75`; rolling restart; monitor `jvm.memory.used` in Grafana for next 30 minutes |
| NIDA API outage blocking all registrations | LOW | Circuit breaker opens automatically; users see "verification temporarily unavailable" message; no action needed until NIDA recovers; reprocess pending verifications via scheduled retry job |
| Upstream SMS provider charges for retried messages | MEDIUM | Pull provider send report for date range; correlate with internal `messages` table via `clientRef`; identify double-billed messages; dispute with provider; update retry logic with provider status check |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Credit double-spend (race condition) | Wallet service implementation | Load test with concurrent dispatches from same user; check ledger for negative balance |
| Azampay callback double-credit | Payment service / Azampay integration | Integration test that POSTs same callback payload twice; assert wallet credit count = 1 |
| RabbitMQ message loss on restart | Infrastructure bootstrap (queue topology) | Kill consumer pod mid-processing; verify message redelivered on restart; check DLQ for poison messages |
| Transactional outbox gap | Payment service / Azampay integration | Kill payment service between DB commit and RabbitMQ publish; verify wallet eventually credits |
| NIDA API unavailability | Identity service / NIDA integration | Mock NIDA with 10s delay + 503 response; assert registration completes in PENDING state |
| JVM OOMKill from heap misconfiguration | Infrastructure bootstrap / Kustomize overlays | Load test messaging service; monitor `kubectl top pods`; confirm no OOMKill events |
| Readiness probe crash loop | Infrastructure bootstrap / Kustomize overlays | Deploy to staging cluster; observe pod startup timeline; confirm no CrashLoopBackOff |
| STK push timeout confusion | Payment service + frontend payment flow | Test with Azampay sandbox delayed callback; verify frontend shows countdown and correct fallback |
| Upstream SMS provider deduplication | Messaging service / provider integration | Simulate provider timeout on batch; verify retry uses new `clientRef`; check no double billing |
| Flyway concurrent migration deadlock | Infrastructure bootstrap (init containers) | Deploy with 2 replicas in staging; observe migration logs; confirm no lock exception |

---

## Sources

- Spring Boot 3.5 Reference Documentation — Kubernetes Probes, Graceful Shutdown, Container Lifecycle: https://docs.spring.io/spring-boot/3.5/how-to/deployment/cloud.html
- Spring Boot 3.5 Actuator — Health Endpoints, JVM Metrics: https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html
- Spring Boot 3.5 Graceful Shutdown: https://docs.spring.io/spring-boot/3.5/reference/web/graceful-shutdown.html
- Spring Data JPA — Locking (`@Lock`, `LockModeType.PESSIMISTIC_WRITE`): https://github.com/spring-projects/spring-data-jpa/blob/main/src/main/antora/modules/ROOT/pages/jpa/locking.adoc
- RabbitMQ — Consumer Acknowledgements and Publisher Confirms: https://www.rabbitmq.com/docs/confirms
- RabbitMQ — Reliability Guide: https://www.rabbitmq.com/docs/reliability
- RabbitMQ — Dead Letter Exchanges: https://www.rabbitmq.com/docs/dlx
- RabbitMQ — Quorum Queues: https://www.rabbitmq.com/docs/quorum-queues
- RabbitMQ — Queue Durability: https://www.rabbitmq.com/docs/queues
- Kubernetes — Assign Memory Resources: https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource
- Kubernetes — Container Probes: https://kubernetes.io/docs/concepts/workloads/pods/probes
- Spring Boot 3.5 — FlywayProperties.Postgresql (transactionalLock): https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/flyway/FlywayProperties.Postgresql.html
- Domain knowledge: Tanzania mobile money STK push flow behavior (Azampay, M-Pesa, Tigo Pesa, Airtel Money)
- Domain knowledge: NIDA API reliability patterns observed in Tanzania fintech integrations

---
*Pitfalls research for: Bulk SMS reseller platform — Spring Boot 3 microservices, prepaid credit wallet, Azampay payments, NIDA KYC, DOKS/Kubernetes*
*Researched: 2026-06-18*
