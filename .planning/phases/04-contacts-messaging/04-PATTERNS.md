# Phase 4: Contacts & Messaging — Pattern Map

**Mapped:** 2026-06-21
**Files analyzed:** 42 new/modified files across contact-service, messaging-service, and wallet-service additions
**Analogs found:** 38 / 42

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `contact-service/.../contact/Contact.java` | model | CRUD | `payment/.../payment/Payment.java` | exact |
| `contact-service/.../contact/ContactRepository.java` | repository | CRUD | `payment/.../payment/PaymentRepository.java` | exact |
| `contact-service/.../contact/ContactService.java` | service | CRUD | `payment/.../payment/PaymentService.java` | exact |
| `contact-service/.../contact/ContactController.java` | controller | request-response | `payment/.../payment/PaymentController.java` | exact |
| `contact-service/.../group/ContactGroup.java` | model | CRUD | `payment/.../payment/Payment.java` | role-match |
| `contact-service/.../group/GroupService.java` | service | CRUD | `payment/.../payment/PaymentService.java` | role-match |
| `contact-service/.../group/GroupController.java` | controller | request-response | `payment/.../payment/PaymentController.java` | role-match |
| `contact-service/.../suppression/SuppressedNumber.java` | model | CRUD | `payment/.../payment/Payment.java` | role-match |
| `contact-service/.../suppression/SuppressionService.java` | service | CRUD | `payment/.../payment/PaymentService.java` | role-match |
| `contact-service/.../suppression/SuppressionController.java` | controller | request-response | `payment/.../payment/PaymentController.java` | role-match |
| `contact-service/.../csv/PhoneNormalizer.java` | utility | transform | none | no analog |
| `contact-service/.../csv/CsvImportService.java` | service | file-I/O | none | no analog |
| `contact-service/.../csv/ImportSummaryResponse.java` | DTO | transform | `payment/.../payment/PaymentDto.java` | role-match |
| `contact-service/.../config/SecurityConfig.java` | config | request-response | `wallet/.../config/SecurityConfig.java` | exact |
| `contact-service/.../config/RabbitMqConfig.java` | config | event-driven | `wallet/.../config/RabbitMqConfig.java` | exact |
| `contact-service/.../outbox/OutboxEntry.java` | model | event-driven | `identity/.../outbox/OutboxEntry.java` | exact |
| `contact-service/.../outbox/OutboxRepository.java` | repository | event-driven | `identity/.../outbox/OutboxRepository.java` | exact |
| `contact-service/.../outbox/OutboxRelay.java` | scheduler | event-driven | `identity/.../outbox/OutboxRelay.java` | exact |
| `contact-service/build.gradle` | config | — | `payment-service/build.gradle` | role-match |
| `contact-service/AbstractContactIntegrationTest.java` | test | — | `wallet/.../AbstractWalletIntegrationTest.java` | exact |
| `messaging-service/.../campaign/Campaign.java` | model | CRUD | `payment/.../payment/Payment.java` | exact |
| `messaging-service/.../campaign/CampaignStatus.java` | model | CRUD | `payment/.../payment/PaymentStatus.java` | exact |
| `messaging-service/.../campaign/CampaignService.java` | service | request-response | `payment/.../payment/PaymentService.java` | exact |
| `messaging-service/.../campaign/CampaignController.java` | controller | request-response | `payment/.../payment/PaymentController.java` | exact |
| `messaging-service/.../message/OutboundMessage.java` | model | event-driven | `payment/.../payment/Payment.java` | role-match |
| `messaging-service/.../message/MessageStatus.java` | model | event-driven | `payment/.../payment/PaymentStatus.java` | role-match |
| `messaging-service/.../message/SendMessageConsumer.java` | consumer | event-driven | `wallet/.../consumer/UserVerifiedConsumer.java` | exact |
| `messaging-service/.../message/DeadLetterConsumer.java` | consumer | event-driven | `wallet/.../consumer/UserVerifiedConsumer.java` | role-match |
| `messaging-service/.../sms/SmsProvider.java` | interface | request-response | `payment/.../gateway/PaymentGateway.java` | exact |
| `messaging-service/.../sms/StubSmsProvider.java` | service | request-response | `payment/.../gateway/StubPaymentGateway.java` | exact |
| `messaging-service/.../sms/RealSmsProvider.java` | service | request-response | `payment/.../gateway/AzampayPaymentGateway.java` | exact |
| `messaging-service/.../wallet/WalletReservationClient.java` | client | request-response | `payment/.../gateway/AzampayPaymentGateway.java` | exact |
| `messaging-service/.../wallet/MessageEventPublisher.java` | service | event-driven | `identity/.../outbox/OutboxRelay.java` | role-match |
| `messaging-service/.../scheduler/ScheduledCampaignDispatchJob.java` | scheduler | batch | `payment/.../reconciliation/ReconciliationJob.java` | exact |
| `messaging-service/.../senderid/SenderIdRequest.java` | model | CRUD | `payment/.../payment/Payment.java` | role-match |
| `messaging-service/.../senderid/SenderIdService.java` | service | CRUD | `payment/.../payment/PaymentService.java` | role-match |
| `messaging-service/.../senderid/SenderIdController.java` | controller | request-response | `payment/.../payment/PaymentController.java` | role-match |
| `messaging-service/.../outbox/OutboxEntry.java` | model | event-driven | `identity/.../outbox/OutboxEntry.java` | exact |
| `messaging-service/.../outbox/OutboxRelay.java` | scheduler | event-driven | `identity/.../outbox/OutboxRelay.java` | exact |
| `messaging-service/.../config/RabbitMqConfig.java` | config | event-driven | `04-RESEARCH.md §Pattern 1` | no codebase analog |
| `messaging-service/.../config/SecurityConfig.java` | config | request-response | `wallet/.../config/SecurityConfig.java` | exact |
| `messaging-service/.../sms/SmsEncoder.java` | utility | transform | none | no analog |
| `wallet-service/.../consumer/MessagingEventConsumer.java` | consumer | event-driven | `wallet/.../consumer/UserVerifiedConsumer.java` | exact |
| `wallet-service/.../lot/LotService.java` (additions) | service | CRUD | `wallet/.../lot/LotService.java` | exact (modify) |
| `AbstractMessagingIntegrationTest.java` | test | — | `wallet/.../AbstractWalletIntegrationTest.java` | exact |

