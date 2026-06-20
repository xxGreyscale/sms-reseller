# Phase 3: Wallet & Payments - Research

**Researched:** 2026-06-20
**Domain:** Append-only credit ledger, lot-based expiry, Azampay STK push, transactional outbox, idempotent callbacks, reconciliation
**Confidence:** HIGH (stack locked in CLAUDE.md + CONTEXT.md; Azampay API shape from Go SDK source — MEDIUM)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Credit consumption is expiry-soonest-first. Lot-based ledger: each credit grant is a lot carrying its own `expires_at`; reservation and consumption walk lots in ascending expiry order.
- **D-02:** Ledger is append-only. Balance is derived, never a mutable column. Reservation uses pessimistic `SELECT FOR UPDATE` (WLET-03) to refuse any reservation that would take available balance below zero.
- **D-03:** Purchased credits expire 12 months from purchase; bonus credits expire 30 days.
- **D-04:** If a payment times out (EXPIRED) but reconciliation later confirms it succeeded, credit the wallet anyway, idempotently. Late-success is a normal path.
- **D-05:** Only ONE pending payment in flight per user at a time.
- **D-06:** Payment lifecycle: PENDING → (SUCCESS | EXPIRED | FAILED), EXPIRED reachable late→SUCCESS via reconciliation.
- **D-07:** Refunds are ledger credit-back only (no real money movement at MVP). Phase 3 builds the mechanism; Phase 4 calls it.
- **D-08:** Low-credit alerts and 7-day expiry warnings use a fixed system-wide default threshold (starting default: 20 credits, tunable via config). Emit outbox events for Phase 5 notification fan-out.
- **D-09:** Bundle catalog is a DB table seeded via Flyway migration. MVP pricing: Taster 50/FREE, Starter 200/3,200 TZS, Growth 1,000/14,500 TZS, Pro 5,000/65,000 TZS, Scale 20,000/240,000 TZS. TZS stored as BIGINT cents (no Taster Azampay purchase — it is the NIDA grant).
- **D-10:** `PaymentGateway` interface with `StubPaymentGateway` (`@Profile("stub")`) and `AzampayPaymentGateway` (`@Profile("prod")`).

### Claude's Discretion

- Exact ledger table shape (lots, reservations, transactions)
- Reconciliation poll interval (CLAUDE.md suggests ~2 min for payments older than 5 min)
- Outbox table/relay reuse vs per-service copy
- Precise low-credit default value (decided: 20 credits)

### Deferred Ideas (OUT OF SCOPE)

- Money-back refunds via Azampay (real cash reversal)
- Per-user configurable low-credit threshold + settings UI
- Admin bundle/refund management and ledger inspection UI (Phase 5)
- Notification delivery/fan-out (Phase 5)
- Credit sharing between users
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WLET-01 | User can view current SMS credit balance on every screen | Derived balance query from credit_lots; REST endpoint on wallet-service |
| WLET-02 | User can view full credit transaction history (append-only ledger) | credit_transactions table; paginated GET endpoint |
| WLET-03 | System prevents balance going below zero using credit reservation before send (SELECT FOR UPDATE) | Pessimistic lot lock; reservation pattern documented below |
| WLET-04 | User receives low-credit alert when balance drops below configurable threshold | Post-debit check + outbox event; @Scheduled sweep |
| WLET-05 | User receives 7-day warning before purchased credits expire | @Scheduled expiry-warning sweep; outbox event |
| WLET-06 | Purchased credits expire after 12 months from purchase date | expires_at on lot; expiry sweep; exclude expired from balance |
| WLET-07 | Bonus credits (NIDA grant, promotions) expire after 30 days | Same mechanism, different TTL |
| PYMT-01 | User can view available SMS bundles with prices | sms_bundles table; GET /api/v1/bundles |
| PYMT-02 | User can purchase a bundle via Azampay mobile money | mobileCheckout STK push through PaymentGateway interface |
| PYMT-03 | Payment flow shows 2-minute countdown during STK push | Payment created in PENDING; UI polls or SSE; @Scheduled marks EXPIRED at 2 min |
| PYMT-04 | User receives confirmation after successful payment with credits credited | payment SUCCESS → credit_lots INSERT; outbox event emitted |
| PYMT-05 | User can view full payment history with statuses | payments table; GET /api/v1/payments |
| PYMT-06 | System processes Azampay callbacks idempotently | idempotency_key = azampay_reference; ON CONFLICT DO NOTHING |
| PYMT-07 | System marks unresolved STK push payments as EXPIRED after timeout | @Scheduled timeout sweeper at 2 minutes |
| PYMT-08 | System issues refunds for payments linked to failed campaigns | Idempotent ledger credit-back callable by Phase 4 |
</phase_requirements>

---

## Summary

Phase 3 builds two cohesive subsystems within the same Spring Boot service group: a **wallet-service** (credit ledger, lots, reservations, alerts, expiry) and a **payment-service** (bundle catalog, Azampay STK push, idempotent callbacks, reconciliation, refunds). They communicate via direct service calls within the payment bounded context, or via internal events through outbox.

The foundational design challenge is the lot-based append-only ledger: every credit grant produces a `credit_lots` row with an expiry timestamp; balance is always `SUM(available)` across non-expired lots; reservations walk lots in ascending `expires_at` order under `SELECT FOR UPDATE` and write `credit_transactions` debit rows, never touching a mutable balance column. This prevents negative balance and double-spend without optimistic retry storms.

The Azampay integration follows the CLAUDE.md pattern exactly: `RestClient` + Resilience4j circuit breaker, public callback endpoint with signature validation (HMAC when Azampay provides it), idempotent processing keyed on `utilityRef`/`externalId`, and a `@Scheduled` reconciliation job every 2 minutes that polls Azampay for payments older than 5 minutes that remain in PENDING or EXPIRED state. Late-confirmed payments (D-04) credit the wallet and flip payment to SUCCESS in a single transaction.

