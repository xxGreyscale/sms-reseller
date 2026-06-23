# Phase 4: Contacts & Messaging — Research

**Researched:** 2026-06-21
**Domain:** Spring Boot 3.5.9 / Java 21 — Contact management + bulk SMS campaigns + credit lifecycle + RabbitMQ quorum queue DLX retry + sender-ID state machine
**Confidence:** HIGH (codebase patterns verified; external lib versions registry-confirmed; RabbitMQ/Spring AMQP config from official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01** Credit flow: RESERVE at campaign QUEUED → CONSUME when SmsProvider accepts the message. Suppressed / invalid / cancelled → RELEASE. Accepted-then-permanently-failed → REFUND credit-back lot.
- **D-02** Ledger txn types `RESERVE | CONSUME | RELEASE | EXPIRE | REFUND` are reused from Phase 3, not redefined.
- **D-03** Reserve is a **synchronous REST call** from messaging-service to wallet-service on the "send campaign" request path. Wrapped in Resilience4j (circuit breaker + timeout). `campaignId` is the idempotency key.
- **D-04** Consume / Release / Refund are **AMQP events**, NOT sync HTTP. messaging-service emits per-message events (`MessageAccepted`, `MessageReleased`, `MessageRefundDue`) on `messaging.events` topic exchange; wallet-service consumes idempotently (`processed_events` + ON CONFLICT DO NOTHING).
- **D-05** One queue message per recipient — enables per-message delivery status, isolated retry, and per-message refund.
- **D-06** Quorum queue + DLX, `default-requeue-rejected: false`. Up to 3 retries with exponential backoff (~1 min / 5 min / 15 min).
- **D-07** "Permanently undeliverable" = max retries exhausted OR SmsProvider hard-fail code. On permanent failure → message FAILED + refund-due event.
- **D-08** Dedup and suppression scoped per-user globally — a phone number is a single contact across the whole account.
- **D-09** CSV import normalizes TZ numbers to E.164, deduplicates, returns import summary (imported / duplicates skipped / invalid).
- **D-10** Scheduled campaigns dispatched by a DB-polled `@Scheduled` job. Cancellation flips state; poller only dispatches SCHEDULED campaigns at/after their dispatch time.
- **D-11** Phase 4 builds the backend only: sender-ID request submission, `REQUESTED → APPROVED | REJECTED` state machine, internal approve/reject API, outbox notification event. Admin panel UI is Phase 5.
- **D-12** `SmsProvider` interface with `StubSmsProvider` (`@Profile("stub")`) and `RealSmsProvider` (`@Profile("prod")`).

### Claude's Discretion

- Exact event names + payloads for the consume/release/refund AMQP contract.
- Delivery-receipt ingestion shape (stub-simulated now; real DLR webhook later mirroring Azampay callback).
- Campaign/message state-machine enums.
- Character-counter/encoding logic (MESG-02).
- Reservation → lot correlation so CONSUME/RELEASE hit the right reserved credits.

### Deferred Ideas (OUT OF SCOPE)

- Admin approval panel UI for sender-ID requests (SNDR-03 screen) — Phase 5.
- Notification delivery (SMS/email/in-app) of delivery summaries and sender-ID outcomes — Phase 5.
- Real SMS provider + real DLR webhook.
- Mobile-app contact groups + campaign scheduling — post-launch UI only; backend still builds them.
- Message templates.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONT-01 | User can add individual contacts manually (name + phone number) | Standard JPA entity + REST controller + `@Valid` on request DTO |
| CONT-02 | User can edit existing contacts | PATCH/PUT endpoint; IDOR guard via JWT sub |
| CONT-03 | User can delete contacts | DELETE endpoint; cascade removal from groups |
| CONT-04 | User can organize contacts into named groups | `contact_groups` + `contact_group_members` join table |
| CONT-05 | User can import contacts from CSV file | commons-csv streaming parser; `@RequestParam MultipartFile` |
| CONT-06 | System automatically deduplicates on import and within groups | Unique index on `(user_id, phone_e164)` in `contacts` table |
| CONT-07 | System normalizes TZ numbers to E.164 (07xx/06xx → +255XXXXXXXXX) | libphonenumber `PhoneNumberUtil.parse(number, "TZ")` |
| CONT-08 | User can add numbers to suppression list; suppressed excluded from all campaigns | `suppressed_numbers` table; join at campaign dispatch |
| CONT-09 | System shows import summary (X imported, Y duplicates, Z invalid) | Accumulate counts during import loop; return `ImportSummaryResponse` |
| MESG-01 | User can create bulk SMS campaign targeting one or more contact groups | `campaigns` entity + `campaign_groups` join table |
| MESG-02 | Campaign composer shows real-time character counter with SMS part count and UCS-2 warning | Pure logic utility (no lib needed): GSM-7 charset check + part calculation |
| MESG-03 | System reserves credits before campaign QUEUED; refuses with clear error if insufficient | Sync REST to wallet `POST /api/v1/wallet/reservations`; catch `409 Conflict` for insufficient |
| MESG-04 | User can schedule a campaign for a specific future date/time | `scheduled_at` column on campaign; `@Scheduled` dispatcher job |
| MESG-05 | User can cancel a scheduled campaign before dispatch | State flip to CANCELLED; idempotent; dispatcher skips non-SCHEDULED |
| MESG-06 | User can view campaign history with aggregate status (sent/delivered/failed counts) | Derived counts from `outbound_messages` aggregate query |
| MESG-07 | User can view per-message delivery status within a campaign | `outbound_messages` table; status enum per row |
| MESG-08 | User sees post-send confirmation screen showing credits deducted and messages queued | Response DTO from campaign dispatch endpoint |
| MESG-09 | System automatically excludes suppressed numbers from campaign recipients | Pre-expand recipients at dispatch; filter against `suppressed_numbers` |
| MESG-10 | System retries failed messages via DLQ; refunds credits for permanently undeliverable messages | Quorum queue + DLX ladder + `MessageRefundDue` event to wallet |
| SNDR-02 | User can request a custom alphanumeric sender ID | `sender_id_requests` table; `POST /api/v1/sender-ids/requests` endpoint |
| SNDR-03 | Admin can approve or reject sender ID requests | Internal `POST /api/v1/internal/sender-ids/{id}/approve|reject`; role guard |
| SNDR-04 | User receives notification when sender ID approved/rejected | Outbox event `SenderIdDecided` on `messaging.events` exchange |
</phase_requirements>

---

## Summary

Phase 4 introduces two new Spring Boot services — `contact-service` and `messaging-service` — on top of the established foundation from Phases 1–3. Every structural pattern (Flyway migrations, transactional outbox, `processed_events` idempotency, `@Scheduled` jobs, mock-first `@Profile` interfaces, Testcontainers IT base, `SecurityConfig` resource-server, IDOR guard via JWT sub) is already proven in the codebase and should be copied, not reinvented.

The most novel element is the **send pipeline**: expand campaign recipients at dispatch → reserve credits (sync REST) → publish one AMQP message per recipient to a quorum queue → consumer calls SmsProvider → on accept emit `MessageAccepted` (wallet CONSUME); on hard fail or delivery-limit exhaustion emit `MessageRefundDue` (wallet REFUND + creditBack lot). The retry backoff is achieved via a TTL-ladder: three parallel retry queues (TTL 60 s, 300 s, 900 s) fed by a DLX, each DLX-ing back to the primary work queue, with `x-delivery-limit: 3` capping total attempts. CloudAMQP supports the x-delayed-message plugin on dedicated plans, but the TTL-ladder approach is safer, universally available, and simpler to reason about.

**Primary recommendation:** Mirror Phase 3 patterns exactly. The most critical correctness property is the reservation → lot correlation: `ReservationResult.lotIds` must travel with each per-message AMQP payload so wallet's consume/release/refund events target the correct reserved lots without requiring a ledger lookup in messaging.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Contact CRUD, groups, suppression | contact-service (API) | PostgreSQL (`contact` schema) | Service owns all contact state; no cross-service joins |
| CSV import + E.164 normalization | contact-service (API) | — | CPU-light; runs in request thread with virtual threads |
| Campaign create / schedule / cancel | messaging-service (API) | PostgreSQL (`messaging` schema) | Campaigns are messaging-service state; no contact-service DB access |
| Credit reservation | messaging-service (API) → wallet-service (API) | — | Sync REST on request path (D-03); not inside AMQP consumer |
| Send fan-out (per-message AMQP publish) | messaging-service (worker) | RabbitMQ quorum queue | One message per recipient; virtual threads for bulk publish |
| SmsProvider dispatch + delivery receipt | messaging-service (worker) | SmsProvider interface | AMQP consumer calls SmsProvider; emits status events |
| Credit consume / release / refund | wallet-service (AMQP consumer) | PostgreSQL (`wallet` schema) | wallet owns ledger; messaging emits events, wallet acts |
| Delivery status tracking | messaging-service (PostgreSQL) | — | `outbound_messages` table updated by the worker consumer |
| Scheduled campaign dispatch | messaging-service (`@Scheduled`) | — | DB poll pattern (D-10); mirrors existing `VerificationRetryJob` |
| Sender-ID request state machine | messaging-service (API) | PostgreSQL (`messaging` schema) | `sender_id_requests` table; outbox event on decision |
| Notification events (output) | messaging-service (outbox relay) | RabbitMQ `messaging.events` | Phase 5 consumes; this phase only emits |

---

## Standard Stack

### Core (both services — no new additions to the monorepo stack)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| Spring Boot | 3.5.9 | Application framework | CLAUDE.md (locked) |
| Spring Data JPA / Hibernate | BOM-managed | ORM per service | CLAUDE.md |
| Spring Security resource server | BOM-managed | JWT validation via shared-security | CLAUDE.md |
| Spring AMQP (`spring-boot-starter-amqp`) | BOM-managed | RabbitMQ quorum queue + DLX + consumer | CLAUDE.md |
| Flyway 10 + flyway-database-postgresql | BOM-managed | Schema migrations | CLAUDE.md |
| Testcontainers 1.21.2 | BOM-managed | PG16 + RabbitMQ ITs | CLAUDE.md |
| Lombok + MapStruct 1.6.3 | BOM / 1.6.3 | Boilerplate + DTO mapping | CLAUDE.md |
| Resilience4j 2.2.0 | 2.2.0 | Circuit breaker on wallet reserve REST call + SmsProvider prod | CLAUDE.md |

### New dependencies (Phase 4 additions)

| Library | Version | Purpose | Registry |
|---------|---------|---------|----------|
| `commons-csv` (Apache) | **1.14.1** | Streaming CSV parse for contact import | [VERIFIED: Maven Central — 1.14.1 confirmed] |
| `libphonenumber` (Google) | **9.0.32** | E.164 parse/format; TZ region code "TZ" for 07xx/06xx normalization | [VERIFIED: Maven Central — 9.0.32 confirmed] |

`google-libphonenumber` (npm) is a separate JS package — the JVM artifact is `com.googlecode.libphonenumber:libphonenumber`. No npm involvement.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `commons-csv` | `opencsv` | Both are listed in CLAUDE.md. `commons-csv` is simpler for streaming large files (no field-mapping ceremony, Reader-based API). `opencsv` is better for annotated bean mapping. For contact import (name + phone only) `commons-csv` wins on simplicity. |
| TTL-ladder DLX retry | `x-delayed-message` plugin | Plugin available on CloudAMQP dedicated plans but has documented limitations (~100k message cap, not cluster-replicated). TTL-ladder is universally available, deterministic, and idiomatic in Spring AMQP. |
| `libphonenumber` | Custom regex | `libphonenumber` handles Tanzanian numbering plan correctly (07xx/06xx mobile prefixes, +255 country code). Custom regex is brittle and misses edge cases (number too long/short, non-mobile prefixes). |

**Gradle additions (contact-service and messaging-service `build.gradle`):**
```groovy
dependencies {
    // Contact service only
    implementation 'org.apache.commons:commons-csv:1.14.1'
    implementation 'com.googlecode.libphonenumber:libphonenumber:9.0.32'
}
```

---

## Package Legitimacy Audit

slopcheck was installed (0.6.1) but its `slopcheck install` command did not produce JSON output for Maven artifacts (it targets npm/PyPI). Both packages verified via Maven Central search API.

| Package | Registry | Age | Source Repo | Disposition |
|---------|----------|-----|-------------|-------------|
| `org.apache.commons:commons-csv` | Maven Central | 12+ years | github.com/apache/commons-csv | Approved — Apache top-level project |
| `com.googlecode.libphonenumber:libphonenumber` | Maven Central | 12+ years | github.com/google/libphonenumber | Approved — Google open source |

**Packages removed due to slopcheck:** none.
**Packages flagged as suspicious:** none.

---

## Architecture Patterns

### System Architecture Diagram

```
Flutter/Web client
       │  POST /campaigns  (send or schedule)
       ▼
messaging-service (API thread — virtual thread)
  1. Expand recipients: contact-service DB query* OR direct REST call
  2. Filter suppressed numbers (contact-service suppression table)
  3. POST /api/v1/wallet/reservations  ──► wallet-service (sync REST, Resilience4j CB)
     returns ReservationResult { lotIds, reservedCount }
  4. Persist Campaign(QUEUED) + OutboundMessage rows (PENDING) + lotId per message
  5. Publish N AMQP messages to messaging.send (quorum queue)
  6. Return 202 + CampaignDispatchResponse { campaignId, recipientCount, creditsReserved }

       │ AMQP  (one msg per recipient)
       ▼
messaging.send  [quorum queue, x-delivery-limit=3, DLX=messaging.retry.dlx]
       │
       ▼
SendMessageConsumer (messaging-service worker — @RabbitListener)
  ├─► SmsProvider.send(phoneE164, body, senderId)  [Resilience4j CB]
  │    ├─ ACCEPTED  → update OutboundMessage(SENT)
  │    │              publish MessageAccepted { messageId, lotId } → messaging.events
  │    │                         ▼
  │    │              wallet-service consumer → CONSUME credit on lotId
  │    │
  │    ├─ HARD FAIL → nack (no requeue) → DLX routes to messaging.retry.dlx
  │    │              (also treated as permanent failure at consumer level)
  │    │
  │    └─ TRANSIENT FAIL → nack (no requeue) → DLX → retry queue (TTL ladder)
  │
  └─► on x-delivery-limit exhausted (broker dead-letters automatically)
       ▼
messaging.dead [poison queue — dead from retry ladder or delivery limit]
       │
       ▼
DeadLetterConsumer
  update OutboundMessage(FAILED)
  publish MessageRefundDue { messageId, lotId } → messaging.events
                     ▼
  wallet-service consumer → REFUND via LotService.creditBack(userId, 1, messageId)

DLR (delivery receipt) path — stub-simulated:
StubSmsProvider fires DeliveryReceiptEvent after configurable delay
  → MessagingService.handleDeliveryReceipt(msgId, DLR_STATUS)
  → update OutboundMessage(DELIVERED | FAILED)
  → (future) real webhook POST /api/v1/messaging/dlr mirrors Azampay callback pattern
```

*Note: messaging-service accesses its OWN contacts schema (contact-service and messaging-service share the PostgreSQL cluster but own separate schemas). No cross-service DB joins — contacts are expanded by querying the `contact` schema directly from the messaging-service worker, OR via an internal REST call to contact-service. The REST approach is cleaner for service boundary; the direct schema approach avoids latency on large sends. This is an open question — see Open Questions.*

### Recommended Project Structure

```
services/
├── contact-service/
│   └── src/main/java/com/smsreseller/contact/
│       ├── config/              # SecurityConfig, RabbitMqConfig (messaging.events passive bind)
│       ├── contact/             # Contact entity, ContactRepository, ContactService, ContactController
│       ├── group/               # ContactGroup, GroupMembership, GroupService, GroupController
│       ├── suppression/         # SuppressedNumber, SuppressionService, SuppressionController
│       ├── csv/                 # CsvImportService, PhoneNormalizer, ImportSummaryResponse
│       └── outbox/              # OutboxEntry, OutboxRepository, OutboxRelay (copy from identity)
├── messaging-service/
│   └── src/main/java/com/smsreseller/messaging/
│       ├── config/              # SecurityConfig, RabbitMqConfig (declares messaging.events, DLX queues)
│       ├── campaign/            # Campaign, CampaignStatus, CampaignService, CampaignController
│       ├── message/             # OutboundMessage, MessageStatus, SendMessageConsumer, DeadLetterConsumer
│       ├── sms/                 # SmsProvider interface, StubSmsProvider, RealSmsProvider
│       ├── wallet/              # WalletReservationClient (RestClient), MessageEventPublisher
│       ├── scheduler/           # ScheduledCampaignDispatchJob
│       ├── senderid/            # SenderIdRequest, SenderIdStatus, SenderIdService, SenderIdController
│       └── outbox/              # OutboxEntry, OutboxRepository, OutboxRelay (copy pattern)
```

### Pattern 1: Quorum Queue + TTL-Ladder DLX (Spring AMQP QueueBuilder)

**What:** Three retry queues with increasing TTLs (60 s, 300 s, 900 s) route via DLX back to the primary work queue. A poison/dead queue catches messages that exhaust all retries or receive a hard-fail.

**Why TTL-ladder over `x-delayed-message`:** TTL-ladder works on all CloudAMQP plans (including shared); `x-delayed-message` plugin requires dedicated plans and has documented memory and replication limitations. [CITED: CloudAMQP delayed messages docs]

**When to use:** Any queue where per-message retry with backoff is needed without blocking the primary consumer.

```java
// Source: Spring AMQP QueueBuilder official docs + RabbitMQ quorum queue docs
@Configuration
public class MessagingRabbitMqConfig {

    // Primary work queue
    public static final String SEND_QUEUE        = "messaging.send";
    public static final String DLX               = "messaging.retry.dlx";
    public static final String DEAD_QUEUE        = "messaging.dead";
    public static final String RETRY_1_QUEUE     = "messaging.retry.1m";   // TTL 60 000 ms
    public static final String RETRY_2_QUEUE     = "messaging.retry.5m";   // TTL 300 000 ms
    public static final String RETRY_3_QUEUE     = "messaging.retry.15m";  // TTL 900 000 ms

    @Bean
    public TopicExchange messagingEventsExchange() {
        return new TopicExchange("messaging.events", true, false);
    }

    @Bean
    public DirectExchange retryDlx() {
        return new DirectExchange(DLX, true, false);
    }

    // Primary quorum queue — delivery limit caps total attempts at 3
    // On nack-without-requeue (default-requeue-rejected=false) broker routes to DLX
    @Bean
    public Queue sendQueue() {
        return QueueBuilder.durable(SEND_QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RETRY_1_QUEUE)
                .deliveryLimit(3)   // x-delivery-limit: max 3 deliveries total
                .build();
    }

    // Retry queues TTL-ladder: expire → DLX → back to primary work queue
    @Bean
    public Queue retry1Queue() {
        return QueueBuilder.durable(RETRY_1_QUEUE)
                .ttl(60_000)
                .deadLetterExchange("")          // default exchange
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    @Bean
    public Queue retry2Queue() {
        return QueueBuilder.durable(RETRY_2_QUEUE)
                .ttl(300_000)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    @Bean
    public Queue retry3Queue() {
        return QueueBuilder.durable(RETRY_3_QUEUE)
                .ttl(900_000)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SEND_QUEUE)
                .build();
    }

    // Poison / permanently-dead queue
    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).quorum().build();
    }

    // Bindings: DLX routes each retry stage progressively
    @Bean
    public Binding retry1Binding() {
        return BindingBuilder.bind(retry1Queue()).to(retryDlx()).with(RETRY_1_QUEUE);
    }
    @Bean
    public Binding retry2Binding() {
        return BindingBuilder.bind(retry2Queue()).to(retryDlx()).with(RETRY_2_QUEUE);
    }
    @Bean
    public Binding retry3Binding() {
        return BindingBuilder.bind(retry3Queue()).to(retryDlx()).with(RETRY_3_QUEUE);
    }
    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(retryDlx()).with(DEAD_QUEUE);
    }
}
```

**application.yml (messaging-service):**
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false   # MUST be false — poison messages must not re-loop
        acknowledge-mode: manual          # or auto; nack on exception routes to DLX
```

**The retry counter problem:** `x-delivery-limit` on the quorum queue counts delivery attempts on `messaging.send` only. Each time a message returns from a retry queue back to `messaging.send`, it counts as a new delivery. With `deliveryLimit(3)`, the sequence is:

1. Deliver attempt 1 → nack → DLX → `messaging.retry.1m` (TTL 60 s) → back to `messaging.send`
2. Deliver attempt 2 → nack → DLX → `messaging.retry.5m` (TTL 300 s) → back to `messaging.send`
3. Deliver attempt 3 → nack → delivery limit exhausted → DLX → `messaging.dead`

`DeadLetterConsumer` on `messaging.dead` handles final disposition (FAILED + refund event).

**Routing key strategy for DLX retry progression:** The initial DLX routing key (`deadLetterRoutingKey`) on `messaging.send` points to `messaging.retry.1m`. But on subsequent failures we need to advance the ladder (retry.1m → retry.5m → retry.15m). Two approaches:

- **Approach A (recommended):** Use the x-delivery-count header (available in Spring AMQP message headers) in the `SendMessageConsumer` to choose which retry queue to dead-letter to. Nack the message; before nacking, republish to the appropriate retry queue directly and ack the original. This gives explicit ladder control.
- **Approach B (simpler):** Let `x-delivery-limit=3` on the quorum queue dead-letter after 3 total attempts; use a fixed retry queue (e.g., only `messaging.retry.5m`) for all retries. Simpler but less granular backoff.

**Recommendation:** Approach A for correctness with the ~1m/5m/15m backoff specified in D-06. The planner should specify this in the plan — it is a non-trivial implementation detail.

### Pattern 2: Per-Message AMQP Payload with Lot Correlation

**What:** Each AMQP message published to `messaging.send` carries the `lotId` reserved for that specific message slot. This eliminates the need for wallet-service to query messaging-service state when consuming events.

```java
// AMQP message payload on messaging.send queue
record SendMessagePayload(
    UUID messageId,
    UUID campaignId,
    UUID userId,
    String phoneE164,
    String body,
    String senderId,
    UUID lotId,          // The specific credit lot reserved for this message
    int   attemptCount   // Incremented in consumer for ladder routing
) {}
```

**Event payloads on `messaging.events`:**
```java
// Wallet-service binds passively to messaging.events exchange
record MessageAccepted(
    String eventId,      // UUID.randomUUID().toString() — idempotency key for processed_events
    UUID   messageId,
    UUID   userId,
    UUID   lotId         // wallet CONSUME against this lot
) {}

record MessageReleased(
    String eventId,
    UUID   messageId,
    UUID   userId,
    UUID   lotId         // wallet RELEASE (suppressed / cancelled / invalid)
) {}

record MessageRefundDue(
    String eventId,
    UUID   messageId,
    UUID   userId,
    UUID   lotId,
    int    creditsToRefund   // always 1 per message
) {}
```

**Wallet-side consumer key pattern** (mirrors `UserVerifiedConsumer` and `PaymentConfirmedConsumer`):
```java
// wallet-service: MessagingEventConsumer
@RabbitListener(bindings = @QueueBinding(
    value = @Queue(name = "wallet.messaging.MessageAccepted", durable = "true"),
    exchange = @Exchange(name = "messaging.events", type = ExchangeTypes.TOPIC, durable = "true"),
    key = "messaging.MessageAccepted"
))
@Transactional
public void onMessageAccepted(MessageAccepted event) {
    if (!processedEventRepository.tryInsert(event.eventId())) return;
    // Apply CONSUME txn against lot
    lotService.consumeFromLot(event.userId(), event.lotId());
}
```

`lotService.consumeFromLot(userId, lotId)` — new method needed in wallet-service (write CONSUME `CreditTransaction`, decrement `lot.consumed++`, decrement `lot.reserved--`).

### Pattern 3: Reservation → Lot-per-Message Mapping

`ReservationService.reserve(userId, count, campaignId)` returns `ReservationResult { List<UUID> lotIds, int reservedCount }`.

**RESOLVED (D-13):** `lotIds` is **per distinct lot touched**, NOT one UUID per credit — an N-credit reservation across 2 lots returns 2 UUIDs, not N. So a naive `lotIds.get(i)` ↔ recipient `i` zip is WRONG. Plan 04-07 Task 1 enriches `ReservationResult` with `List<LotAllocation>{lotId, count}` (preserving legacy `lotIds` for Phase 3 back-compat); messaging-service then fills recipients from `allocation[k].count` recipients per `allocation[k].lotId` (expiry-soonest-first order), persisting that `lotId` on each `OutboundMessage` row and in every AMQP payload so wallet's CONSUME/RELEASE/REFUND target the correct lot.

**Edge case:** `ReservationService.reserve` returns a list of lot UUIDs one-per-credit. For large campaigns this could be a list of 1000+ UUIDs. The planner should ensure the wallet REST response can handle this — consider pagination or a stream if recipient count is very large. At MVP the cap is the user's available balance (practical limit is a few thousand).

### Pattern 4: E.164 Normalization for Tanzania

```java
// Source: google/libphonenumber GitHub README + Maven Central
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.NumberParseException;

@Component
public class PhoneNormalizer {
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    /** Returns E.164 string or throws if number is invalid for TZ. */
    public String normalizeToE164(String raw) throws NumberParseException {
        var parsed = UTIL.parse(raw.strip(), "TZ");
        if (!UTIL.isValidNumber(parsed)) {
            throw new NumberParseException(ErrorType.NOT_A_NUMBER, "Invalid TZ number: " + raw);
        }
        return UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
    }
}
```

Tanzania region code: `"TZ"`. Handles `0712345678` → `+255712345678` and `+255712345678` (already E.164) transparently.

### Pattern 5: CSV Import (commons-csv streaming)

```java
// Source: Apache commons-csv official docs
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

public ImportSummaryResponse importCsv(UUID userId, InputStream csv) throws IOException {
    int imported = 0, duplicates = 0, invalid = 0;
    try (var reader = new InputStreamReader(csv, StandardCharsets.UTF_8);
         var parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
        for (var record : parser) {
            String name = record.get("name");
            String rawPhone = record.get("phone");
            try {
                String e164 = phoneNormalizer.normalizeToE164(rawPhone);
                boolean inserted = contactService.insertIfAbsent(userId, name, e164);
                if (inserted) imported++; else duplicates++;
            } catch (Exception ex) {
                invalid++;
                // Log row number + raw value; do not abort the import
            }
        }
    }
    return new ImportSummaryResponse(imported, duplicates, invalid);
}
```

`insertIfAbsent` uses `INSERT INTO contacts (user_id, name, phone_e164) VALUES (...) ON CONFLICT (user_id, phone_e164) DO NOTHING` returning rows affected.

### Pattern 6: GSM-7 / UCS-2 Character Counter (no external lib)

**What:** Pure utility logic — no library needed. GSM-7 uses 7-bit encoding (160 chars/part; 153 per part in multipart). UCS-2 uses 16-bit encoding (70 chars/part; 67 per part in multipart). Extended GSM-7 characters (`{`, `}`, `[`, `]`, `~`, `\`, `|`, `€`, `^`) count as 2 characters each.

```java
// [ASSUMED] — standard SMS encoding rules, confirmed via industry sources
public class SmsEncoder {
    // GSM-7 basic character set (128 chars) + extended set (escaped chars count as 2)
    private static final String GSM7_CHARS =
        "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ !\"#¤%&'()*+,-./0123456789:;<=>?" +
        "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM7_EXTENDED = "{}[]~\\|€^";

    public SmsEncoding detect(String text) {
        for (char c : text.toCharArray()) {
            if (!isGsm7(c)) return SmsEncoding.UCS2;
        }
        return SmsEncoding.GSM7;
    }

    public int charCount(String text, SmsEncoding encoding) {
        if (encoding == SmsEncoding.UCS2) return text.length();
        return (int) text.chars().mapToLong(c ->
            GSM7_EXTENDED.indexOf(c) >= 0 ? 2 : 1).sum();
    }

    public int partCount(int charCount, SmsEncoding encoding) {
        int single = encoding == SmsEncoding.GSM7 ? 160 : 70;
        int multi  = encoding == SmsEncoding.GSM7 ? 153 : 67;
        if (charCount <= single) return 1;
        return (int) Math.ceil((double) charCount / multi);
    }
}
```

MESG-02 is a **UI feature** (character counter in the campaign composer). The backend exposes this logic as a utility/helper for the API response or validates message length server-side. The Flutter/web client implements its own counter using the same algorithm.

### Pattern 7: Sender-ID State Machine

```
REQUESTED ──(admin approve)──► APPROVED
          ──(admin reject) ──► REJECTED
```

`SenderIdStatus` enum: `REQUESTED | APPROVED | REJECTED`

Internal API (no JWT user role required — admin-only in Phase 5; for Phase 4 provide the endpoint secured behind `ROLE_ADMIN` even though the admin UI is Phase 5):

```
POST /api/v1/sender-ids/requests              — user submits (JWT user)
GET  /api/v1/sender-ids/requests              — user views own requests
POST /api/v1/internal/sender-ids/{id}/approve — admin decides (ROLE_ADMIN or service token)
POST /api/v1/internal/sender-ids/{id}/reject  — admin decides
```

On approve/reject: update `SenderIdRequest.status`, write `SenderIdDecided` outbox entry → `messaging.events` exchange with routing key `messaging.SenderIdDecided`. Phase 5 notification-service binds to this event.

### Pattern 8: Scheduled Campaign Dispatcher

Mirrors `VerificationRetryJob` / `ReconciliationJob` pattern exactly:

```java
@Component
@RequiredArgsConstructor
public class ScheduledCampaignDispatchJob {

    @Scheduled(fixedDelay = 30_000)  // poll every 30 seconds
    public void dispatch() {
        dispatch(Instant.now());
    }

    // Testable method — @Scheduled delegates to this
    public void dispatch(Instant now) {
        List<Campaign> due = campaignRepository
            .findByStatusAndScheduledAtBefore(CampaignStatus.SCHEDULED, now, PageRequest.of(0, 50));
        for (Campaign c : due) {
            try {
                campaignService.executeSend(c);
            } catch (Exception ex) {
                log.warn("Dispatch failed for campaign {}: {}", c.getId(), ex.getMessage());
            }
        }
    }
}
```

Cancellation is a state flip: `campaign.setStatus(CANCELLED)` + save. The dispatcher's `findByStatus(SCHEDULED)` never sees it.

### Anti-Patterns to Avoid

- **Sync HTTP inside AMQP consumer:** `SendMessageConsumer` MUST NOT call wallet-service synchronously. All credit operations are AMQP events (D-04). Only the send-campaign request-path calls wallet synchronously (D-03).
- **Batching messages in AMQP:** One queue message per recipient (D-05). Do not bundle all recipients in one payload — breaks per-message retry and per-message refund.
- **Storing lot correlation in wallet:** The `lotId` per message must be carried in the AMQP payload from messaging-service. Wallet must not look up campaign state — it only sees the event.
- **Infinite retry loop:** `default-requeue-rejected: false` is mandatory. Without it a nacked message re-enters `messaging.send` indefinitely, never reaching the DLX.
- **Cross-service DB joins:** messaging-service must not query the `contact` schema tables directly via JPA. Use either an internal REST call to contact-service or acknowledge that services share a logical PG database (not schemas) and document the tradeoff as an open question.
- **Sharing `OutboxEntry` entity across services:** Each service copies the outbox pattern independently. No shared `outbox` module — service boundary must be honoured.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Phone number parsing and E.164 format | Custom regex for `07xx → +255xx` | `libphonenumber` | Tanzania numbering plan has edge cases (invalid lengths, non-mobile prefixes); libphonenumber includes complete NANP + ITU metadata |
| CSV parsing | Custom `split(",")` parser | `commons-csv` | Handles quoted fields, escaped commas, multi-line values, encoding; contact CSV will have user-supplied dirty data |
| Idempotent AMQP consumer guard | SELECT-then-INSERT check | `processed_events` `tryInsert` (Phase 3 pattern) | Eliminates SELECT-INSERT race; atomic at DB level |
| Credit double-spend prevention | Application-level balance check | Phase 3 `ReservationService.reserve` with `SELECT FOR UPDATE` | Concurrent campaign sends from same user would race without the DB-level lock |
| Delivery-receipt retry/backoff | In-memory retry in consumer | RabbitMQ quorum queue + DLX TTL-ladder | Consumer crash loses in-memory state; broker-side durability survives pod restarts |

---

## Common Pitfalls

### Pitfall 1: `default-requeue-rejected` Not Set to `false`

**What goes wrong:** A nacked message re-enters `messaging.send` immediately without DLX routing, creating an infinite tight loop that exhausts consumer threads and floods logs.
**Why it happens:** Default Spring AMQP behaviour requeues on nack.
**How to avoid:** Set `spring.rabbitmq.listener.simple.default-requeue-rejected=false` in `application.yml` (or `direct` if using `DirectMessageListenerContainer`). This is a CLAUDE.md requirement.
**Warning signs:** CPU spike in messaging-service worker; same message ID appearing hundreds of times in logs within seconds.

### Pitfall 2: Quorum Queue vs Classic Queue Declaration Mismatch

**What goes wrong:** Declaring a queue as `x-queue-type: classic` (the default) and then trying to change it to `quorum` later fails with `inequivalent arg` error from RabbitMQ. Queue must be deleted and re-declared.
**Why it happens:** Spring AMQP's `QueueBuilder.durable(name).build()` creates a classic queue if `.quorum()` is not explicitly called.
**How to avoid:** Always use `.quorum()` on `QueueBuilder` for `messaging.send` and `messaging.dead`. In dev, ensure Docker Compose RabbitMQ is reset if queues were previously created as classic.
**Warning signs:** `com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=406 ...)` on startup.

### Pitfall 3: Delivery Limit Counter and Retry Queue Round-Trip

**What goes wrong:** `x-delivery-limit=3` counts deliveries on `messaging.send`. If a message goes: `send → retry.1m → send → retry.5m → send → dead`, that is 3 deliveries on `messaging.send`, which is correct. But if the DLX routing key is wrong (points to `messaging.dead` instead of `messaging.retry.1m`), all messages go straight to dead queue on first failure.
**Why it happens:** `deadLetterRoutingKey` on the quorum queue declaration determines where nacked messages go. Must match a binding on the DLX.
**How to avoid:** Verify DLX bindings in the RabbitMQ management UI after first startup. Write an IT that asserts message arrives in `messaging.retry.1m` after a single nack.

### Pitfall 4: `lotIds` List Mismatch on Large Campaigns

**What goes wrong:** `ReservationResult.lotIds` from wallet-service contains one UUID per credit reserved. A 500-recipient campaign reserves 500 credits and gets a list of 500 lot UUIDs (which may be the same lot ID repeated if the lot has enough credits). Messaging-service must zip recipient → lotId correctly or CONSUME events target the wrong lot.
**Why it happens:** `ReservationService.reserve` page-walks lots in expiry order. A single lot with 1000 available credits produces a list of 1000 identical lot UUIDs.
**How to avoid:** In `OutboundMessage`, store the specific `lotId` from the zipped pair. The AMQP payload carries this `lotId`. CONSUME events reference it directly. No secondary lookup needed.

### Pitfall 5: CSV Import Blocking the Request Thread for Large Files

**What goes wrong:** A 50,000-row CSV import blocks the HTTP thread for minutes, exhausting the thread pool.
**Why it happens:** Synchronous streaming on a request thread is fine for small files (< 5,000 rows) but degrades under load. Java 21 virtual threads mitigate this significantly.
**How to avoid:** Virtual threads (`spring.threads.virtual.enabled=true`, already in the stack) make blocking I/O cheap. For MVP (small organizations), synchronous streaming is acceptable. If files exceed 10,000 rows, consider async job + polling endpoint — defer to post-launch.

### Pitfall 6: `SmsProvider` Mock not Exercising Failure Paths

**What goes wrong:** `StubSmsProvider` only returns success, so the DLX retry path is never tested before prod.
**How to avoid:** `StubSmsProvider` must support configurable outcomes per number or via a test control: `SUCCESS`, `HARD_FAIL` (invalid number), `TRANSIENT_FAIL` (provider overloaded). The IT suite must exercise all three paths. Mirror `StubPaymentGateway` from Phase 3 which supports `SUCCESS | TIMEOUT | FAILURE` outcomes.

### Pitfall 7: IDOR on Campaign / Contact Endpoints

**What goes wrong:** A user queries `/api/v1/campaigns/{id}` for another user's campaign and sees their contacts or message content.
**Why it happens:** Endpoints that filter by path-variable `id` without also filtering by `userId` from JWT.
**How to avoid:** All repository queries include `userId` from `auth.getSubject()`. Return 404 (not 403) when the record exists but belongs to another user (avoids information leakage about resource existence).

---

## Validation Architecture

> `workflow.nyquist_validation` not explicitly set to false in config; section is required.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Testcontainers 1.21.2 + Spring Boot Test |
| Config file | inherited from Phase 3 base (`AbstractWalletIntegrationTest` pattern — mirror as `AbstractContactIntegrationTest` + `AbstractMessagingIntegrationTest`) |
| Quick run command | `./gradlew :services:contact-service:test :services:messaging-service:test` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONT-01 | Add individual contact; persisted; returned in list | IT | `./gradlew :services:contact-service:test --tests ContactCrudIT.addContactPersistsAndIsRetrievable` | ❌ Wave 0 |
| CONT-02 | Edit contact name/phone; change persisted | IT | `./gradlew :services:contact-service:test --tests ContactCrudIT.editContactUpdatesFields` | ❌ Wave 0 |
| CONT-03 | Delete contact; removed from list and groups | IT | `./gradlew :services:contact-service:test --tests ContactCrudIT.deleteContactRemovesFromGroups` | ❌ Wave 0 |
| CONT-04 | Create group; add/remove contacts; group membership persisted | IT | `./gradlew :services:contact-service:test --tests ContactGroupIT.groupMembershipIsPersisted` | ❌ Wave 0 |
| CONT-05 | Upload CSV; contacts inserted | IT | `./gradlew :services:contact-service:test --tests CsvImportIT.csvUploadInsertsContacts` | ❌ Wave 0 |
| CONT-06 | CSV import with duplicate phone; duplicate count = 1, not inserted twice | IT | `./gradlew :services:contact-service:test --tests CsvImportIT.duplicatePhoneSkippedAndCounted` | ❌ Wave 0 |
| CONT-07 | `07xx` number normalizes to `+255xx` | Unit | `./gradlew :services:contact-service:test --tests PhoneNormalizerTest.tanzanianMobileNormalizesToE164` | ❌ Wave 0 |
| CONT-08 | Suppressed number excluded from campaign expansion | IT | `./gradlew :services:contact-service:test --tests SuppressionIT.suppressedNumberIsExcluded` | ❌ Wave 0 |
| CONT-09 | Import summary counts correct for mixed valid/duplicate/invalid rows | IT | `./gradlew :services:contact-service:test --tests CsvImportIT.importSummaryCountsAreCorrect` | ❌ Wave 0 |
| MESG-01 | Create campaign targeting groups; campaign in DRAFT state | IT | `./gradlew :services:messaging-service:test --tests CampaignIT.createCampaignTargetingGroups` | ❌ Wave 0 |
| MESG-02 | GSM-7 160 chars = 1 part; 161 chars = 2 parts; Swahili char triggers UCS-2 | Unit | `./gradlew :services:messaging-service:test --tests SmsEncoderTest.partCountAndEncodingDetection` | ❌ Wave 0 |
| MESG-03 | Send campaign with insufficient credits → 402/409 response | IT | `./gradlew :services:messaging-service:test --tests CampaignIT.insufficientCreditsBlocksQueuedTransition` | ❌ Wave 0 |
| MESG-04 | Schedule campaign for future time; poller dispatches at/after time | IT | `./gradlew :services:messaging-service:test --tests ScheduledCampaignIT.pollerDispatchesAtScheduledTime` | ❌ Wave 0 |
| MESG-05 | Cancel scheduled campaign; poller skips it | IT | `./gradlew :services:messaging-service:test --tests ScheduledCampaignIT.cancelledCampaignNotDispatched` | ❌ Wave 0 |
| MESG-06 | Campaign aggregate status counts correct after send | IT | `./gradlew :services:messaging-service:test --tests CampaignStatusIT.aggregateStatusCountsAreCorrect` | ❌ Wave 0 |
| MESG-07 | Per-message status visible; PENDING → SENT → DELIVERED via stub DLR | IT | `./gradlew :services:messaging-service:test --tests DeliveryTrackingIT.perMessageStatusTransitions` | ❌ Wave 0 |
| MESG-08 | Dispatch response includes credits reserved and message count | IT | `./gradlew :services:messaging-service:test --tests CampaignIT.dispatchResponseIncludesCreditsAndCount` | ❌ Wave 0 |
| MESG-09 | Suppressed recipient excluded from AMQP messages published | IT | `./gradlew :services:messaging-service:test --tests SendPipelineIT.suppressedNumberNotPublished` | ❌ Wave 0 |
| MESG-10 | Hard-fail nack → DLX → retry queue → exhausted → dead queue; refund event emitted | IT | `./gradlew :services:messaging-service:test --tests DlxRetryIT.permanentFailureEmitsRefundDueEvent` | ❌ Wave 0 |
| SNDR-02 | User submits sender ID request; status REQUESTED | IT | `./gradlew :services:messaging-service:test --tests SenderIdIT.userCanSubmitRequest` | ❌ Wave 0 |
| SNDR-03 | Admin approves request; status APPROVED; outbox event written | IT | `./gradlew :services:messaging-service:test --tests SenderIdIT.adminApproveTransitionsToApproved` | ❌ Wave 0 |
| SNDR-04 | Outbox event `SenderIdDecided` published on `messaging.events` | IT | `./gradlew :services:messaging-service:test --tests SenderIdIT.senderIdDecidedEventPublished` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:contact-service:test` or `./gradlew :services:messaging-service:test` (whichever service the task touches)
- **Per wave merge:** `./gradlew test` (full monorepo)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps (all files — two new services)