---

## Pattern Assignments

### `contact-service/.../contact/Contact.java` (model, CRUD)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/payment/Payment.java`

**Entity pattern** (lines 1–100 — full file):
```java
package com.opendesk.payment.payment;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

**Apply to:** `Contact.java`, `ContactGroup.java`, `SuppressedNumber.java`, `Campaign.java`, `OutboundMessage.java`, `SenderIdRequest.java`
- Use `jakarta.*` (not `javax.*`), `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate`/`@LastModifiedDate`, `@Enumerated(EnumType.STRING)` for status fields
- `userId` is always `updatable = false` — set once at creation
- `id` is `UUID`, assigned by caller (`UUID.randomUUID()`) not DB sequence

---

### `contact-service/.../contact/ContactController.java` (controller, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/payment/PaymentController.java`

**IDOR guard + JWT pattern** (lines 1–72 — full file):
```java
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    @PostMapping
    public ResponseEntity<PaymentDto> initiate(
            JwtAuthenticationToken auth,
            @Valid @RequestBody PurchaseRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());  // IDOR guard
        Payment payment = paymentService.initiate(userId, request);
        return ResponseEntity.ok(PaymentDto.from(payment, timeoutSeconds));
    }

    @GetMapping
    public Page<PaymentDto> history(
            JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());  // IDOR guard
        return paymentService.history(userId, PageRequest.of(page, size))
                .map(p -> PaymentDto.from(p, timeoutSeconds));
    }
}
```

**Apply to:** `ContactController.java`, `GroupController.java`, `SuppressionController.java`, `CampaignController.java`, `SenderIdController.java`
- `userId` is ALWAYS from `auth.getToken().getSubject()` — never from path/body
- All repository queries must include `userId` as a filter
- Return 404 (not 403) when record exists but belongs to another user (information leakage prevention)