**Primary recommendation:** Build in order — ledger core → catalog seed → UserVerified consumer (first live integration) → payment flow with StubGateway → callback + reconciliation → refund mechanism → alerts and expiry sweep.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Credit balance query | wallet-service (API) | — | Owns the ledger; no cross-service join |
| Credit reservation (SELECT FOR UPDATE) | wallet-service (API) | — | Must be in same DB transaction as lot rows |
| Lot expiry sweep | wallet-service (worker/@Scheduled) | — | Same DB; no remote call needed |
| Bundle catalog read | payment-service (API) | — | catalog-service module or payment-service; Flyway-seeded |
| STK push initiation | payment-service (API) | Azampay (external) | Delegates to PaymentGateway interface |
| Azampay callback webhook | payment-service (API, public endpoint) | — | Inbound from Azampay; must be unauthenticated but signature-validated |
| Reconciliation polling | payment-service (worker/@Scheduled) | Azampay status API | Runs every 2 min, queries Azampay for pending/expired payments |
| Outbox relay (payment events) | payment-service (worker/@Scheduled) | RabbitMQ | Mirror of identity-service OutboxRelay |
| UserVerified consumer | wallet-service (AMQP consumer) | identity.events exchange | Grants 50-credit bonus lot on NIDA verification |
| Low-credit/expiry alerts | wallet-service (worker/@Scheduled) | RabbitMQ outbox | Sweep → outbox event for Phase 5 |
| Refund mechanism | wallet-service (API, internal) | — | Append-only credit-back; callable by Phase 4 |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.9 | Application framework | Locked in CLAUDE.md |
| Spring Data JPA + HikariCP | BOM-managed | Ledger persistence; `SELECT FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` | Part of locked stack |
| Flyway + flyway-database-postgresql | 10.x | Schema migrations for wallet and payment DBs | CLAUDE.md — must add `flyway-database-postgresql` explicitly |
| Spring AMQP | BOM-managed | AMQP consumer for UserVerified; outbox relay publisher | Locked stack |
| Resilience4j-spring-boot3 | 2.2.0 | Circuit breaker + retry on Azampay calls | CLAUDE.md — wrap all external calls |
| spring-boot-starter-validation | BOM-managed | Bean validation on DTOs | All services |
| spring-boot-starter-oauth2-resource-server | BOM-managed | JWT validation (trust Phase 2 JWTs via shared-security) | All services |
| spring-retry | BOM-managed | Retry on Azampay status polling | CLAUDE.md |
| Lombok + MapStruct | BOM / 1.6.3 | DTO mapping, boilerplate | CLAUDE.md — Lombok before MapStruct in annotationProcessor |
| RestClient | Spring 6.2.x (BOM) | HTTP to Azampay (sync) | CLAUDE.md — use RestClient, not RestTemplate |
| Testcontainers (PG16 + RabbitMQ) | 1.21.2 | Integration tests | CLAUDE.md; `@ServiceConnection` pattern |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-data-redis | BOM | Distributed lock for single-pending-payment enforcement (D-05) | Acquire Redis lock per userId before creating new payment |
| micrometer-registry-prometheus | BOM | Payment/wallet metrics | Standard observability |
| sentry-spring-boot-starter-jakarta | 7.x | Error tracking | All services |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SELECT FOR UPDATE` pessimistic lock | Optimistic locking + retry | Optimistic causes retry storms under concurrent load; pessimistic is correct for financial transactions |
| Per-service outbox copy | Shared outbox library | Library adds coupling; per-service copy is the Phase 2 pattern — reuse by duplication, not extraction at MVP |

**Installation (additions beyond Phase 2):**
```bash
# payment-service and wallet-service build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
implementation("org.springframework.retry:spring-retry")
implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.x")
runtimeOnly("org.postgresql:postgresql")
annotationProcessor("org.projectlombok:lombok")
annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
testImplementation("org.testcontainers:postgresql")
testImplementation("org.testcontainers:rabbitmq")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
```

---

## Package Legitimacy Audit

All packages in this phase are part of the locked Spring Boot BOM or established open-source libraries (Resilience4j, Testcontainers, Sentry). No new third-party packages were introduced that require slopcheck vetting beyond what Phase 2 already validated. `resilience4j-spring-boot3:2.2.0` and `sentry-spring-boot-starter-jakarta` were validated in CLAUDE.md sources. [ASSUMED: slopcheck not run — all packages are Spring ecosystem or already in project BOM]

| Package | Registry | Age | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|
| resilience4j-spring-boot3 | Maven Central | 5+ yrs | OK (established) | Approved |
| flyway-database-postgresql | Maven Central | 3+ yrs | OK (official Flyway module) | Approved |
| spring-boot-starter-amqp | Maven Central | 10+ yrs | OK (Spring official) | Approved |
| sentry-spring-boot-starter-jakarta | Maven Central | 2+ yrs | OK (official Sentry SDK) | Approved |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Flutter/Next.js client
         │
         ▼
   [Traefik ingress] ── JWT validated
         │
    ┌────┴──────────────────────┐
    │                           │
    ▼                           ▼
payment-service              wallet-service
(catalog, STK push,          (ledger lots,
 callback, reconcile,         reservations,
 refund trigger)              balance, alerts)
    │                           │   ▲
    │ internal REST call         │   │
    └──────── reserve/grant ────►│   │
                                 │   │
                       identity.events (AMQP)
                       UserVerified ──────────┘
                       (NIDA grant bonus lot)
    │
    ▼ Azampay external
POST /checkout/mobileCheckout   (STK push initiate)
GET  /transactionStatus          (reconciliation poll)
    │
    ▼ callback inbound
POST /api/v1/payments/callback   (webhook, public, idempotent)
    │
    ▼
payment_outbox → OutboxRelay → payments.events (AMQP)
                               PaymentConfirmed
                               LowCreditAlert
                               ExpiryWarning
```

### Recommended Project Structure

