# Phase 3: Wallet & Payments — Pattern Map

**Mapped:** 2026-06-20
**Files analyzed:** 34 new/modified files across wallet-service and payment-service
**Analogs found:** 34 / 34 (all from identity-service Phase 2 — exact or role-match)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `wallet-service/.../lot/CreditLot.java` | model/entity | CRUD | `identity/.../user/User.java` | role-match |
| `wallet-service/.../lot/CreditLotRepository.java` | repository | CRUD | `identity/.../user/UserRepository.java` | role-match |
| `wallet-service/.../lot/LotService.java` | service | CRUD | `identity/.../auth/RegistrationService.java` | role-match |
| `wallet-service/.../transaction/CreditTransaction.java` | model/entity | append-only | `identity/.../outbox/OutboxEntry.java` | role-match |
| `wallet-service/.../transaction/CreditTransactionRepository.java` | repository | CRUD | `identity/.../outbox/OutboxRepository.java` | role-match |
| `wallet-service/.../reservation/ReservationService.java` | service | CRUD+lock | `identity/.../auth/RegistrationService.java` | role-match |
| `wallet-service/.../balance/BalanceService.java` | service | request-response | `identity/.../auth/LoginService.java` | role-match |
| `wallet-service/.../consumer/UserVerifiedConsumer.java` | consumer | event-driven | `identity/.../outbox/OutboxRelay.java` | role-match |
| `wallet-service/.../consumer/ProcessedEventRepository.java` | repository | CRUD | `identity/.../outbox/OutboxRepository.java` | role-match |
| `wallet-service/.../sweep/ExpirySweepJob.java` | scheduler | batch | `identity/.../verification/VerificationRetryJob.java` | exact |
| `wallet-service/.../sweep/LowCreditAlertJob.java` | scheduler | batch | `identity/.../verification/VerificationRetryJob.java` | exact |
| `wallet-service/.../outbox/OutboxEntry.java` | model/entity | event-driven | `identity/.../outbox/OutboxEntry.java` | exact (copy) |
| `wallet-service/.../outbox/OutboxRepository.java` | repository | event-driven | `identity/.../outbox/OutboxRepository.java` | exact (copy) |
| `wallet-service/.../outbox/OutboxRelay.java` | scheduler | event-driven | `identity/.../outbox/OutboxRelay.java` | exact (copy) |
| `wallet-service/.../api/WalletController.java` | controller | request-response | `identity/.../auth/RegistrationController.java` | role-match |
| `wallet-service/.../config/RabbitMqConfig.java` | config | event-driven | `identity/.../config/RabbitMqConfig.java` | exact |
| `wallet-service/.../config/SecurityConfig.java` | config | request-response | `identity/.../config/SecurityConfig.java` | role-match |
| `wallet-service/src/main/resources/db/migration/V1__create_credit_lots.sql` | migration | CRUD | `identity/.../db/migration/V1__create_users.sql` | exact |
| `wallet-service/src/main/resources/db/migration/V4__create_outbox.sql` | migration | event-driven | `identity/.../db/migration/V3__create_outbox.sql` | exact (copy) |
| `wallet-service/src/test/.../AbstractWalletIntegrationTest.java` | test | — | `identity/.../AbstractIntegrationTest.java` | exact |
| `payment-service/.../bundle/SmsBundle.java` | model/entity | CRUD | `identity/.../user/User.java` | role-match |
| `payment-service/.../bundle/BundleRepository.java` | repository | CRUD | `identity/.../user/UserRepository.java` | role-match |
| `payment-service/.../bundle/BundleController.java` | controller | request-response | `identity/.../auth/RegistrationController.java` | role-match |
| `payment-service/.../gateway/PaymentGateway.java` | interface | request-response | `identity/.../verification/NidaVerificationService.java` | exact |
| `payment-service/.../gateway/StubPaymentGateway.java` | service (stub) | request-response | `identity/.../verification/StubNidaVerificationService.java` | exact |
| `payment-service/.../gateway/AzampayPaymentGateway.java` | service (prod) | request-response | `identity/.../verification/RealNidaVerificationService.java` | exact |
| `payment-service/.../payment/Payment.java` | model/entity | CRUD | `identity/.../user/User.java` | role-match |
| `payment-service/.../payment/PaymentRepository.java` | repository | CRUD | `identity/.../user/UserRepository.java` | role-match |
| `payment-service/.../payment/PaymentService.java` | service | request-response | `identity/.../auth/RegistrationService.java` | role-match |
| `payment-service/.../payment/PaymentController.java` | controller | request-response | `identity/.../auth/RegistrationController.java` | role-match |
| `payment-service/.../callback/CallbackController.java` | controller | request-response | `identity/.../auth/RegistrationController.java` | role-match |
| `payment-service/.../reconciliation/ReconciliationJob.java` | scheduler | batch | `identity/.../verification/VerificationRetryJob.java` | exact |
| `payment-service/.../outbox/OutboxEntry.java` | model/entity | event-driven | `identity/.../outbox/OutboxEntry.java` | exact (copy) |
| `payment-service/.../outbox/OutboxRelay.java` | scheduler | event-driven | `identity/.../outbox/OutboxRelay.java` | exact (copy) |