Alternatively, `WalletController` uses `@AuthenticationPrincipal Jwt auth` + `auth.getSubject()` — both patterns are equivalent; use whichever is already in the service.

---

### `contact-service/.../config/SecurityConfig.java` (config, request-response)

**Analog:** `services/wallet-service/src/main/java/com/opendesk/wallet/config/SecurityConfig.java`

**Full file** (lines 1–47):
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
```

**Apply to:** `contact-service/.../config/SecurityConfig.java`, `messaging-service/.../config/SecurityConfig.java`
- For `messaging-service`, add an `ADMIN` role check on the internal approve/reject endpoint:
  `.requestMatchers("/api/v1/internal/**").hasRole("ADMIN")`

---

### `contact-service/.../config/RabbitMqConfig.java` + `messaging-service/.../config/RabbitMqConfig.java` (config, event-driven)

**Analog for simple exchange declaration:** `services/wallet-service/src/main/java/com/opendesk/wallet/config/RabbitMqConfig.java`

**Simple exchange + converter pattern** (lines 1–60 — full file):
```java
@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE = "wallet.events";
    public static final String ROUTING_KEY_PREFIX = "wallet.";

    @Bean
    public TopicExchange walletEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
```

**For messaging-service specifically**, this config must also declare the quorum queue + DLX + TTL-ladder. There is no codebase analog for the quorum/DLX pattern — use the excerpt from `04-RESEARCH.md §Pattern 1` directly:

```java
// Primary quorum queue
@Bean
public Queue sendQueue() {
    return QueueBuilder.durable("messaging.send")
            .quorum()
            .deadLetterExchange("messaging.retry.dlx")
            .deadLetterRoutingKey("messaging.retry.1m")
            .deliveryLimit(3)
            .build();
}

// Retry queues TTL-ladder
@Bean
public Queue retry1Queue() {
    return QueueBuilder.durable("messaging.retry.1m")
            .ttl(60_000)
            .deadLetterExchange("")
            .deadLetterRoutingKey("messaging.send")
            .build();
}

// Dead queue
@Bean
public Queue deadQueue() {
    return QueueBuilder.durable("messaging.dead").quorum().build();
}

// DLX (DirectExchange routes to retry ladder)
@Bean
public DirectExchange retryDlx() {
    return new DirectExchange("messaging.retry.dlx", true, false);
}
```

`application.yml` must include:
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        acknowledge-mode: manual
```

---

### `contact-service/.../outbox/OutboxEntry.java` (model, event-driven)

**Analog:** `services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxEntry.java`

**Full file** (lines 1–84):
Copy verbatim; change package from `com.opendesk.identity.outbox` to service-specific package (e.g. `com.opendesk.contact.outbox`). The entity is identical across all three existing services — identity, wallet, payment all have their own copy.

Key fields: `id UUID`, `eventId UUID (unique)`, `aggregateType String`, `aggregateId String`, `eventType String`, `payload TEXT`, `sent boolean`, `createdAt Instant`, `sentAt Instant`.

---

### `contact-service/.../outbox/OutboxRepository.java` (repository, event-driven)

**Analog:** `services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxRepository.java`

**Full file** (lines 1–29):
```java
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);
    List<OutboxEntry> findBySentFalse();  // tests only
}
```

Copy verbatim; change package and `OutboxEntry` import.

---

### `contact-service/.../outbox/OutboxRelay.java` + `messaging-service/.../outbox/OutboxRelay.java` (scheduler, event-driven)

**Analog:** `services/identity-service/src/main/java/com/opendesk/identity/outbox/OutboxRelay.java`