Both services are new. Wave 0 must create:

**contact-service:**
- [ ] `AbstractContactIntegrationTest.java` — Testcontainers PG16 + `@ServiceConnection` (mirror `AbstractWalletIntegrationTest`)
- [ ] `ContactCrudIT.java` — placeholder tests for CONT-01/02/03
- [ ] `ContactGroupIT.java` — placeholder for CONT-04
- [ ] `CsvImportIT.java` — placeholder for CONT-05/06/09
- [ ] `PhoneNormalizerTest.java` — unit test for CONT-07
- [ ] `SuppressionIT.java` — placeholder for CONT-08
- [ ] `build.gradle` with deps (Spring Boot, Flyway, Testcontainers PG16, commons-csv, libphonenumber)

**messaging-service:**
- [ ] `AbstractMessagingIntegrationTest.java` — Testcontainers PG16 + RabbitMQ + `@ServiceConnection`
- [ ] `CampaignIT.java` — placeholder for MESG-01/03/08
- [ ] `SmsEncoderTest.java` — unit test for MESG-02
- [ ] `ScheduledCampaignIT.java` — placeholder for MESG-04/05
- [ ] `CampaignStatusIT.java` — placeholder for MESG-06
- [ ] `DeliveryTrackingIT.java` — placeholder for MESG-07
- [ ] `SendPipelineIT.java` — placeholder for MESG-09
- [ ] `DlxRetryIT.java` — placeholder for MESG-10
- [ ] `SenderIdIT.java` — placeholder for SNDR-02/03/04
- [ ] `build.gradle` with deps (Spring Boot, Flyway, Testcontainers PG16 + RabbitMQ, Resilience4j)