---

## Pattern Assignments

### Outbox: `OutboxEntry`, `OutboxRepository`, `OutboxRelay` (both services)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/outbox/`

These three files are **direct copies** — change only the package name and the exchange constant. No other modifications.

**OutboxEntry** — lines 1–84 of `outbox/OutboxEntry.java`:
```java
package com.smsreseller.wallet.outbox;  // or com.smsreseller.payment.outbox

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutboxEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "sent", nullable = false)
    @Builder.Default
    private boolean sent = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
```

**OutboxRepository** — lines 1–28 of `outbox/OutboxRepository.java`:
```java
package com.smsreseller.wallet.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    List<OutboxEntry> findBySentFalse();  // tests only
}
```

**OutboxRelay** — lines 1–83 of `outbox/OutboxRelay.java` (change exchange constant only):
```java
package com.smsreseller.wallet.outbox;

import com.smsreseller.wallet.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

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
                log.warn("OutboxRelay: failed to publish id={} — will retry", entry.getId(), ex);
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

**Outbox Flyway migration** — copy of `V3__create_outbox.sql` verbatim (rename to the appropriate `V{N}__create_outbox.sql` per service):
```sql
CREATE TABLE IF NOT EXISTS outbox (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    event_id       UUID        NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        TEXT        NOT NULL,
    sent           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX IF NOT EXISTS idx_outbox_sent ON outbox (sent) WHERE sent = FALSE;
```

---

### `PaymentGateway` interface + `StubPaymentGateway` + `AzampayPaymentGateway`

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/verification/`
- Interface: `NidaVerificationService.java` (lines 1–32)
- Stub: `StubNidaVerificationService.java` (lines 1–94)
- Real/Prod: `RealNidaVerificationService.java` (lines 1–105)

**Interface pattern** (copy `NidaVerificationService.java` shape):
```java
package com.smsreseller.payment.gateway;

/**
 * Implementations:
 *   StubPaymentGateway  — @Profile("stub"), configurable outcomes for dev/test (D-10)
 *   AzampayPaymentGateway — @Profile("prod"), RestClient + Resilience4j (CLAUDE.md)
 */
public interface PaymentGateway {
    StkPushResult initiateStkPush(StkPushRequest request);
    TransactionStatusResult queryTransactionStatus(String externalId);
}
```

**Stub pattern** (copy `StubNidaVerificationService.java`, adapt for payment outcomes):
```java
@Profile("stub")
@Service
public class StubPaymentGateway implements PaymentGateway {

    // Magic externalId suffix controls outcome (mirrors magic-NIN pattern):
    //   ...0001 → FAILURE
    //   ...0002 → TIMEOUT (never callbacks)
    //   default → SUCCESS (callbacks after configurable delay)
    @Value("${app.payment.stub.default-outcome:SUCCESS}")
    private String defaultOutcome;

    @Value("${app.payment.stub.delay-ms:500}")
    private long delayMs;

    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        log.debug("Stub STK push — externalId={}, outcome={}",
                  request.paymentId(), resolveOutcome(request.paymentId().toString()));
        // Return immediately; outcome delivered on queryTransactionStatus() call
        return StkPushResult.accepted(request.paymentId().toString());
    }

    @Override
    public TransactionStatusResult queryTransactionStatus(String externalId) {
        String outcome = resolveOutcome(externalId);
        return switch (outcome) {
            case "FAILURE" -> TransactionStatusResult.failed(externalId);
            case "TIMEOUT" -> TransactionStatusResult.pending(externalId);
            default -> TransactionStatusResult.success(externalId);
        };
    }
}
```

**Real/Prod pattern** (copy `RealNidaVerificationService.java`, adapt for Azampay):
```java
@Profile("prod")
@Service
@Slf4j
public class AzampayPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    @Value("${app.azampay.base-url}")
    private String azampayBaseUrl;

    public AzampayPaymentGateway(AzampayTokenProvider tokenProvider) {
        // RestClient with connect 5s / read 30s (CLAUDE.md: external call timeouts)
        this.restClient = RestClient.builder()
            .requestInitializer(req ->
                req.getHeaders().setBearerAuth(tokenProvider.getToken()))
            .build();
    }

    @CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
    @Retryable(retryFor = AzampayTransientException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    @Override
    public StkPushResult initiateStkPush(StkPushRequest req) {
        // POST /azampay/mobileCheckout — amount as String (no decimals), externalId = payment UUID
        ...
    }

    @SuppressWarnings("unused")
    public StkPushResult stkPushFallback(StkPushRequest req, Throwable ex) {
        log.warn("Azampay circuit breaker open");
        throw new AzampayTransientException("Azampay circuit breaker open", ex);
    }
}
```

---

### `ReconciliationJob` and `ExpirySweepJob` / `LowCreditAlertJob`

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationRetryJob.java` (lines 1–79)

**Scheduled job pattern** (all three jobs follow this shape exactly):
```java
package com.smsreseller.payment.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Value("${app.reconciliation.max-per-run:50}")
    private int maxPerRun;

    // fixedDelay (not fixedRate) — run completes before next starts (VerificationRetryJob pattern)
    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:120000}")
    @Transactional
    public void reconcile() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Payment> stale = paymentRepository
            .findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.EXPIRED),
                cutoff,
                PageRequest.of(0, maxPerRun));

        if (stale.isEmpty()) {
            log.debug("ReconciliationJob: no stale payments");
            return;
        }

        log.info("ReconciliationJob: checking {} stale payment(s)", stale.size());
        for (Payment p : stale) {
            try {
                checkAndReconcile(p);
            } catch (Exception ex) {
                log.warn("ReconciliationJob: error checking payment id={}", p.getId(), ex);
            }
        }
    }
}
```

---

### `UserVerifiedConsumer` (wallet-service)

**Analog:** `identity/.../outbox/OutboxRelay.java` (consumer side) + `identity/.../config/RabbitMqConfig.java`

This is the first inbound integration: wallet-service binds a durable queue to the `identity.events` topic exchange already declared by identity-service. The consumer pattern follows Spring AMQP `@RabbitListener` with `@QueueBinding`.

```java
package com.smsreseller.wallet.consumer;