**Full relay pattern** (lines 1–83 — full file):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5_000)
    public void relay() {
        List<OutboxEntry> batch =
                outboxRepository.findBySentFalseOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                markSent(entry);
            } catch (Exception ex) {
                log.warn("OutboxRelay: failed to publish entry id={} eventId={} — will retry",
                        entry.getId(), entry.getEventId(), ex);
            }
        }
    }

    private void publish(OutboxEntry entry) {
        String routingKey = RabbitMqConfig.ROUTING_KEY_PREFIX + entry.getEventType();
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, entry.getPayload());
    }

    @Transactional
    public void markSent(OutboxEntry entry) {
        entry.setSent(true);
        entry.setSentAt(Instant.now());
        outboxRepository.save(entry);
    }
}
```

Change: exchange constant reference (`RabbitMqConfig.EXCHANGE` → `messaging.events` for messaging-service outbox), package.

---

### `messaging-service/.../sms/SmsProvider.java` (interface, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/gateway/PaymentGateway.java`

**Interface pattern** (lines 1–36 — full file):
```java
/**
 * Mock-first contract. Implementations:
 * - StubSmsProvider @Profile("stub") — configurable outcomes for dev/test
 * - RealSmsProvider @Profile("prod") — RestClient + Resilience4j CB
 */
public interface PaymentGateway {
    StkPushResult initiateStkPush(StkPushRequest request);
    TransactionStatusResult queryTransactionStatus(String externalId);
}
```

**Apply to `SmsProvider.java`:**
```java
public interface SmsProvider {
    /** Sends a single SMS. Returns a result carrying acceptance status + optional provider reference. */
    SmsResult send(String phoneE164, String body, String senderId);
}
```

Result type `SmsResult` should encode: `ACCEPTED | HARD_FAIL | TRANSIENT_FAIL` outcome + `externalId` (provider ref). Mirrors `StkPushResult`/`TransactionStatusResult` approach.

---

### `messaging-service/.../sms/StubSmsProvider.java` (service, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/gateway/StubPaymentGateway.java`

**Stub pattern with magic suffix + configurable default** (lines 1–93 — full file):
```java
@Profile("stub")
@Service
@Slf4j
public class StubPaymentGateway implements PaymentGateway {

    private static final String FAILURE_SUFFIX = "0001";
    private static final String TIMEOUT_SUFFIX = "0002";

    @Value("${app.payment.stub.default-outcome:SUCCESS}")
    private String defaultOutcome;

    @Value("${app.payment.stub.delay-ms:500}")
    private long delayMs;

    private String resolveOutcome(String externalId) {
        if (externalId != null) {
            if (externalId.endsWith(FAILURE_SUFFIX)) return "FAILURE";
            if (externalId.endsWith(TIMEOUT_SUFFIX)) return "TIMEOUT";
        }
        return defaultOutcome;
    }
}
```

**Apply to `StubSmsProvider.java`:**
- Magic suffix on `phoneE164`: last 4 digits `0001` → HARD_FAIL, `0002` → TRANSIENT_FAIL, else → ACCEPTED
- `@Value("${app.sms.stub.default-outcome:ACCEPTED}")` for override
- `@Value("${app.sms.stub.dlr-delay-ms:10000}")` — for the in-memory DLR simulation
- DLR simulation: maintain `ConcurrentHashMap<UUID, Instant> pendingDeliveries`; a `@Scheduled(fixedDelay=5_000)` method checks entries past `dlrDelayMs` and calls `messagingService.handleDeliveryReceipt(msgId, DELIVERED)`
- Also supports `@Profile("stub")` on the delivery-receipt webhook endpoint stub

---

### `messaging-service/.../sms/RealSmsProvider.java` (service, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/gateway/AzampayPaymentGateway.java`

**RestClient + Resilience4j CB + Retry pattern** (lines 29–144 — full file):
```java
@Profile("prod")
@Service
@Slf4j
public class AzampayPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public AzampayPaymentGateway(...) {
        this.restClient = RestClient.builder().build();
    }

    @CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
    @Retry(name = "azampay")
    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        Map<String, Object> response = restClient.post()
                .uri(azampayBaseUrl + "/azampay/mobileCheckout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        ...
    }

    public StkPushResult stkPushFallback(StkPushRequest request, Throwable ex) {
        throw new AzampayTransientException("circuit breaker open", ex);
    }
}
```