```
services/
├── wallet-service/
│   ├── src/main/java/com/opendesk/wallet/
│   │   ├── lot/             # CreditLot entity, CreditLotRepository, LotService
│   │   ├── reservation/     # CreditReservation entity, ReservationService (SELECT FOR UPDATE)
│   │   ├── transaction/     # CreditTransaction entity (append-only ledger entries)
│   │   ├── balance/         # BalanceService (derives available balance from lots)
│   │   ├── consumer/        # UserVerifiedConsumer (@RabbitListener)
│   │   ├── sweep/           # ExpirySweepJob, LowCreditAlertJob (@Scheduled)
│   │   ├── outbox/          # OutboxEntry, OutboxRepository, OutboxRelay (copy from identity-service)
│   │   ├── api/             # WalletController (balance, history, reservation endpoints)
│   │   └── config/          # RabbitMqConfig, SecurityConfig
│   └── src/main/resources/db/migration/
│       ├── V1__create_credit_lots.sql
│       ├── V2__create_credit_transactions.sql
│       ├── V3__create_credit_reservations.sql
│       └── V4__create_outbox.sql
│
├── payment-service/
│   ├── src/main/java/com/opendesk/payment/
│   │   ├── bundle/          # SmsBundle entity, BundleRepository, BundleController
│   │   ├── gateway/         # PaymentGateway interface, StubPaymentGateway, AzampayPaymentGateway
│   │   ├── payment/         # Payment entity, PaymentRepository, PaymentService, PaymentController
│   │   ├── callback/        # CallbackController (public), CallbackProcessor (idempotent)
│   │   ├── reconciliation/  # ReconciliationJob (@Scheduled)
│   │   ├── refund/          # RefundService (calls wallet-service reservation/grant API)
│   │   ├── outbox/          # OutboxEntry, OutboxRepository, OutboxRelay (copy from identity-service)
│   │   └── config/          # RabbitMqConfig, SecurityConfig, Resilience4jConfig
│   └── src/main/resources/db/migration/
│       ├── V1__create_sms_bundles.sql
│       ├── V2__seed_sms_bundles.sql
│       └── V3__create_payments.sql + V4__create_outbox.sql
```

### Pattern 1: Lot-Based Append-Only Ledger

**What:** Every credit event (purchase, bonus grant, refund, expiry) is represented as a `credit_lots` row. Reservations and consumptions write `credit_transactions` rows. Balance is always derived — never stored.

**Schema:**
```sql
-- V1__create_credit_lots.sql
CREATE TABLE credit_lots (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    lot_type      VARCHAR(20) NOT NULL,  -- PURCHASED | BONUS | REFUND
    granted       INT NOT NULL,
    consumed      INT NOT NULL DEFAULT 0,
    reserved      INT NOT NULL DEFAULT 0,
    expires_at    TIMESTAMPTZ NOT NULL,
    payment_id    UUID,                  -- FK to payments.id (nullable for bonus)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_lots_user_expires ON credit_lots (user_id, expires_at)
    WHERE (granted - consumed - reserved) > 0;

-- credit_transactions: immutable audit trail
CREATE TABLE credit_transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    lot_id        UUID NOT NULL REFERENCES credit_lots(id),
    txn_type      VARCHAR(20) NOT NULL,  -- GRANT | RESERVE | CONSUME | RELEASE | EXPIRE
    delta         INT NOT NULL,          -- always positive; txn_type conveys direction
    reference_id  UUID,                  -- payment_id or campaign_id
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Balance derivation (no mutable column):**
```sql
SELECT COALESCE(SUM(granted - consumed - reserved), 0) AS available_credits
FROM   credit_lots
WHERE  user_id = :userId
  AND  expires_at > now();
```

**Source:** D-02 (CONTEXT.md) + standard financial ledger pattern [ASSUMED — no Context7 source; pattern is well-established]

### Pattern 2: Expiry-Soonest-First Reservation (SELECT FOR UPDATE)

**What:** When reserving N credits, walk lots in ascending `expires_at` order, take as much as available from each lot (updating `reserved`), write a `credit_transactions` row per lot touched. All in one `@Transactional` method.

**Implementation:**
```java
// Source: D-01 + D-02 (CONTEXT.md); SELECT FOR UPDATE per JPA @Lock [ASSUMED pattern]
@Transactional(isolation = Isolation.READ_COMMITTED)
public ReservationResult reserve(UUID userId, int count, UUID referenceId) {
    List<CreditLot> lots = lotRepository
        .findAvailableByUserIdOrderByExpiresAtAsc(userId, PageRequest.ofSize(50));
    // pessimistic write lock applied per lot via @Lock(PESSIMISTIC_WRITE) on the query
    int remaining = count;
    List<UUID> lotIds = new ArrayList<>();
    for (CreditLot lot : lots) {
        if (remaining <= 0) break;
        int canTake = lot.getGranted() - lot.getConsumed() - lot.getReserved();
        if (canTake <= 0 || lot.getExpiresAt().isBefore(Instant.now())) continue;
        int take = Math.min(canTake, remaining);
        lot.setReserved(lot.getReserved() + take);
        remaining -= take;
        lotIds.add(lot.getId());
        txnRepository.save(new CreditTransaction(userId, lot.getId(), TXN_RESERVE, take, referenceId));
    }
    if (remaining > 0) throw new InsufficientCreditsException("Not enough available credits");
    return new ReservationResult(lotIds, count - remaining);
}
```

**Repository query (Spring Data JPA):**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT l FROM CreditLot l WHERE l.userId = :userId " +
       "AND (l.granted - l.consumed - l.reserved) > 0 " +
       "AND l.expiresAt > :now ORDER BY l.expiresAt ASC")
List<CreditLot> findAvailableByUserIdOrderByExpiresAtAsc(
    @Param("userId") UUID userId,
    @Param("now") Instant now,
    Pageable pageable);
```

**Deadlock prevention:** Always lock lots in `expires_at` ASC order. Never acquire locks in arbitrary order. Use `READ_COMMITTED` isolation (PostgreSQL default) — `REPEATABLE_READ` is unnecessary and increases contention. [ASSUMED — standard PostgreSQL deadlock avoidance by consistent lock ordering]

### Pattern 3: PaymentGateway Mock-First Interface

**What:** Mirrors the `NidaVerificationService` stub pattern from Phase 2 exactly.