---

## Database Schema

### contact schema (contact-service)

```sql
-- V1__create_contacts.sql
CREATE TABLE contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    name        VARCHAR(255),
    phone_e164  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_contact_user_phone UNIQUE (user_id, phone_e164)
);
CREATE INDEX idx_contacts_user_id ON contacts (user_id);

-- V2__create_contact_groups.sql
CREATE TABLE contact_groups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_user_name UNIQUE (user_id, name)
);

CREATE TABLE contact_group_members (
    group_id   UUID NOT NULL REFERENCES contact_groups(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, contact_id)
);

-- V3__create_suppressed_numbers.sql
CREATE TABLE suppressed_numbers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    phone_e164  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppressed_user_phone UNIQUE (user_id, phone_e164)
);
```

### messaging schema (messaging-service)

```sql
-- V1__create_campaigns.sql
CREATE TABLE campaigns (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    name          VARCHAR(255),
    body          TEXT NOT NULL,
    sender_id     VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scheduled_at  TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_campaigns_user_id ON campaigns (user_id);
CREATE INDEX idx_campaigns_status_scheduled ON campaigns (status, scheduled_at)
    WHERE status = 'SCHEDULED';

-- V2__create_outbound_messages.sql
CREATE TABLE outbound_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    user_id     UUID NOT NULL,
    phone_e164  VARCHAR(20) NOT NULL,
    lot_id      UUID NOT NULL,        -- credit lot reserved for this message
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_id VARCHAR(255),         -- provider reference (filled on accept)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbound_messages_campaign ON outbound_messages (campaign_id);
CREATE INDEX idx_outbound_messages_status   ON outbound_messages (campaign_id, status);

-- V3__create_sender_id_requests.sql
CREATE TABLE sender_id_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    sender_name VARCHAR(11) NOT NULL,  -- alphanumeric sender ID max 11 chars (GSMA standard)
    status      VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reject_reason TEXT,
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- V4__create_outbox.sql  (copy from identity-service V3)
CREATE TABLE outbox (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload    TEXT NOT NULL,
    sent       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unsent ON outbox (created_at) WHERE sent = FALSE;
```