**Apply to `RealSmsProvider.java`:**
- `@Profile("prod")`, `@CircuitBreaker(name = "sms")`, `@Retry(name = "sms")`
- `RestClient` with connect/read timeouts; base URL from `@Value("${app.sms.api.base-url}")`
- Fallback method throws `SmsTransientException` (mirrors `AzampayTransientException`)
- Map HTTP 4xx/hard-fail codes to `SmsResult.hardFail(...)`, 5xx to `SmsResult.transientFail(...)`

---

### `messaging-service/.../wallet/WalletReservationClient.java` (client, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/gateway/AzampayPaymentGateway.java`

**RestClient + CB pattern** — same structure as `AzampayPaymentGateway` but calling wallet-service:
```java
// From AzampayPaymentGateway lines 40-49, 59-91
public AzampayPaymentGateway(AzampayTokenProvider tokenProvider, @Value(...) String baseUrl) {
    this.restClient = RestClient.builder()
            .requestInitializer(req -> req.getHeaders().setBearerAuth(tokenProvider.getToken()))
            .build();
}

@CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
@Retry(name = "azampay")
public StkPushResult initiateStkPush(StkPushRequest request) {
    Map<String, Object> response = restClient.post()
            .uri(azampayBaseUrl + "/azampay/mobileCheckout")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
    ...
}
```

**Apply to `WalletReservationClient.java`:**
- No `@Profile` — always active (there is no stub for wallet-service in messaging; use mock in tests)
- `@CircuitBreaker(name = "wallet")`, no Retry (reservation failure → surface as "insufficient credits")
- `POST /api/v1/wallet/reservations` with `{ userId, count, campaignId }` body
- On HTTP 409 → throw `InsufficientCreditsException`
- Returns `ReservationResult { List<UUID> lotIds, int reservedCount }`
- `RestClient` configured with `connectTimeout(5s)` / `readTimeout(10s)` via `.defaultHeader(...)` or timeout builder

---

### `messaging-service/.../message/SendMessageConsumer.java` (consumer, event-driven)

**Analog:** `services/wallet-service/src/main/java/com/opendesk/wallet/consumer/UserVerifiedConsumer.java`

**@RabbitListener + idempotency + @Transactional pattern** (lines 1–67 — full file):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserVerifiedConsumer {

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
            exchange = @Exchange(name = "identity.events", type = "topic", durable = "true"),
            key = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(UserVerifiedEvent event) {
        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate event ignored: eventId={}", event.eventId());
            return;
        }
        // business logic
    }
}
```

**Apply to `SendMessageConsumer.java`:**
- Listens on `messaging.send` (quorum queue — declared in `RabbitMqConfig`, not re-declared here)
- `@RabbitListener(queues = "messaging.send")` — no `@QueueBinding` since queue is declared programmatically
- `@Transactional` on the handler method
- On `SmsProvider.ACCEPTED`: update `OutboundMessage(SENT)` + write `MessageAccepted` outbox entry
- On `SmsProvider.HARD_FAIL`: update `OutboundMessage(FAILED)` + write `MessageRefundDue` outbox entry + nack (Channel.basicNack, requeue=false)
- On `SmsProvider.TRANSIENT_FAIL`: nack (requeue=false) so DLX routes to retry queue
- Read `x-delivery-count` header to route to the correct retry queue progression (Approach A from RESEARCH.md)
- DO NOT call wallet-service synchronously — emit AMQP event via outbox

**Wallet-side consumer analog applies here too:** `ProcessedEventRepository.tryInsert(event.eventId())` guards are on the wallet-service consumer, not in SendMessageConsumer. The SendMessageConsumer itself does not need idempotency guard if the queue message is consumed exactly-once (at-least-once + nack). But `MessageAccepted`/`MessageRefundDue` outbox entries must carry unique `eventId` for the wallet-side idempotency guard.

---

### `messaging-service/.../message/DeadLetterConsumer.java` (consumer, event-driven)

**Analog:** `services/wallet-service/src/main/java/com/opendesk/wallet/consumer/UserVerifiedConsumer.java`

Same `@RabbitListener` structure but listening on `messaging.dead`:
```java
@RabbitListener(queues = "messaging.dead")
@Transactional
public void onDeadLetter(SendMessagePayload payload) {
    // update OutboundMessage to FAILED
    // write MessageRefundDue outbox entry
}
```

---

### `messaging-service/.../scheduler/ScheduledCampaignDispatchJob.java` (scheduler, batch)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/reconciliation/ReconciliationJob.java`