import com.smsreseller.identity.outbox.UserVerifiedEvent;  // or mirror the record locally
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserVerifiedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final LotService lotService;

    // Queue binding: wallet binds its own durable queue to identity.events exchange.
    // identity-service already declares the exchange — wallet does NOT redeclare it.
    @RabbitListener(bindings = @QueueBinding(
        value  = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
        exchange = @Exchange(name = "identity.events", type = ExchangeTypes.TOPIC, durable = "true"),
        key    = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(UserVerifiedEvent event) {
        // Idempotency: processed_events table — ON CONFLICT DO NOTHING
        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.debug("UserVerifiedConsumer: duplicate eventId={} — skipping", event.eventId());
            return;
        }
        // Grant 50-credit bonus lot, 30-day expiry (D-03)
        lotService.grantBonus(event.userId(), event.freeCredits(),
            Instant.now().plus(30, ChronoUnit.DAYS));
        log.info("UserVerifiedConsumer: granted {} bonus credits to userId={}",
                 event.freeCredits(), event.userId());
    }
}
```

**ProcessedEventRepository** — table: `processed_events (event_id UUID PRIMARY KEY)`. The `tryInsert` method uses `INSERT ... ON CONFLICT DO NOTHING` and returns `true` if 1 row was inserted, `false` if 0 (already exists). Use a `@Modifying @Query` with Spring Data JPA nativeQuery.

---

### JPA Entity pattern (`CreditLot`, `CreditTransaction`, `Payment`, `SmsBundle`)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/user/User.java` (lines 1–75)