```java
// Source: D-10 (CONTEXT.md); Phase 2 pattern [ASSUMED code shape]
public interface PaymentGateway {
    StkPushResult initiateStkPush(StkPushRequest request);
    TransactionStatusResult queryTransactionStatus(String externalId);
}

@Service
@Profile("stub")
public class StubPaymentGateway implements PaymentGateway {
    // Configurable outcome: SUCCESS (default), FAILURE, TIMEOUT
    // Via application.yml: stub.payment.default-outcome=SUCCESS
    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        // simulate async — return immediately, outcome resolved on next poll
    }
}

@Service
@Profile("prod")
public class AzampayPaymentGateway implements PaymentGateway {
    @CircuitBreaker(name = "azampay")
    @Retry(name = "azampay")
    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        // POST to Azampay mobileCheckout endpoint
    }
}
```

### Pattern 4: Idempotent Callback + Transactional Outbox

**What:** Azampay delivers the webhook callback (possibly multiple times). The handler is idempotent via `externalId` uniqueness in the payments table.

```java
// Source: CLAUDE.md §Azampay; D-04 (CONTEXT.md) [ASSUMED code shape]
@Transactional
public void processCallback(AzampayCallbackPayload payload) {
    // payload.utilityRef == our externalId (the payment UUID we sent to Azampay)
    Payment payment = paymentRepository.findByExternalId(payload.getUtilityRef())
        .orElseThrow(() -> new PaymentNotFoundException(payload.getUtilityRef()));
    
    if (payment.getStatus() == PaymentStatus.SUCCESS) {
        return; // idempotent guard — already processed
    }

    if ("success".equalsIgnoreCase(payload.getTransactionStatus())) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setOperatorReference(payload.getMnoReference());
        // Credit the wallet
        walletService.grantCredits(payment.getUserId(), payment.getBundle().getSmsCount(),
            LotType.PURCHASED, payment.getId());
        // Write outbox event
        outboxRepository.save(new OutboxEntry("PaymentConfirmed",
            objectMapper.writeValueAsString(new PaymentConfirmedEvent(payment))));
    } else {
        payment.setStatus(PaymentStatus.FAILED);
    }
}
```

**Idempotency key:** `payments.external_id` has a UNIQUE constraint. A second callback for the same `utilityRef` hits the idempotent guard (`status == SUCCESS`) and returns immediately. No double-credit.

**Alternative idempotency table approach (processed_events):**
Use `INSERT INTO processed_events (event_id) VALUES (:utilityRef) ON CONFLICT DO NOTHING` — if 0 rows inserted, skip processing. This is the stronger pattern for at-least-once AMQP delivery. Use this for the UserVerified consumer.

### Pattern 5: Reconciliation Job

```java
// Source: CLAUDE.md §Azampay "Polling recovery pattern" [ASSUMED code shape]
@Component
public class ReconciliationJob {

    @Scheduled(fixedDelay = 120_000) // every 2 minutes
    @Transactional
    public void reconcile() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        // Query payments that are PENDING or EXPIRED and older than 5 minutes
        List<Payment> stale = paymentRepository
            .findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.EXPIRED), cutoff);
        
        for (Payment p : stale) {
            TransactionStatusResult result = paymentGateway.queryTransactionStatus(p.getExternalId());
            if (result.isSuccess()) {
                // Credit wallet — idempotent (guard on payment status check)
                processLateSuccess(p);
            }
            // FAILED stays FAILED or remains EXPIRED; no action
        }
    }
}
```

**Key insight (D-04):** When an EXPIRED payment is confirmed succeeded by reconciliation, credit the wallet and flip to SUCCESS. Money left the customer's account; honor it. This is a normal path.

### Pattern 6: UserVerified Consumer (First Inbound Integration)

```java
// Source: 02-06-SUMMARY.md — identity.events topic exchange, routing key identity.UserVerified [VERIFIED from SUMMARY]
@Component
public class UserVerifiedConsumer {

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
        exchange = @Exchange(name = "identity.events", type = ExchangeTypes.TOPIC),
        key = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(UserVerifiedEvent event) {
        // Idempotency: check processed_events table
        if (!processedEventRepository.tryInsert(event.eventId())) {
            return; // already handled
        }
        // Grant 50-credit bonus lot, 30-day expiry (D-03)
        lotService.grantBonus(event.userId(), event.freeCredits(),
            Instant.now().plus(30, ChronoUnit.DAYS));
    }
}
```

**Exchange:** `identity.events` (TopicExchange, durable=true) — created by identity-service. Wallet binds its own durable queue. [VERIFIED from 02-06-SUMMARY.md]

**UserVerifiedEvent shape (from identity-service):**
```java
// Source: 02-06-SUMMARY.md [VERIFIED from SUMMARY]
record UserVerifiedEvent(String eventId, UUID userId, int freeCredits) {
    static final int DEFAULT_FREE_CREDITS = 50;
}
```

### Pattern 7: Expiry Sweep + Low-Credit Alert Jobs

```java
// Source: D-03, D-08 (CONTEXT.md) [ASSUMED code shape]
@Scheduled(cron = "0 0 2 * * *") // daily at 02:00 UTC
@Transactional
public void expireLots() {
    int expired = lotRepository.markExpired(Instant.now());
    // INSERT credit_transactions rows for expired lots (type=EXPIRE, delta=remaining)
    // Emit ExpiryEvent outbox for Phase 5
}

@Scheduled(cron = "0 0 8 * * *") // daily at 08:00 UTC — 7-day warning
@Transactional
public void expiryWarningCheck() {
    Instant warningCutoff = Instant.now().plus(7, ChronoUnit.DAYS);
    List<CreditLot> expiringSoon = lotRepository
        .findExpiringBefore(warningCutoff, LotType.PURCHASED);
    // For each user with expiring lots not yet warned: emit ExpiryWarning outbox event
}

@Scheduled(fixedDelay = 300_000) // every 5 minutes
public void lowCreditCheck() {
    // Query users with available_credits < LOW_CREDIT_THRESHOLD (20, from config)
    // For those not yet alerted today: emit LowCreditAlert outbox event
}
```