**Scheduled job pattern — testable via delegate method** (lines 37–117 — full file):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    @Value("${app.reconciliation.max-per-run:50}")
    private int maxPerRun;

    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:120000}")
    public void scheduleReconcile() {
        reconcile(Instant.now().minus(5, ChronoUnit.MINUTES));
    }

    @Transactional
    public void reconcile(Instant cutoff) {
        List<Payment> stale = paymentRepository.findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.EXPIRED),
                cutoff,
                PageRequest.of(0, maxPerRun));

        if (stale.isEmpty()) {
            log.debug("ReconciliationJob: no stale payments to reconcile");
            return;
        }

        for (Payment payment : stale) {
            try {
                checkAndReconcile(payment);
            } catch (Exception ex) {
                log.warn("ReconciliationJob: error checking paymentId={}", payment.getId(), ex);
            }
        }
    }
}
```

**Apply to `ScheduledCampaignDispatchJob.java`:**
```java
@Scheduled(fixedDelay = 30_000)
public void schedule() { dispatch(Instant.now()); }

public void dispatch(Instant now) {   // testable — pass future now to fast-forward
    List<Campaign> due = campaignRepository
        .findByStatusAndScheduledAtBefore(CampaignStatus.SCHEDULED, now, PageRequest.of(0, 50));
    if (due.isEmpty()) return;
    for (Campaign c : due) {
        try { campaignService.executeSend(c); }
        catch (Exception ex) { log.warn("Dispatch failed for campaign {}", c.getId(), ex); }
    }
}
```

---

### `messaging-service/.../senderid/SenderIdController.java` (controller, request-response)

**Analog:** `services/payment-service/src/main/java/com/opendesk/payment/payment/PaymentController.java`

Same IDOR pattern. The internal admin endpoint must additionally check `ROLE_ADMIN`:
```java
// On SecurityConfig — add above anyRequest().authenticated():
.requestMatchers("/api/v1/internal/**").hasRole("ADMIN")
```

---

### `wallet-service/.../consumer/MessagingEventConsumer.java` (consumer, event-driven)

**Analog:** `services/wallet-service/src/main/java/com/opendesk/wallet/consumer/UserVerifiedConsumer.java`

**Full exact pattern** (lines 1–67 — full file):
```java
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
        exchange = @Exchange(name = "identity.events", type = "topic", durable = "true"),
        key = "identity.UserVerified"
))
@Transactional
public void onUserVerified(UserVerifiedEvent event) {
    if (!processedEventRepository.tryInsert(event.eventId())) {
        log.info("Duplicate UserVerified event ignored: eventId={}", event.eventId());
        return;
    }
    // business logic
}
```

**Apply to `MessagingEventConsumer.java` — three listener methods:**
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
@RabbitListener(bindings = @QueueBinding(...key = "messaging.MessageReleased"))
@Transactional
public void onMessageReleased(MessageReleased event) {
    if (!processedEventRepository.tryInsert(event.eventId())) return;
    lotService.releaseFromLot(event.userId(), event.lotId());
}

// MessageRefundDue → REFUND via creditBack
@RabbitListener(bindings = @QueueBinding(...key = "messaging.MessageRefundDue"))
@Transactional
public void onMessageRefundDue(MessageRefundDue event) {
    if (!processedEventRepository.tryInsert(event.eventId())) return;
    lotService.creditBack(event.userId(), event.creditsToRefund(), event.messageId());
}
```

Note: `wallet-service` does NOT redeclare `messaging.events` exchange in its `RabbitMqConfig`. The `@QueueBinding` `@Exchange` annotation handles passive binding.

---

### `wallet-service/.../lot/LotService.java` additions