---

## State Machines

### Campaign Status

```
DRAFT ──(reserve credits)──► QUEUED ──(dispatch job)──► SENDING ──(all messages terminal)──► COMPLETED
      ──(cancel)──────────► CANCELLED
DRAFT ──(set scheduledAt)──► SCHEDULED ──(poller picks up)──► QUEUED ──► ...
SCHEDULED ──(cancel before dispatch)──► CANCELLED
```

| Status | Meaning |
|--------|---------|
| `DRAFT` | Created, not yet sent or scheduled |
| `SCHEDULED` | Dispatched for a future time; poller will pick up |
| `QUEUED` | Credits reserved; messages enqueued to RabbitMQ |
| `SENDING` | At least one message in flight |
| `COMPLETED` | All messages reached terminal status (DELIVERED or FAILED) |
| `CANCELLED` | User cancelled before dispatch |

### OutboundMessage Status

```
PENDING ──(SmsProvider accept)──► SENT ──(DLR received)──► DELIVERED
        ──(nack hard-fail)───────────────────────────────► FAILED
SENT ──(DLR failure)──────────────────────────────────────► FAILED
```

---

## AMQP Event Contract (Claude's Discretion — Recommended)

**Exchange:** `messaging.events` (TopicExchange, durable)

| Event | Routing Key | Payload | Consumer |
|-------|------------|---------|----------|
| `MessageAccepted` | `messaging.MessageAccepted` | `{ eventId, messageId, userId, lotId }` | wallet-service → CONSUME |
| `MessageReleased` | `messaging.MessageReleased` | `{ eventId, messageId, userId, lotId }` | wallet-service → RELEASE |
| `MessageRefundDue` | `messaging.MessageRefundDue` | `{ eventId, messageId, userId, lotId, creditsToRefund: 1 }` | wallet-service → REFUND via creditBack |
| `CampaignCompleted` | `messaging.CampaignCompleted` | `{ eventId, campaignId, userId, sent, delivered, failed }` | Phase 5 notification-service |
| `SenderIdDecided` | `messaging.SenderIdDecided` | `{ eventId, requestId, userId, senderName, decision: APPROVED|REJECTED, reason }` | Phase 5 notification-service |