### Anti-Patterns to Avoid

- **Mutable balance column:** Never add a `balance` column to user or wallet table. Balance is always derived. A cached column creates the double-credit/phantom-balance vector. [D-02]
- **Cross-service DB joins:** wallet-service and payment-service each own their schema. Phase 4 calls wallet-service REST API — never queries its DB directly. [CLAUDE.md]
- **`javax.*` imports:** Use `jakarta.*` throughout. Spring Boot 3 / Jakarta EE 10. [CLAUDE.md]
- **`RestTemplate`:** Use `RestClient` for Azampay HTTP calls. [CLAUDE.md]
- **Sharing a single `SELECT FOR UPDATE` on the user row:** Lock individual lot rows in consistent order, not a user-level sentinel row — the sentinel row creates a global bottleneck per user.
- **Floating-point TZS amounts:** Store all TZS amounts as `BIGINT` cents (e.g., 3200 TZS = 320000 in BIGINT cents, or simply 3200 if treating TZS as atomic unit). CLAUDE.md says "BIGINT TZS cents" — no `DECIMAL` or `FLOAT`.
- **Single pending payment enforcement via DB unique index on status:** Use a Redis distributed lock per userId instead (acquire before creating payment, release after PENDING row committed). A partial DB index is an alternative but less flexible.

---

## Azampay API Shape

[CITED: pkg.go.dev/github.com/kateile/go-azampay — authoritative Go SDK reflecting official Azampay API]
[ASSUMED: no official documentation URL was accessible; derived from Go SDK source and Python SDK README]

### Authentication

| Environment | Token Endpoint |
|-------------|---------------|
| Sandbox | `https://authenticator-sandbox.azampay.co.tz/AppRegistration/GenerateToken` |
| Production | `https://authenticator.azampay.co.tz/AppRegistration/GenerateToken` |

Request body: `{ "appName": "", "clientId": "", "clientSecret": "" }`
Response: Bearer token (short-lived — refresh before calls or on 401).

### Mobile Checkout (STK Push Initiate)

| Environment | Base URL |
|-------------|---------|
| Sandbox | `https://sandbox.azampay.co.tz` |
| Production | `https://checkout.azampay.co.tz` |

**POST** `{base}/azampay/mobileCheckout`

Request body:
```json
{
  "accountNumber": "0700000000",
  "amount": "3200",
  "currency": "TZS",
  "externalId": "<UUID of our payment record>",
  "provider": "Airtel | Tigo | Halopesa | Azampesa",
  "additionalProperties": {}
}
```

Response:
```json
{ "success": true, "transactionId": "<azampay-txn-id>", "message": "..." }
```

**Important:** `externalId` is our idempotency key — must be UUID of the `payments` row. Max 128 ASCII chars.

### Webhook Callback Payload (inbound from Azampay)

Azampay POSTs to `{our callback_url}` when the transaction resolves:
```json
{
  "msisdn": "0700000000",
  "amount": "3200",
  "message": "Payment successful",
  "utilityRef": "<our externalId — the payment UUID>",
  "operator": "Airtel",
  "reference": "<azampay-internal-ref>",
  "transactionStatus": "success | fail",
  "submerchantAcc": ""
}
```

**Key field:** `utilityRef` maps back to our `payments.external_id`. This is the idempotency key.

**Signature validation:** The Go SDK does not document an explicit HMAC header. CLAUDE.md notes "validate webhook signatures (if Azampay provides HMAC — verify during merchant onboarding)". Implement a `StubSignatureValidator` for dev and a `HmacSignatureValidator` that reads a secret from config for prod. If Azampay does not provide HMAC, validate by checking the `utilityRef` exists in our DB.

### Transaction Status Query

**GET** `{base}/azampay/transactionStatus?pgReferenceId={externalId}`

Used by reconciliation job. Returns current status for a given `externalId`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT validation | Custom JWT parser | `shared-security` NimbusJwtDecoder (from Phase 2) | Already built and tested |
| HTTP circuit breaker | Manual try/catch/retry | Resilience4j `@CircuitBreaker` + `@Retry` | Handles open/half-open/close, backoff, fallback |
| AMQP message serialization | Manual JSON marshalling | `Jackson2JsonMessageConverter` (Spring AMQP, already used in identity-service) | Type-safe, same pattern as Phase 2 |
| Idempotency table | Custom lock table | `processed_events` table with `ON CONFLICT DO NOTHING` | Standard outbox idempotency — already a Phase 2 pattern |
| Outbox relay | Custom scheduler | Copy of identity-service `OutboxRelay` | Proven, tested, bounded batch |
| DB connection pool | Custom pool | HikariCP (Spring Boot autoconfigures) | Autoconfigured; just tune pool size |
| Distributed lock (single-pending) | SELECT FOR UPDATE on user row | Redis `SET NX EX` via `RedisTemplate` | User row lock is a global bottleneck; Redis lock is per-userId with TTL |

**Key insight:** The entire Phase 2 outbox + relay machinery is copy-paste reusable. Do not extract into a shared library at MVP — duplication here is intentional (per CLAUDE.md: no cross-service joins, per-service ownership).

---

## Common Pitfalls

### Pitfall 1: Double-Credit via Concurrent Callbacks
**What goes wrong:** Azampay delivers the webhook twice simultaneously. Both threads read `payment.status == PENDING`, both proceed to grant credits.
**Why it happens:** No idempotency guard before the DB write.
**How to avoid:** Idempotent guard at the start of `processCallback`: `if (payment.getStatus() == SUCCESS) return;` — but this is not thread-safe. The stronger approach is `processed_events` table with `INSERT ... ON CONFLICT DO NOTHING` — if 0 rows inserted, return early. This is atomic.
**Warning signs:** Balance drift; users reporting more credits than expected.

### Pitfall 2: Negative Balance Under Concurrent Reservations
**What goes wrong:** Two campaign sends for the same user run concurrently. Both read the same available balance, both succeed, combined reservation exceeds actual balance.
**Why it happens:** No pessimistic lock on lots during balance check.
**How to avoid:** `@Lock(PESSIMISTIC_WRITE)` on the lot query. Both reads serialize via Postgres row-level locks. Second reservation correctly sees reduced available after the first commits.
**Warning signs:** `reserved > granted - consumed` on any lot row.