```java
package com.smsreseller.wallet.lot;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_lots")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreditLot {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_type", nullable = false, updatable = false)
    private LotType lotType;  // PURCHASED | BONUS | REFUND

    @Column(name = "granted", nullable = false, updatable = false)
    private int granted;

    @Column(name = "consumed", nullable = false)
    private int consumed;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "payment_id")
    private UUID paymentId;  // nullable for bonus lots

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    // No @LastModifiedDate — consumed/reserved updated in-place but no updatedAt needed
}
```

Key differences from `User`:
- `CreditTransaction` has no `@Setter` (fully immutable append-only record after save)
- `Payment` adds `@Enumerated(EnumType.STRING)` for `PaymentStatus` — same pattern as `User.status`
- `SmsBundle` maps `price_tzs BIGINT` to `long priceTzs` — no `BigDecimal`, no `Double`
- All entities use `jakarta.*` throughout (never `javax.*`)

---

### Repository pattern (`CreditLotRepository`, `PaymentRepository`, etc.)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/user/UserRepository.java` (lines 1–43)

```java
package com.smsreseller.wallet.lot;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditLotRepository extends JpaRepository<CreditLot, UUID> {

    // Pessimistic write lock — expiry-soonest-first order for reservation (D-01, D-02)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM CreditLot l WHERE l.userId = :userId " +
           "AND (l.granted - l.consumed - l.reserved) > 0 " +
           "AND l.expiresAt > :now ORDER BY l.expiresAt ASC")
    List<CreditLot> findAvailableByUserIdOrderByExpiresAtAsc(
        @Param("userId") UUID userId,
        @Param("now") Instant now,
        Pageable pageable);

    // Balance derivation — no FOR UPDATE needed (read-only)
    @Query("SELECT COALESCE(SUM(l.granted - l.consumed - l.reserved), 0) FROM CreditLot l " +
           "WHERE l.userId = :userId AND l.expiresAt > :now")
    int sumAvailableCredits(@Param("userId") UUID userId, @Param("now") Instant now);
}
```

`PaymentRepository` adds the `findByStatusInAndCreatedAtBefore` method (same as `UserRepository.findByStatusAndCreatedAtBefore`):
```java
List<Payment> findByStatusInAndCreatedAtBefore(
    List<PaymentStatus> statuses, Instant cutoff, Pageable pageable);
```

---

### REST Controller pattern (`WalletController`, `BundleController`, `PaymentController`)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/auth/RegistrationController.java` (lines 1–37)

```java
package com.smsreseller.wallet.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final BalanceService balanceService;
    private final CreditTransactionRepository transactionRepository;

    @GetMapping("/balance")
    public BalanceResponse getBalance(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        // Extract userId from JWT — NEVER from request body or path param (ASVS V4, IDOR)
        return balanceService.getBalance(userId);
    }

    @GetMapping("/transactions")
    public Page<CreditTransactionDto> getHistory(
            JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(page, size))
            .map(CreditTransactionDto::from);
    }
}
```

**CallbackController** is the exception — it is `permitAll` (public webhook endpoint, no JWT required):
```java
@RestController
@RequestMapping("/api/v1/payments/callback")
public class CallbackController {
    // No JwtAuthenticationToken parameter — public endpoint
    // Authenticated by HMAC signature validator (StubSignatureValidator in stub profile)
    @PostMapping
    public ResponseEntity<Void> handleCallback(@RequestBody AzampayCallbackPayload payload) {
        callbackProcessor.processCallback(payload);
        return ResponseEntity.ok().build();
    }
}
```

---

### `SecurityConfig` (wallet-service and payment-service)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/config/SecurityConfig.java` (lines 1–98)

Wallet/payment SecurityConfig differs from identity's: no `DaoAuthenticationProvider`, no `PasswordEncoder` bean — these are purely JWT resource servers. Copy the filter chain shape, keep `oauth2ResourceServer.jwt`, add `permitAll` for the Azampay callback endpoint on payment-service.

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
                // payment-service only: Azampay callback is public (signature-validated internally)
                // .requestMatchers("/api/v1/payments/callback").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

Shared-security JWT decoder wired via `libs/shared-security` — add `implementation(project(":libs:shared-security"))` to each service's `build.gradle.kts`. No additional `JwtDecoder` bean needed.

---