Wallet-service binds passively to `messaging.events` (does NOT redeclare the exchange — mirrors `UserVerifiedConsumer` binding to `identity.events`).

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT resource server via shared-security (Phase 2 pattern) |
| V3 Session Management | no | Stateless JWT; sessions managed by identity-service |
| V4 Access Control | yes | userId from `auth.getSubject()` only; IDOR guard on all campaign/contact endpoints; 404 not 403 for cross-user records |
| V5 Input Validation | yes | `@Valid` on all request DTOs; phone E.164 validated by libphonenumber; sender ID name max 11 chars |
| V6 Cryptography | no | No new crypto; Phase 2 RSA JWT keypair still in use |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR on campaign/contact/group | Elevation of privilege | userId from JWT sub on all queries; 404 on mismatch |
| Duplicate wallet events (CONSUME/RELEASE/REFUND) | Tampering | `processed_events` `tryInsert(eventId)` in wallet consumer |
| Campaign send without reserved credits | Tampering / Spoofing | `QUEUED` transition only after successful `reserve()` response |
| Credit double-spend on concurrent sends | Tampering | Phase 3 `SELECT FOR UPDATE` in `ReservationService.reserve` |
| Poison AMQP message infinite loop | Denial of Service | `default-requeue-rejected: false` + `x-delivery-limit: 3` |
| CSV injection (phone/name with formula prefix) | Tampering | Treat all CSV values as strings; do not render in Excel-consuming contexts at MVP |
| Sender-ID approval bypass | Spoofing | `/internal/` approve/reject endpoint must require `ROLE_ADMIN` or a shared service token verified by Spring Security |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 16 | Both services (Flyway, JPA) | ✓ (DO Managed) | 16 | — |
| RabbitMQ 3.x | messaging-service (quorum queues + DLX) | ✓ (CloudAMQP) | 3.x | — |
| Redis 7 | contact-service unlikely; messaging-service rate limiting optional | ✓ (DO Managed) | 7 | Skip rate limiter at MVP |
| wallet-service REST API | messaging-service (reserve call) | ✓ (Phase 3 built) | Phase 3 | — |