### Pitfall 3: Deadlock from Inconsistent Lot Lock Order
**What goes wrong:** Thread A locks lot-1 then lot-2; Thread B locks lot-2 then lot-1. Deadlock.
**Why it happens:** Lots returned in non-deterministic order if query doesn't enforce ORDER BY.
**How to avoid:** Always `ORDER BY expires_at ASC` in the lot query. Consistent lock ordering eliminates deadlock.
**Warning signs:** `PSQLException: ERROR: deadlock detected`.

### Pitfall 4: Expired Lots Included in Balance
**What goes wrong:** Expiry sweep hasn't run yet; expired lots inflate available balance.
**Why it happens:** Balance query missing `AND expires_at > now()` filter.
**How to avoid:** Every balance derivation query must filter `expires_at > now()`. Expiry sweep is belt-and-suspenders (marks lots consumed for audit trail), not the primary guard.
**Warning signs:** Users can send SMS they shouldn't be able to; expired lots appear in balance.

### Pitfall 5: Lost Payment Due to Callback Race with EXPIRED State Machine
**What goes wrong:** EXPIRED sweep runs at T+2min, flips payment to EXPIRED. Callback arrives at T+2min+1s. Callback sees EXPIRED and skips (if idempotency guard is `status == SUCCESS` only).
**Why it happens:** Idempotency guard only skips if SUCCESS; EXPIRED is not SUCCESS so it would re-process — but we actually want to process it (D-04).
**How to avoid:** Callback handler must process both PENDING and EXPIRED payments. Only skip if already SUCCESS. The reconciliation job also handles EXPIRED → late-SUCCESS.

### Pitfall 6: Flyway Missing `flyway-database-postgresql`
**What goes wrong:** Silent degraded behavior; migrations may not run or fail without clear error.
**Why it happens:** Flyway 10 split the PostgreSQL driver module from core.
**How to avoid:** Add `org.flywaydb:flyway-database-postgresql` explicitly alongside `flyway-core`. [VERIFIED: CLAUDE.md]

### Pitfall 7: TZS Stored as DECIMAL/FLOAT
**What goes wrong:** Floating-point rounding errors in financial amounts.
**Why it happens:** Using `Double` or `BigDecimal` carelessly in entity.
**How to avoid:** Store all TZS amounts as `BIGINT` in Postgres; map to `long` in Java. [VERIFIED: CLAUDE.md]

---

## Code Examples

### Grant Bonus Credits (UserVerified handler)
```java
// Source: D-01, D-03 (CONTEXT.md); lot pattern [ASSUMED]
@Transactional
public CreditLot grantBonus(UUID userId, int credits, Instant expiresAt) {
    CreditLot lot = CreditLot.builder()
        .userId(userId)
        .lotType(LotType.BONUS)
        .granted(credits)
        .consumed(0)
        .reserved(0)
        .expiresAt(expiresAt)
        .build();
    return lotRepository.save(lot);
}
```

### Azampay STK Push (AzampayPaymentGateway)
```java
// Source: Azampay Go SDK + CLAUDE.md Azampay Integration [CITED: pkg.go.dev/github.com/kateile/go-azampay]
@CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
@Retry(name = "azampay")
public StkPushResult initiateStkPush(StkPushRequest req) {
    AzampayMobileCheckoutRequest body = AzampayMobileCheckoutRequest.builder()
        .accountNumber(req.msisdn())
        .amount(String.valueOf(req.amountTzs()))  // BIGINT → String, no decimals
        .currency("TZS")
        .externalId(req.paymentId().toString())   // UUID — our idempotency key
        .provider(req.provider())
        .build();
    
    return restClient.post()
        .uri(azampayBaseUrl + "/azampay/mobileCheckout")
        .header("Authorization", "Bearer " + tokenProvider.getToken())
        .body(body)
        .retrieve()
        .body(AzampayCheckoutResponse.class)
        .toStkPushResult();
}
```

### Payment State Machine
```java
// Source: D-06 (CONTEXT.md) [ASSUMED enum]
public enum PaymentStatus {
    PENDING,     // STK push initiated, awaiting Azampay callback
    SUCCESS,     // Confirmed paid; credits granted
    EXPIRED,     // 2-minute timeout elapsed; may transition to SUCCESS via reconciliation
    FAILED       // Azampay reported failure; terminal
}
```

### Flyway Bundle Seed Migration
```sql
-- V2__seed_sms_bundles.sql
-- Source: D-09 (CONTEXT.md) [VERIFIED from CONTEXT.md]
INSERT INTO sms_bundles (id, name, sms_count, price_tzs, is_active, description) VALUES
  (gen_random_uuid(), 'Taster',  50,     0,         true, 'Free on NIDA verification — not purchasable'),
  (gen_random_uuid(), 'Starter', 200,    320000,    true, NULL),  -- 3,200 TZS in cents
  (gen_random_uuid(), 'Growth',  1000,   1450000,   true, NULL),  -- 14,500 TZS
  (gen_random_uuid(), 'Pro',     5000,   6500000,   true, NULL),  -- 65,000 TZS
  (gen_random_uuid(), 'Scale',   20000,  24000000,  true, NULL)   -- 240,000 TZS
ON CONFLICT DO NOTHING;
```