### `RabbitMqConfig` (wallet-service and payment-service)

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/config/RabbitMqConfig.java` (lines 1–47)

wallet-service binds to `identity.events` (declared by identity-service, NOT re-declared here — `@RabbitListener` binding in `UserVerifiedConsumer` handles queue creation) and declares `wallet.events` for outbound:

```java
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "wallet.events";       // wallet-service outbound
    public static final String ROUTING_KEY_PREFIX = "wallet.";

    // DO NOT redeclare identity.events — identity-service owns that exchange.
    // The @QueueBinding on UserVerifiedConsumer creates the binding passively.

    @Bean
    public TopicExchange walletEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
```

payment-service: same shape, exchange name `payment.events`, routing key prefix `payment.`.

---

### Flyway migrations (`.sql` files)

**Analog:** `services/identity-service/src/main/resources/db/migration/V1__create_users.sql` (lines 1–20)

Pattern: `COMMENT ON TABLE` + `COMMENT ON COLUMN` for sensitive/notable fields; `TIMESTAMPTZ` for all timestamps; `UUID PRIMARY KEY`; `BIGINT` for TZS amounts (never `DECIMAL`/`FLOAT`); partial indexes for common query patterns.

```sql
-- V1__create_credit_lots.sql (wallet-service)
-- Part of wallet schema — wallet-service owns this Flyway migration set.