**Missing dependencies with no fallback:** none.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | messaging-service can access the `contact` schema via a direct DB connection (same PG cluster, separate logical DB schemas) to expand recipients at dispatch — OR must go through an internal REST call to contact-service | Architecture Patterns | If cross-schema access is disallowed by policy, recipient expansion requires an internal REST call — adds latency for large campaigns |
| A2 | CloudAMQP shared plan (not dedicated) is used at MVP | Architecture Patterns | x-delayed-message plugin (if needed) requires dedicated plan; TTL-ladder sidesteps this but assumption should be confirmed |
| A3 | `LotService.consumeFromLot(userId, lotId)` method does not exist yet in wallet-service; Phase 4 plan must add it | Credit Lifecycle | If Phase 3 already has it under a different name, rename accordingly |
| A4 | `ReservationResult.lotIds` is a `List<UUID>` with one UUID per credit reserved (not per lot) | Credit Lifecycle | If it is one UUID per lot (not per credit), the zip logic must account for partial lot consumption — look up `ReservationService.reserve` implementation to verify |
| A5 | Sender ID max length is 11 alphanumeric characters (GSMA standard for GSM networks) | Database Schema | TCRA may impose a different limit; confirm during SMS provider onboarding |
| A6 | GSM-7 extended character set logic (escape sequences count as 2) is correctly transcribed | MESG-02 Pattern | If a character is missed, part counts will be off by 1; unit test with known strings mitigates this |