**Analog:** `services/wallet-service/src/main/java/com/opendesk/wallet/lot/LotService.java` (modify existing file)

**Existing `creditBack` pattern to copy for `consumeFromLot` and `releaseFromLot`** (lines 99–115):
```java
@Transactional
public CreditLot creditBack(UUID userId, int credits, UUID referenceId) {
    CreditLot lot = CreditLot.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .lotType(LotType.REFUND)
            ...
            .build();
    lot = lotRepository.save(lot);
    txnRepository.save(new CreditTransaction(userId, lot.getId(), TxnType.REFUND, credits, referenceId));
    return lot;
}
```

**New methods to add:**
```java
@Transactional
public void consumeFromLot(UUID userId, UUID lotId) {
    CreditLot lot = lotRepository.findById(lotId)
            .orElseThrow(() -> new IllegalStateException("Lot not found: " + lotId));
    lot.setConsumed(lot.getConsumed() + 1);
    lot.setReserved(lot.getReserved() - 1);
    txnRepository.save(new CreditTransaction(userId, lotId, TxnType.CONSUME, 1, null));
}

@Transactional
public void releaseFromLot(UUID userId, UUID lotId) {
    CreditLot lot = lotRepository.findById(lotId)
            .orElseThrow(() -> new IllegalStateException("Lot not found: " + lotId));
    lot.setReserved(lot.getReserved() - 1);
    txnRepository.save(new CreditTransaction(userId, lotId, TxnType.RELEASE, 1, null));
}
```

---

### `AbstractContactIntegrationTest.java` + `AbstractMessagingIntegrationTest.java` (test)

**Analog:** `services/wallet-service/src/test/java/com/opendesk/wallet/AbstractWalletIntegrationTest.java`

**Full base class pattern** (lines 1–73 — full file):
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
@Import(WalletTestConfiguration.class)
public abstract class AbstractWalletIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("wallet_test")
                    .withUsername("test")
                    .withPassword("test");

    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3-management");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
```

**For `AbstractContactIntegrationTest`:** No RabbitMQ needed (contact-service has no AMQP consumers); include only Postgres. For `AbstractMessagingIntegrationTest`: include Postgres + RabbitMQ (no Redis unless rate-limiting is added). Omit Redis unless explicitly needed.

---

## Shared Patterns

### IDOR Guard (ALL controllers)
**Source:** `services/payment-service/.../payment/PaymentController.java` lines 51–53 and 64–65
```java
UUID userId = UUID.fromString(auth.getToken().getSubject());
```
Or from `WalletController.java` lines 51, 68:
```java
UUID userId = UUID.fromString(auth.getSubject());
```
Apply to every endpoint in: `ContactController`, `GroupController`, `SuppressionController`, `CampaignController`, `SenderIdController`. All repository queries MUST include `userId` from JWT subject.

### Idempotent AMQP Consumer Guard
**Source:** `services/wallet-service/.../consumer/ProcessedEventRepository.java` lines 27–41
```java
@Modifying
@Query(value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT DO NOTHING",
       nativeQuery = true)
int insertIfAbsent(@Param("eventId") String eventId);

default boolean tryInsert(String eventId) {
    return insertIfAbsent(eventId) == 1;
}
```
Apply to: `wallet-service/.../consumer/MessagingEventConsumer.java` (all three handlers). Copy the `ProcessedEvent` entity and `ProcessedEventRepository` verbatim — these are already in wallet-service and need no new copy.

### Transactional Outbox Pattern
**Source:** `services/identity-service/.../outbox/` (OutboxEntry + OutboxRepository + OutboxRelay — all three files)
Apply to: `contact-service/.../outbox/` and `messaging-service/.../outbox/` — copy all three files, change package + exchange name constant reference.

### @Scheduled Job Pattern (bounded page + per-item try/catch + testable delegate)
**Source:** `services/payment-service/.../reconciliation/ReconciliationJob.java` lines 50–83
```java
@Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:120000}")
public void scheduleReconcile() {
    reconcile(Instant.now().minus(5, ChronoUnit.MINUTES));
}