CREATE TABLE credit_lots (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL,
    lot_type    VARCHAR(20) NOT NULL,  -- PURCHASED | BONUS | REFUND
    granted     INT         NOT NULL,
    consumed    INT         NOT NULL DEFAULT 0,
    reserved    INT         NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    payment_id  UUID,                  -- nullable for BONUS lots
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_lots_user_expires
    ON credit_lots (user_id, expires_at)
    WHERE (granted - consumed - reserved) > 0;

COMMENT ON COLUMN credit_lots.lot_type IS 'PURCHASED (12-month expiry) | BONUS (30-day) | REFUND';
COMMENT ON COLUMN credit_lots.granted IS 'SMS credits originally granted in this lot';
COMMENT ON COLUMN credit_lots.consumed IS 'Credits debited from this lot (immutable once consumed)';
COMMENT ON COLUMN credit_lots.reserved IS 'Credits held for in-flight campaigns (SELECT FOR UPDATE)';
```

Payment-service `V3__create_payments.sql` must include the partial unique index for single-pending enforcement (D-13):
```sql
-- Enforce one pending payment per user (D-05 / D-13)
CREATE UNIQUE INDEX uq_payments_user_pending
    ON payments (user_id)
    WHERE status = 'PENDING';
```

Seed migration `V2__seed_sms_bundles.sql` (payment-service): prices stored as raw TZS BIGINT (D-11 — whole shillings, no ×100):
```sql
INSERT INTO sms_bundles (id, name, sms_count, price_tzs, is_active, is_purchasable, description)
VALUES
  (gen_random_uuid(), 'Taster',  50,     0,      true, false, 'Free on NIDA verification — not purchasable via Azampay'),
  (gen_random_uuid(), 'Starter', 200,    3200,   true, true,  NULL),
  (gen_random_uuid(), 'Growth',  1000,   14500,  true, true,  NULL),
  (gen_random_uuid(), 'Pro',     5000,   65000,  true, true,  NULL),
  (gen_random_uuid(), 'Scale',   20000,  240000, true, true,  NULL)
ON CONFLICT DO NOTHING;
```

---

### `AbstractWalletIntegrationTest` / `AbstractPaymentIntegrationTest`

**Analog:** `services/identity-service/src/test/java/com/smsreseller/identity/AbstractIntegrationTest.java` (lines 1–73)

Direct copy — change package, database name, and activate `{"stub", "test"}` profiles:

```java
package com.smsreseller.wallet;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
public abstract class AbstractWalletIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wallet_test")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7").withExposedPorts(6379);

    static final RabbitMQContainer RABBITMQ =
        new RabbitMQContainer("rabbitmq:3-management");

    static {
        POSTGRES.start();
        REDIS.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
```

**IT test pattern** — `VerificationOutboxIT.java` (lines 1–91) shows the exact structure for all Phase 3 ITs:
```java
class BalanceIT extends AbstractWalletIntegrationTest {

    @Autowired private CreditLotRepository lotRepository;
    @Autowired private BalanceService balanceService;

    @Test
    void derivedBalanceSumsNonExpiredLots() {
        // Given: two lots, one expired
        // When: getBalance(userId)
        // Then: only non-expired lot included
    }
}
```

---

### `AuthClaims` usage in controllers (shared-security)

**Analog:** `libs/shared-security/src/main/java/com/smsreseller/shared/security/AuthClaims.java` (lines 1–70) and `JwtConfig.java` (lines 1–72)

All wallet/payment controllers that serve authenticated users must:
1. Accept `JwtAuthenticationToken auth` as method parameter
2. Extract `userId` from `auth.getToken().getSubject()` — never from request body
3. Optionally gate on `AuthClaims.isVerified(auth.getToken())` for operations requiring NIDA-verified status

```java
// In any wallet/payment controller method:
UUID userId = UUID.fromString(auth.getToken().getSubject());
if (!AuthClaims.isVerified(auth.getToken())) {
    return ResponseEntity.status(403).body("NIDA verification required");
}
```

---

## Shared Patterns

### 1. Transactional Outbox
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/outbox/`
**Apply to:** wallet-service outbox, payment-service outbox
- Copy all three files (`OutboxEntry`, `OutboxRepository`, `OutboxRelay`), change package and exchange constant only
- Flyway migration: copy `V3__create_outbox.sql` verbatim

### 2. Mock-First Interface + `@Profile`
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/verification/` (`NidaVerificationService`, `StubNidaVerificationService`, `RealNidaVerificationService`)
**Apply to:** `PaymentGateway` + `StubPaymentGateway` + `AzampayPaymentGateway`
- Interface with single-line contract doc listing both impls
- Stub: `@Profile("stub")`, magic-suffix outcome control, `@Value` delay + default-outcome
- Real: `@Profile("prod")`, `RestClient`, `@CircuitBreaker` + `@Retryable`, fallback method

### 3. Scheduled Job Structure
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/verification/VerificationRetryJob.java` (lines 39–79)
**Apply to:** `ReconciliationJob`, `ExpirySweepJob`, `LowCreditAlertJob`
- `@Scheduled(fixedDelayString = "${...}")` — not fixedRate
- Bounded query with `PageRequest.of(0, maxPerRun)`
- `if (list.isEmpty()) { log.debug(...); return; }` early exit
- Per-item try/catch — one item failure does not block the rest

### 4. JWT Security — Resource Server Only
**Source:** `libs/shared-security/src/main/java/com/smsreseller/shared/security/JwtConfig.java` + `services/identity-service/src/main/java/com/smsreseller/identity/config/SecurityConfig.java`
**Apply to:** `SecurityConfig` in wallet-service and payment-service
- Add `implementation(project(":libs:shared-security"))` to `build.gradle.kts`
- No `PasswordEncoder`, no `DaoAuthenticationProvider`, no `UserDetailsService`
- CSRF disabled, STATELESS session, `oauth2ResourceServer.jwt(Customizer.withDefaults())`
- Only difference: payment-service `SecurityConfig` adds `permitAll` for `/api/v1/payments/callback`

### 5. JPA Entity Conventions
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/user/User.java`
- `jakarta.*` imports throughout (never `javax.*`)
- `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` / `@LastModifiedDate`
- Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`
- UUID primary keys assigned by application, not DB sequence
- `@Enumerated(EnumType.STRING)` for all status/type columns
- `BIGINT` → `long` for TZS amounts; `INT` → `int` for credit counts

### 6. Idempotency Guard
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/outbox/OutboxEntry.java` + `OutboxRelay.java`
**Apply to:** `UserVerifiedConsumer`, `CallbackProcessor`
- `processed_events (event_id UUID PRIMARY KEY)` table
- `INSERT INTO processed_events ... ON CONFLICT DO NOTHING` → if 0 rows inserted, skip
- Stronger than status-check guard (atomic via DB constraint)

### 7. RabbitMQ Config
**Source:** `services/identity-service/src/main/java/com/smsreseller/identity/config/RabbitMqConfig.java`
**Apply to:** wallet-service `RabbitMqConfig`, payment-service `RabbitMqConfig`
- Declares own exchange only (wallet.events, payment.events)
- Does NOT redeclare `identity.events` — consumer `@QueueBinding` creates binding passively
- `Jackson2JsonMessageConverter` bean + `RabbitTemplate` bean with converter wired in

---

## No Analog Found

All Phase 3 files have clear analogs in identity-service. No files require falling back to RESEARCH.md patterns as primary reference — RESEARCH.md patterns are secondary confirmation only.

---

## Metadata

**Analog search scope:** `services/identity-service/`, `libs/shared-security/`
**Files read:** 22 source files
**Pattern extraction date:** 2026-06-20