Note: TZS stored as BIGINT cents (multiply TZS × 100). Taster `is_purchasable=false` or price=0 to block Azampay flow.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` | `RestClient` (sync) | Spring 6.1 / Boot 3.2 | Use `RestClient.create()` builder; RestTemplate is soft-deprecated |
| `javax.transaction.Transactional` | `jakarta.transaction.Transactional` | Spring Boot 3 / Jakarta EE 10 | All `@Transactional` uses `jakarta.*` |
| `REPEATABLE_READ` for financial TX | `READ_COMMITTED` + `SELECT FOR UPDATE` | Standard PostgreSQL advice | READ_COMMITTED + explicit locks avoids phantom reads without locking the entire table |
| Separate `idempotency_keys` table | `processed_events` with `ON CONFLICT DO NOTHING` | Phase 2 pattern established | Reuse the outbox idempotency table design |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Azampay callback payload uses `utilityRef` field mapping to our `externalId` | Azampay API Shape | Wrong field name causes callback processing failure; verify during sandbox testing |
| A2 | Azampay transaction status query is `GET /azampay/transactionStatus?pgReferenceId={externalId}` | Azampay API Shape | Reconciliation job calls wrong endpoint; fix is a 1-line URL change |
| A3 | Azampay does NOT provide HMAC signature headers (verify during merchant onboarding) | Azampay API Shape | If they do provide HMAC, the stub validator needs a real implementation |
| A4 | Lot `reserved` column is updated in-place during reservation (not a separate reservations table) | Pattern 2 | A separate reservations table (like hotel booking) is cleaner but adds joins; simplification is intentional for MVP |
| A5 | TZS amounts in Azampay API are sent as String (not BIGINT) | Azampay API Shape | Amount format mismatch rejected by Azampay; confirmed by Go SDK `amount: string` |
| A6 | Low-credit sweep runs every 5 minutes (post-debit async) | Pattern 7 | Latency in alert delivery; acceptable for MVP |
| A7 | `sms_bundles.price_tzs` is stored as BIGINT cents (price × 100) | Schema design | If stored as raw TZS integer (not cents), seed values in CONTEXT.md must be used as-is; clarify with D-09 |

---

## Open Questions

1. **Azampay HMAC signature scheme**
   - What we know: CLAUDE.md says "validate webhook signatures (if Azampay provides HMAC — verify during merchant onboarding)"
   - What's unclear: Field name of the signature header; HMAC algorithm (SHA-256?); key format
   - Recommendation: Build a `WebhookSignatureValidator` interface with `StubSignatureValidator` (always valid) and `HmacSignatureValidator` (reads secret from env). Wire prod implementation when merchant onboarding documents arrive.

2. **TZS cents vs raw TZS for price storage**
   - What we know: CLAUDE.md says "store as BIGINT — no decimals needed"; D-09 gives prices in TZS (e.g., 3,200 TZS for Starter)
   - What's unclear: Whether BIGINT means TZS × 100 (cents) or raw TZS integer (TZS has no cents sub-unit)
   - Recommendation: Since TZS has no decimal subdivision, store as raw TZS integer (3200, not 320000). "BIGINT cents" in CLAUDE.md may mean "integer, no floating point" not "multiply by 100". **Planner should confirm with user before writing seed migration.**

3. **payment-service vs wallet-service module boundary**
   - What we know: 8 services exist (catalog-service, payment-service, wallet-service confirmed from `ls services/`)
   - What's unclear: Whether catalog-service is a separate deployed service or a module within payment-service
   - Recommendation: Use payment-service to host both bundle catalog and Azampay payment flow; wallet-service owns the ledger. catalog-service directory may be a placeholder — check build.gradle.kts before creating new service modules.

4. **Single-pending-payment enforcement mechanism**
   - What we know: D-05 requires one pending payment per user
   - What's unclear: Redis lock (with TTL) vs DB partial unique index on `(user_id, status='PENDING')`
   - Recommendation: DB partial unique index is simpler (`CREATE UNIQUE INDEX uq_payments_user_pending ON payments(user_id) WHERE status = 'PENDING'`). Insert fails with constraint violation if a pending payment already exists. Redis lock adds complexity for marginal benefit.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 | Wallet/payment ledger | ✓ (via Testcontainers in tests; DO Managed in prod) | 16 | — |
| RabbitMQ 3 | UserVerified consumer; outbox relay | ✓ (AbstractIntegrationTest already has RabbitMQContainer) | 3-management | — |
| Redis 7 | Single-pending-payment lock; OTP TTL (if used) | ✓ (AbstractIntegrationTest has Redis) | 7 | — |
| Azampay sandbox | AzampayPaymentGateway smoke test | ✓ (confirmed available per project memory 2026-06-19) | — | StubPaymentGateway (always available) |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

> Nyquist validation enabled (workflow.nyquist_validation not set to false).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Testcontainers 1.21.2 (Spring Boot Test) |
| Config file | `build.gradle.kts` per service (`test { useJUnitPlatform() }`) |
| Quick run command | `./gradlew :services:wallet-service:test --tests "*IT" -x` |
| Full suite command | `./gradlew :services:wallet-service:test :services:payment-service:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| WLET-01 | GET /api/v1/wallet/balance returns derived credit sum | Integration (HTTP) | `./gradlew :services:wallet-service:test --tests "BalanceIT"` | ❌ Wave 0 |
| WLET-02 | GET /api/v1/wallet/transactions returns append-only history | Integration (HTTP) | `./gradlew :services:wallet-service:test --tests "TransactionHistoryIT"` | ❌ Wave 0 |
| WLET-03 | Concurrent reservations: second thread blocked; no negative balance | Integration (DB) | `./gradlew :services:wallet-service:test --tests "CreditReservationIT"` | ❌ Wave 0 |
| WLET-04 | Low-credit alert outbox event emitted when balance < threshold | Integration (scheduled) | `./gradlew :services:wallet-service:test --tests "LowCreditAlertIT"` | ❌ Wave 0 |
| WLET-05 | 7-day expiry warning outbox event emitted for expiring lots | Integration (scheduled) | `./gradlew :services:wallet-service:test --tests "ExpiryWarningIT"` | ❌ Wave 0 |
| WLET-06 | Lot with expires_at 12mo past is excluded from balance | Unit | `./gradlew :services:wallet-service:test --tests "CreditLotExpiryTest"` | ❌ Wave 0 |
| WLET-07 | Bonus lot with 30-day TTL excluded after expiry | Unit | included in `CreditLotExpiryTest` | ❌ Wave 0 |
| PYMT-01 | GET /api/v1/bundles returns 5 bundles with correct prices | Integration (HTTP) | `./gradlew :services:payment-service:test --tests "BundleCatalogIT"` | ❌ Wave 0 |
| PYMT-02 | POST /api/v1/payments initiates STK push; payment created PENDING | Integration (stub) | `./gradlew :services:payment-service:test --tests "PaymentInitiationIT"` | ❌ Wave 0 |
| PYMT-03 | PENDING payment flipped to EXPIRED after 2 minutes (Stub: fast-forward) | Integration (scheduled) | `./gradlew :services:payment-service:test --tests "PaymentTimeoutIT"` | ❌ Wave 0 |
| PYMT-04 | Successful callback → credits granted exactly once; outbox event written | Integration | `./gradlew :services:payment-service:test --tests "CallbackProcessingIT"` | ❌ Wave 0 |
| PYMT-05 | GET /api/v1/payments returns paginated payment history | Integration (HTTP) | `./gradlew :services:payment-service:test --tests "PaymentHistoryIT"` | ❌ Wave 0 |
| PYMT-06 | Duplicate callback: second delivery does not double-credit wallet | Integration | included in `CallbackProcessingIT` | ❌ Wave 0 |
| PYMT-07 | EXPIRED payment surfaces to status endpoint; no infinite PENDING | Integration | included in `PaymentTimeoutIT` | ❌ Wave 0 |
| PYMT-08 | Refund API: idempotent credit-back; calling twice does not double-credit | Integration | `./gradlew :services:payment-service:test --tests "RefundIT"` | ❌ Wave 0 |
| (cross) | UserVerified event → 50 bonus credits granted once (idempotent re-delivery) | Integration (AMQP) | `./gradlew :services:wallet-service:test --tests "UserVerifiedConsumerIT"` | ❌ Wave 0 |
| (cross) | Reconciliation job: EXPIRED payment confirmed → credits granted, status=SUCCESS | Integration (scheduled) | `./gradlew :services:payment-service:test --tests "ReconciliationIT"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:wallet-service:test :services:payment-service:test --tests "*Test"` (unit tests only, < 10s)
- **Per wave merge:** full `./gradlew :services:wallet-service:test :services:payment-service:test` (ITs with containers)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `services/wallet-service/src/test/java/com/opendesk/wallet/AbstractWalletIntegrationTest.java` — PG16 + RabbitMQ + Redis @ServiceConnection base (copy from identity-service)
- [ ] `services/wallet-service/src/test/java/com/opendesk/wallet/BalanceIT.java`
- [ ] `services/wallet-service/src/test/java/com/opendesk/wallet/CreditReservationIT.java`
- [ ] `services/wallet-service/src/test/java/com/opendesk/wallet/UserVerifiedConsumerIT.java`
- [ ] `services/payment-service/src/test/java/com/opendesk/payment/AbstractPaymentIntegrationTest.java`
- [ ] `services/payment-service/src/test/java/com/opendesk/payment/BundleCatalogIT.java`
- [ ] `services/payment-service/src/test/java/com/opendesk/payment/CallbackProcessingIT.java`
- [ ] `services/payment-service/src/test/java/com/opendesk/payment/ReconciliationIT.java`
- [ ] Stub IT placeholder per requirement (one stub per REQ, matching Phase 2 02-01 convention)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT via shared-security NimbusJwtDecoder (Phase 2) — all wallet/payment endpoints require valid JWT |
| V3 Session Management | no | Stateless JWT; no session |
| V4 Access Control | yes | Users may only read/modify their own wallet/payments — `userId` extracted from JWT claim, never from request body |
| V5 Input Validation | yes | `@Valid` on all DTOs; amount > 0; provider enum validation |
| V6 Cryptography | conditional | HMAC signature validation for Azampay webhook — if Azampay provides it |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Double-credit via duplicate callback | Tampering | `processed_events` ON CONFLICT DO NOTHING; payment status idempotent guard |
| Negative balance via concurrent reservation | Tampering | SELECT FOR UPDATE on lots; consistent lock order |
| IDOR: user reads another user's wallet | Elevation of Privilege | Extract userId from JWT claim only; ignore any userId in request body/path for data access |
| Azampay callback spoofing (fake success) | Spoofing | HMAC signature validation (stub in dev, real in prod); verify utilityRef exists in our DB |
| Replay attack on callback | Repudiation | `processed_events` idempotency table; same event_id → skip |
| Negative amount in refund or grant | Tampering | Validate `delta > 0` in all credit-granting paths; refund must reference a real payment |