@Transactional
public void reconcile(Instant cutoff) {
    List<Payment> stale = paymentRepository.findByStatusInAndCreatedAtBefore(..., PageRequest.of(0, maxPerRun));
    if (stale.isEmpty()) { log.debug("..."); return; }
    for (Payment payment : stale) {
        try { checkAndReconcile(payment); }
        catch (Exception ex) { log.warn("...", payment.getId(), ex); }
    }
}
```
Apply to: `ScheduledCampaignDispatchJob.java`, and any future sweep jobs.

### Mock-First @Profile Interface Triple
**Source:** `services/payment-service/.../gateway/PaymentGateway.java` + `StubPaymentGateway.java` + `AzampayPaymentGateway.java`
Apply to: `SmsProvider.java` + `StubSmsProvider.java` + `RealSmsProvider.java`
- Interface: no annotations
- Stub: `@Profile("stub") @Service`
- Real: `@Profile("prod") @Service @CircuitBreaker @Retry`

### RestClient Sync HTTP Call
**Source:** `services/payment-service/.../gateway/AzampayPaymentGateway.java` lines 40–49, 59–91
```java
private final RestClient restClient;

public AzampayPaymentGateway(...) {
    this.restClient = RestClient.builder().build();
}

@CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
@Retry(name = "azampay")
public StkPushResult initiateStkPush(StkPushRequest request) {
    Map<String, Object> response = restClient.post()
            .uri(baseUrl + "/endpoint")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
}
```
Apply to: `WalletReservationClient.java` and `RealSmsProvider.java`.

---

## No Analog Found

Files with no close match in the codebase — planner should use RESEARCH.md patterns directly:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `contact-service/.../csv/PhoneNormalizer.java` | utility | transform | No libphonenumber usage anywhere in the codebase. Use RESEARCH.md §Pattern 4 (PhoneNumberUtil.parse/format) |
| `contact-service/.../csv/CsvImportService.java` | service | file-I/O | No CSV import in the codebase. Use RESEARCH.md §Pattern 5 (CSVFormat.DEFAULT.withFirstRecordAsHeader) |
| `messaging-service/.../sms/SmsEncoder.java` | utility | transform | No SMS encoding logic anywhere. Use RESEARCH.md §Pattern 6 (GSM-7 charset check + part counting) |
| `messaging-service/.../config/RabbitMqConfig.java` (quorum/DLX declarations) | config | event-driven | No quorum queue or DLX declarations exist in the codebase. Use RESEARCH.md §Pattern 1 (QueueBuilder.quorum().deliveryLimit().deadLetterExchange()) |

---

## Key Observations for Planner

1. **ReservationResult.lotIds structure (RESEARCH.md OQ #2):** Reading `ReservationService.reserve` (lines 62–86) confirms: `lotIds` is a `List<UUID>` of **distinct lot IDs** (one entry per lot touched), NOT one UUID per credit. For a 100-credit campaign spanning 2 lots, `lotIds` has 2 entries. The zip logic in `CampaignService.executeSend` must walk lots in order, assigning recipients from each lot proportionally (e.g., fill first lot's share, then next lot's share).

2. **`LotService.consumeFromLot` does NOT exist yet** (confirmed by reading `LotService.java`). The wallet-service plan must add `consumeFromLot(userId, lotId)` and `releaseFromLot(userId, lotId)` as new methods alongside the existing `grantBonus`, `grantPurchased`, `creditBack`.

3. **`creditBack` already exists in LotService** (lines 99–115). The `MessageRefundDue` consumer calls it directly — no new refund method needed.

4. **DLX retry counter advancement (RESEARCH.md Approach A):** The codebase has no existing DLX pattern. The planner must specify: `SendMessageConsumer` reads `x-delivery-count` from AMQP message headers to decide which retry queue to republish to, then acks the original message. This is more complex than standard nack behavior and must be an explicit plan task.

---

## Metadata

**Analog search scope:** `services/identity-service/`, `services/wallet-service/`, `services/payment-service/`
**Files read:** 22 Java source files
**Pattern extraction date:** 2026-06-21