---

## Open Questions (RESOLVED)

> All open questions below were resolved during planning via CONTEXT decisions D-13/D-14/D-15
> and the plan actions in 04-05/04-07. Retained for audit trail.


1. **Recipient expansion — contact-service REST call vs direct schema access?**
   - What we know: both services share the same PG cluster with separate schemas; CLAUDE.md says "do not cross-service sync HTTP in AMQP consumer critical path."
   - What's unclear: the campaign dispatch is initiated on the HTTP request path (not inside an AMQP consumer), so a REST call from messaging-service to contact-service to expand recipients is legal per CLAUDE.md. But it adds an internal HTTP call on every campaign send. Direct schema access (messaging-service queries `contact.contacts` with a schema-qualified query) avoids the hop but blurs service boundaries.
   - Recommendation: Use a REST call to contact-service for cleaner service boundary. Cache the recipient list in `outbound_messages` at creation time so the AMQP consumer never needs to contact contact-service.

2. **`lotIds` list structure from `ReservationService.reserve` — one UUID per credit or per lot?**
   - What we know: `ReservationResult { List<UUID> lotIds, int reservedCount }` from 03-02-SUMMARY.md. The summary says "walks list taking min(available, remaining) per lot" — implies multiple credits can come from a single lot.
   - What's unclear: Does `lotIds` repeat the same lot UUID if multiple credits come from one lot (e.g., `[lotA, lotA, lotA]` for 3 credits from lot A), or does it return distinct lot UUIDs only?
   - Recommendation: Read `ReservationService.reserve` source before planning the zip logic. If it returns `List<UUID> lotIds` where length == reservedCount (one per credit), the zip is straightforward. If it returns distinct lot UUIDs with a count per lot, the zip needs adjustment.