---

## Sources

### Primary (HIGH confidence)
- `CONTEXT.md` (03-CONTEXT.md) — 10 locked decisions D-01..D-10; read first
- `CLAUDE.md` — Azampay integration pattern, RestClient, Resilience4j, idempotency, TZS BIGINT, Flyway, virtual threads
- `02-06-SUMMARY.md` — identity.events exchange topology, routing key `identity.UserVerified`, UserVerifiedEvent shape, OutboxRelay pattern

### Secondary (MEDIUM confidence)
- [pkg.go.dev/github.com/kateile/go-azampay](https://pkg.go.dev/github.com/kateile/go-azampay) — Azampay API shape: auth endpoints, mobileCheckout request fields, callback payload fields, base URLs (sandbox vs prod)
- [github.com/Neurotech-HQ/azampay](https://github.com/Neurotech-HQ/azampay) — Python SDK confirming callback payload structure (msisdn, amount, utilityref, operator, reference, transactionstatus)

### Tertiary (LOW confidence)
- General Spring Boot transactional ledger patterns — training data; no Context7 lookup performed (locked stack, no library uncertainty)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — locked in CLAUDE.md; all libraries from Spring BOM
- Architecture: HIGH — derived from locked decisions + Phase 2 established patterns
- Azampay API shape: MEDIUM — confirmed from Go SDK + Python SDK source; official docs not accessible
- Pitfalls: HIGH — PostgreSQL SELECT FOR UPDATE deadlock + idempotency patterns are well-documented

**Research date:** 2026-06-20
**Valid until:** 2026-07-20 (Azampay API shape: re-verify during sandbox integration)