3. **`LotService.consumeFromLot` — does it exist in Phase 3?**
   - What we know: Phase 3 built `grantBonus`, `grantPurchased`, `creditBack`. No `consumeFromLot` was visible in the summaries.
   - What's unclear: Phase 4's wallet consumer needs to apply CONSUME against a specific lot when `MessageAccepted` arrives. Is this a new method, or does `ReservationService.release` + `CreditTransaction(CONSUME)` cover it?
   - Recommendation: Phase 4 plan must include a wallet-service task to add `LotService.consumeFromLot(UUID userId, UUID lotId)` that writes a CONSUME `CreditTransaction` and decrements `lot.consumed++, lot.reserved--`.

4. **RabbitMQ quorum queue TTL support on CloudAMQP shared plan?**
   - What we know: Standard RabbitMQ 3.10+ supports TTL on quorum queues. CloudAMQP manages RabbitMQ.
   - What's unclear: Exact RabbitMQ version on CloudAMQP shared plan; whether the TTL-ladder retry queues (non-quorum classic queues with TTL) are permitted.
   - Recommendation: The retry queues (messaging.retry.1m, .5m, .15m) can be classic durable queues — TTL on classic queues is universally supported. Only the primary `messaging.send` and `messaging.dead` need to be quorum queues. This sidesteps any version concerns.

5. **Delivery receipt (DLR) ingestion — `StubSmsProvider` simulation mechanism?**
   - What we know: `StubSmsProvider` should simulate delivery receipts after a configurable delay (D-12). Real DLR will be a webhook later.
   - Recommendation: Inject a `@Scheduled` job in `StubSmsProvider` that runs every 5 seconds and for each message in SENT status (stored in an in-memory `ConcurrentHashMap`) fires `MessagingService.handleDeliveryReceipt(msgId, DELIVERED)` after a configured delay (default: 10 s). This is testable via `Awaitility`. The real webhook endpoint `POST /api/v1/messaging/dlr` can be added as a stub REST endpoint that delegates to the same `handleDeliveryReceipt` method.

---

## Sources

### Primary (HIGH confidence)
- 03-02-SUMMARY.md — `ReservationService.reserve(userId, count, referenceId) → ReservationResult { List<UUID> lotIds, int reservedCount }` signature; lot structure; `LotService.creditBack` for refund
- 03-04-SUMMARY.md — `processed_events` / `ProcessedEventRepository.tryInsert` idempotency pattern; `@RabbitListener @QueueBinding` passive bind pattern; `UserVerifiedConsumer` template
- 03-06-SUMMARY.md — `PaymentConfirmedConsumer` binding pattern; `OutboxEntry/OutboxRepository/OutboxRelay` wallet copy; `RefundService` using `processed_events` with `"refund:"` prefix
- [Spring AMQP QueueBuilder official docs](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/core/QueueBuilder.html) — `.quorum()`, `.deliveryLimit()`, `.deadLetterExchange()`, `.ttl()` methods confirmed
- [RabbitMQ Quorum Queues official docs](https://www.rabbitmq.com/docs/quorum-queues) — `x-delivery-limit`, dead-lettering, at-least-once dead letter strategy
- Maven Central — `commons-csv:1.14.1`, `libphonenumber:9.0.32` versions confirmed via registry API

### Secondary (MEDIUM confidence)
- [CloudAMQP DLX FAQ](https://www.cloudamqp.com/blog/when-and-how-to-use-the-rabbitmq-dead-letter-exchange.html) — TTL-ladder retry pattern; DLX usage
- [CloudAMQP delayed messages docs](https://www.cloudamqp.com/docs/delayed-messages.html) — x-delayed-message plugin on dedicated plans only
- [google/libphonenumber GitHub](https://github.com/google/libphonenumber) — `PhoneNumberUtil.parse(number, "TZ")` pattern for Tanzania
- [Baeldung: libphonenumber](https://www.baeldung.com/java-libphonenumber) — `PhoneNumberFormat.E164` usage
- [messente/sms-length-calculator](https://github.com/messente/sms-length-calculator) — GSM-7 part counting implementation reference

### Tertiary (LOW confidence)
- Industry-standard SMS encoding rules (GSM-7 charset, extended chars, part sizes) — cross-verified with multiple sources but no single authoritative Java library in the ecosystem; hand-roll recommended and unit-tested

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libs registry-confirmed or from CLAUDE.md
- Architecture: HIGH — patterns directly copied from Phase 3 verified implementations
- RabbitMQ quorum + DLX: HIGH — from official RabbitMQ docs + Spring AMQP official API docs
- CSV/libphonenumber: HIGH — Maven Central confirmed; official Google library
- GSM-7 character counter: MEDIUM — standard algorithm, no authoritative Java lib; unit tests will verify

**Research date:** 2026-06-21
**Valid until:** 2026-08-01 (stable stack — Spring Boot BOM, libphonenumber, commons-csv are all stable)
