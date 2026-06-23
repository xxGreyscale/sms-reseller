# Phase 5: Notifications, Admin & Analytics — Pattern Map

**Mapped:** 2026-06-21
**Files analyzed:** 32 (new/modified across notification-service, identity-service, wallet-service, messaging-service, payment-service, admin-web)
**Analogs found:** 26 / 32 (6 NOVEL — all admin-web frontend files)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `services/notification-service/…/config/RabbitMqConfig.java` | config | event-driven | `services/wallet-service/…/config/RabbitMqConfig.java` | exact |
| `services/notification-service/…/config/SecurityConfig.java` | config | request-response | `services/messaging-service/…/config/SecurityConfig.java` | exact |
| `services/notification-service/…/idempotency/ProcessedEvent.java` | model | CRUD | `services/wallet-service/…/consumer/ProcessedEvent.java` | exact |
| `services/notification-service/…/idempotency/ProcessedEventRepository.java` | repository | CRUD | `services/wallet-service/…/consumer/ProcessedEventRepository.java` | exact |
| `services/notification-service/…/consumer/IdentityEventConsumer.java` | consumer | event-driven | `services/wallet-service/…/consumer/UserVerifiedConsumer.java` | exact |
| `services/notification-service/…/consumer/PaymentEventConsumer.java` | consumer | event-driven | `services/wallet-service/…/consumer/PaymentConfirmedConsumer.java` | exact |
| `services/notification-service/…/consumer/WalletEventConsumer.java` | consumer | event-driven | `services/wallet-service/…/consumer/MessagingEventConsumer.java` | exact |
| `services/notification-service/…/consumer/MessagingEventConsumer.java` | consumer | event-driven | `services/wallet-service/…/consumer/MessagingEventConsumer.java` | exact |
| `services/notification-service/…/notification/Notification.java` | model | CRUD | `services/wallet-service/…/outbox/OutboxEntry.java` | role-match |
| `services/notification-service/…/notification/NotificationRepository.java` | repository | CRUD | `services/payment-service/…/bundle/BundleRepository.java` | role-match |
| `services/notification-service/…/notification/NotificationService.java` | service | CRUD | `services/wallet-service/…/lot/LotService.java` | role-match |
| `services/notification-service/…/notification/NotificationController.java` | controller | request-response | `services/payment-service/…/bundle/BundleController.java` | role-match |
| `services/notification-service/…/push/NotificationChannel.java` | interface | request-response | `services/identity-service/…/nida/NidaVerificationService.java` (interface) | role-match |
| `services/notification-service/…/push/StubPushChannel.java` | mock | request-response | `services/identity-service/…/nida/StubNidaVerificationService.java` | exact |
| `services/notification-service/src/main/resources/db/migration/V1__create_processed_events.sql` | migration | CRUD | `services/wallet-service/src/main/resources/db/migration/V1__create_processed_events.sql` | exact |
| `services/notification-service/src/main/resources/db/migration/V2__create_notifications.sql` | migration | CRUD | `services/messaging-service/…/V1__create_campaigns.sql` | role-match |
| `services/notification-service/src/test/…/AbstractNotificationIntegrationTest.java` | test | event-driven | `services/identity-service/src/test/…/AbstractIntegrationTest.java` | exact |
| `services/notification-service/src/test/…/UserVerifiedConsumerIT.java` | test | event-driven | `services/wallet-service/src/test/…/UserVerifiedConsumerIT.java` | exact |
| `services/messaging-service/…/message/DeliveryReceiptService.java` (modify) | service | event-driven | self (add outbox emit to `checkCampaignCompletion`) | self-modify |
| `services/identity-service/…/token/JwtIssuer.java` (modify) | service | request-response | self (add `issueAdminToken`) | self-modify |
| `services/identity-service/…/admin/AdminLoginController.java` | controller | request-response | `services/messaging-service/…/senderid/SenderIdAdminController.java` | role-match |
| `services/identity-service/…/admin/AdminUserSearchController.java` | controller | CRUD | `services/messaging-service/…/senderid/SenderIdAdminController.java` | role-match |
| `services/identity-service/src/main/resources/db/migration/V5__seed_admin_user.sql` | migration | CRUD | `services/payment-service/src/main/resources/db/migration/V2__seed_sms_bundles.sql` | exact |
| `services/wallet-service/…/admin/AdminLedgerController.java` | controller | CRUD | `services/messaging-service/…/senderid/SenderIdAdminController.java` | role-match |
| `services/wallet-service/…/analytics/CreditUsageController.java` | controller | CRUD | `services/payment-service/…/bundle/BundleController.java` | role-match |
| `services/messaging-service/…/analytics/CampaignAnalyticsController.java` | controller | CRUD | `services/payment-service/…/bundle/BundleController.java` | role-match |
| `services/payment-service/…/bundle/AdminBundleController.java` | controller | CRUD | `services/payment-service/…/bundle/BundleController.java` | exact |
| `services/identity-service/…/admin/AuditEntry.java` + `AuditRepository.java` | model+repo | CRUD | `services/wallet-service/…/outbox/OutboxEntry.java` | role-match |
| `apps/admin-web/app/(auth)/login/page.tsx` | component | request-response | NOVEL | — |
| `apps/admin-web/middleware.ts` | middleware | request-response | NOVEL | — |
| `apps/admin-web/app/(admin)/…/*.tsx` (6 screen pages) | component | request-response | NOVEL | — |
| `apps/admin-web/lib/api.ts` + `lib/auth.ts` | utility | request-response | NOVEL | — |

---

## Pattern Assignments

### `ProcessedEventRepository.java` — idempotency gate (notification-service copy)

**Analog:** `services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/ProcessedEventRepository.java`

**Full file (lines 1–42) — copy verbatim, change package only:**
```java
package com.smsreseller.notification.idempotency;  // ← only change

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(
        value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId);

    default boolean tryInsert(String eventId) {
        return insertIfAbsent(eventId) == 1;
    }
}
```

---

### `IdentityEventConsumer.java` — single-event consumer (notification-service)

**Analog:** `services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/UserVerifiedConsumer.java`

**Imports pattern (lines 1–14):**
```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
```

**Core consumer pattern (lines 44–66):**
```java
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "wallet.identity.UserVerified", durable = "true"),
        exchange = @Exchange(name = "identity.events", type = "topic", durable = "true"),
        key = "identity.UserVerified"
))
@Transactional
public void onUserVerified(UserVerifiedEvent event) {
    log.debug("Received UserVerified event: eventId={}, userId={}", event.eventId(), event.userId());

    if (!processedEventRepository.tryInsert(event.eventId())) {
        log.info("Duplicate UserVerified event ignored: eventId={}", event.eventId());
        return;
    }

    // business action here
    log.info("Processed UserVerified for userId={} (eventId={})", event.userId(), event.eventId());
}
```

**Notification-service queue naming convention:** `notification.<sourceExchange>.<EventType>`
- `notification.identity.UserVerified` on `identity.events / identity.UserVerified`
- `notification.payment.PaymentConfirmed` on `payment.events / payment.PaymentConfirmed`
- `notification.wallet.alerts` on `wallet.events / wallet.LowCreditAlert` + `wallet.ExpiryWarning`
- `notification.messaging.events` on `messaging.events / messaging.CampaignCompleted` + `messaging.SenderIdDecided`

---

### `WalletEventConsumer.java` / `MessagingEventConsumer.java` — multi-routing-key consumers

**Analog:** `services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/MessagingEventConsumer.java`

**Multi-binding pattern (lines 49–68 shows one binding; repeat @RabbitListener per routing key or use separate methods sharing one queue):**
```java
// Two separate @RabbitListener methods on different queue names works fine.
// If sharing one queue for two routing keys, use separate @QueueBinding annotations
// in a @RabbitListeners container annotation (Spring AMQP 2.x+).

@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "wallet.messaging.MessageAccepted", durable = "true"),
        exchange = @Exchange(name = "messaging.events", type = "topic", durable = "true"),
        key = "messaging.MessageAccepted"
))
@Transactional
public void onMessageAccepted(MessageAccepted event) {
    if (!processedEventRepository.tryInsert(event.eventId())) {
        log.info("Duplicate MessageAccepted event ignored: eventId={}", event.eventId());
        return;
    }
    // business action
}

// Repeat @RabbitListener for each additional routing key (separate method, separate queue name)
```

> CRITICAL: Do NOT use `@Exchange(... ignoreDeclarationExceptions = "true")` unless the exchange topology may diverge. The wallet-service pattern omits it because the exchange configs match. For notification-service consuming four external exchanges, adding `ignoreDeclarationExceptions = "true"` on each `@Exchange` is recommended as a safety net (RESEARCH.md Pitfall 1).

---

### `SecurityConfig.java` (notification-service + admin service colocated configs)

**Analog:** `services/messaging-service/src/main/java/com/smsreseller/messaging/config/SecurityConfig.java` (lines 1–78)

**Full pattern — copy and adapt path matchers:**
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
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        // Admin-only endpoints — adapt path pattern per service:
                        // identity-service: /api/v1/admin/**
                        // wallet-service: /api/v1/admin/**
                        // payment-service: /api/v1/admin/bundles/**
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                return roles.stream()
                        .map(Object::toString)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
            return List.of();
        });
        return converter;
    }
}
```

> For notification-service SecurityConfig: replace `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` with `.anyRequest().authenticated()` — no admin-only paths in notification-service.

---

### `JwtIssuer.java` modification — add `issueAdminToken`

**Analog:** `services/identity-service/src/main/java/com/smsreseller/identity/token/JwtIssuer.java` (lines 55–68)

**Existing `issueAccessToken` pattern to mirror:**
```java
public String issueAccessToken(UUID userId, VerificationStatus status) {
    Instant now = Instant.now();

    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES))
            .claim("verification_status", status.name())
            .claim("roles", List.of("ROLE_USER"))
            .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
}
```

**New method to add — `issueAdminToken`:**
```java
// Add below issueAccessToken. 60-min TTL per RESEARCH.md Pitfall 6.
// roles:["ROLE_ADMIN"] satisfies existing hasRole("ADMIN") guards in messaging-service + others.
@Value("${app.jwt.admin-token-ttl-minutes:60}")
private long adminTokenTtlMinutes;

public String issueAdminToken(UUID adminId) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(adminId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(adminTokenTtlMinutes, ChronoUnit.MINUTES))
            .claim("roles", List.of("ROLE_ADMIN"))
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
}
```

---

### `AdminLoginController.java` (identity-service — new)

**Analog:** `services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdAdminController.java` (lines 1–64)

**Imports + controller skeleton pattern (lines 1–23):**
```java
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminLoginService adminLoginService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request) {
        // adminLoginService.login checks BCrypt hash, calls jwtIssuer.issueAdminToken
        String token = adminLoginService.login(request.email(), request.password());
        return ResponseEntity.ok(Map.of("accessToken", token));
    }
}
```

**SecurityConfig must permit `/api/v1/auth/admin/login` (no JWT required):**
```java
.requestMatchers("/api/v1/auth/admin/login", "/actuator/health/**", "/error").permitAll()
```

---

### `AdminUserSearchController.java` / `AdminLedgerController.java` (identity-service / wallet-service)

**Analog:** `services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdAdminController.java`

**ADMIN-guarded controller pattern (lines 32–44):**
```java
// Route protected by SecurityConfig: /api/v1/admin/** requires hasRole("ADMIN")
// No subject-scoping — admin sees all users (contrast with analytics endpoints)
@GetMapping
public ResponseEntity<Page<UserSummaryDto>> searchUsers(
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(adminUserService.search(q, PageRequest.of(page, size)));
}
```

**Error handling pattern (lines 37–44 — SenderIdAdminController):**
```java
try {
    SenderIdRequest req = senderIdService.approve(id);
    return ResponseEntity.ok(SenderIdDto.SenderIdResponse.from(req));
} catch (IllegalStateException e) {
    if (e.getMessage().contains("not found")) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.status(409).body(Map.of("error", "invalid_state", "message", e.getMessage()));
}
```

---

### `AdminBundleController.java` (payment-service — new CRUD on existing BundleRepository)

**Analog:** `services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleController.java` (lines 1–41)

**Read-only pattern to extend with CRUD:**
```java
@RestController
@RequestMapping("/api/v1/admin/bundles")   // /api/v1/admin/** → hasRole("ADMIN") in SecurityConfig
@RequiredArgsConstructor
public class AdminBundleController {

    private final BundleRepository bundleRepository;

    @GetMapping
    public List<BundleDto> list() {
        return bundleRepository.findAll().stream().map(BundleDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<BundleDto> create(@Valid @RequestBody BundleSaveRequest req) { ... }

    @PutMapping("/{id}")
    public ResponseEntity<BundleDto> update(@PathVariable UUID id, @Valid @RequestBody BundleSaveRequest req) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { ... }
}
```

> `@Positive @Min(1)` on `priceTzs` and `smsCount` fields of `BundleSaveRequest` — per RESEARCH.md security threat table.

---

### `RefundController.java` (wallet-service — existing, no changes for admin manual refund)

**Analog:** `services/wallet-service/src/main/java/com/smsreseller/wallet/refund/RefundController.java` (lines 1–47)

The existing `POST /api/v1/wallet/refunds` endpoint is reused by admin-web. The admin SecurityConfig must ensure this endpoint is accessible with ROLE_ADMIN tokens. No code changes needed — admin-web calls it with the admin JWT, and the wallet-service SecurityConfig already requires `authenticated()` for all requests.

---

### `DeliveryReceiptService.java` modification — add CampaignCompleted outbox emit (D-12 gap fix)

**File:** `services/messaging-service/src/main/java/com/smsreseller/messaging/message/DeliveryReceiptService.java`

**Existing method to modify (lines 75–93):**
```java
private void checkCampaignCompletion(UUID campaignId) {
    List<OutboundMessage> messages = outboundMessageRepository.findByCampaignId(campaignId);
    if (messages.isEmpty()) { return; }

    boolean allTerminal = messages.stream().allMatch(m ->
            m.getStatus() == MessageStatus.DELIVERED || m.getStatus() == MessageStatus.FAILED);

    if (allTerminal) {
        campaignRepository.findById(campaignId).ifPresent(campaign -> {
            if (campaign.getStatus() != CampaignStatus.COMPLETED) {
                campaign.setStatus(CampaignStatus.COMPLETED);
                campaignRepository.save(campaign);
                log.info("Campaign {} set to COMPLETED — all {} messages terminal", campaignId, messages.size());
                // ← INSERT outbox emit here (see below)
            }
        });
    }
}
```

**Outbox emit to insert after the `log.info` call — follow OutboxEntry pattern:**
```java
// After campaign.setStatus + save + log.info, write outbox row:
long delivered = messages.stream().filter(m -> m.getStatus() == MessageStatus.DELIVERED).count();
long failed = messages.stream().filter(m -> m.getStatus() == MessageStatus.FAILED).count();
OutboxEntry outbox = OutboxEntry.builder()
        .id(UUID.randomUUID())
        .eventId(UUID.randomUUID())
        .aggregateType("Campaign")
        .aggregateId(campaignId.toString())
        .eventType("CampaignCompleted")
        .payload(buildCampaignCompletedPayload(campaign.getUserId(), campaignId, messages.size(), (int) delivered, (int) failed))
        .build();
outboxRepository.save(outbox);
// OutboxRelay @Scheduled polls and publishes to messaging.events/messaging.CampaignCompleted
```

---

### `V5__seed_admin_user.sql` (identity-service Flyway migration)

**Analog:** `services/payment-service/src/main/resources/db/migration/V2__seed_sms_bundles.sql`

**Seed pattern with ON CONFLICT DO NOTHING:**
```sql
-- V5__seed_admin_user.sql
-- Seeds the operator admin account (D-02). Password BCrypt hash injected via ${ADMIN_PASSWORD_HASH}
-- Flyway placeholder substitution: set flyway.placeholders.admin-password-hash in application.yml
-- pointing to K8s Secret value. Do NOT hardcode a real hash in VCS.
INSERT INTO users (id, email, phone, full_name, password_hash, role, verification_status, created_at)
VALUES (
    gen_random_uuid(),
    '${adminEmail}',
    NULL,
    'Platform Admin',
    '${adminPasswordHash}',  -- Flyway placeholder — value from env/K8s Secret
    'ADMIN',
    'VERIFIED',
    now()
)
ON CONFLICT (email) DO NOTHING;
```

> Flyway placeholder syntax requires `flyway.placeholder-replacement=true` (default true) and `flyway.placeholders.adminEmail` + `flyway.placeholders.adminPasswordHash` set via `application.yml` reading from env vars. This avoids hardcoding credentials in SQL.

---

### `AbstractNotificationIntegrationTest.java` (notification-service)

**Analog:** `services/identity-service/src/test/java/com/smsreseller/identity/AbstractIntegrationTest.java` (lines 1–73)

**Full pattern — copy, change package + container config:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"stub", "test"})
public abstract class AbstractNotificationIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("notification_test")
                    .withUsername("test").withPassword("test");

    // No Redis needed for notification-service (no OTP/session)
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

---

### `UserVerifiedConsumerIT.java` (notification-service test)

**Analog:** `services/wallet-service/src/test/java/com/smsreseller/wallet/UserVerifiedConsumerIT.java` (lines 1–108)

**Core test structure to mirror (lines 57–107):**
```java
@Test
void userVerifiedEventGrantsBonusIdempotently() throws Exception {
    UUID userId = UUID.randomUUID();
    String eventId = UUID.randomUUID().toString();

    String payload = objectMapper.writeValueAsString(Map.of(
            "eventId", eventId, "userId", userId.toString(), "freeCredits", 50));
    var message = MessageBuilder.withBody(payload.getBytes())
            .andProperties(new MessageProperties()).build();
    message.getMessageProperties().setContentType("application/json");

    rabbitTemplate.send("identity.events", "identity.UserVerified", message);

    // Assert notification row created
    await().atMost(10, SECONDS).untilAsserted(() -> {
        var notifications = notificationRepository.findByUserId(userId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.NIDA_VERIFIED);
    });

    // IDEMPOTENCY: re-deliver same eventId — must not create second row
    rabbitTemplate.send("identity.events", "identity.UserVerified", message);
    await().atMost(5, SECONDS).pollDelay(2, SECONDS).untilAsserted(() -> {
        assertThat(notificationRepository.findByUserId(userId)).hasSize(1);
    });
}
```

---

### Analytics controllers (messaging-service + wallet-service — new endpoints)

**Analog:** `services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleController.java`

**JWT-scoped read controller pattern:**
```java
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class CampaignAnalyticsController {

    private final CampaignAnalyticsService analyticsService;

    // ANLX-01: delivery stats scoped to JWT subject (no IDOR risk — user sees only their own)
    @GetMapping("/campaigns/{id}/stats")
    public ResponseEntity<CampaignStatsDto> getCampaignStats(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(analyticsService.getStats(userId, id));
    }

    // ANLX-03: operator rates — scoped to userId from JWT
    @GetMapping("/operator-rates")
    public ResponseEntity<List<OperatorRateDto>> getOperatorRates(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(analyticsService.getOperatorRates(userId));
    }
}
```

> Inject `JwtAuthenticationToken` directly in method signature (Spring Security resource-server auto-populates). Do NOT use `SecurityContextHolder` thread-local — virtual threads prefer method injection.

**JPQL aggregate query pattern (from RESEARCH.md Pattern 5):**
```java
// In OutboundMessageRepository:
@Query("""
    SELECT m.provider AS operator, m.status AS status, COUNT(m) AS count
    FROM OutboundMessage m
    WHERE m.userId = :userId
    GROUP BY m.provider, m.status
""")
List<OperatorRateRow> findOperatorRatesByUser(@Param("userId") UUID userId);
```

---

## Shared Patterns

### Idempotency Gate
**Source:** `services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/ProcessedEventRepository.java` (full file, lines 1–42)
**Apply to:** All AMQP consumer classes in notification-service and audit consumer in admin/audit module.
```java
if (!processedEventRepository.tryInsert(event.eventId())) {
    log.info("Duplicate {} ignored: eventId={}", event.getClass().getSimpleName(), event.eventId());
    return;
}
```

### AMQP Consumer Structure
**Source:** `services/wallet-service/src/main/java/com/smsreseller/wallet/consumer/UserVerifiedConsumer.java` (lines 34–67)
**Apply to:** All 4 notification-service consumer classes; audit consumer.
- `@Component @RequiredArgsConstructor @Slf4j` class annotation triple
- `@RabbitListener @QueueBinding @Queue @Exchange` on consumer method
- `@Transactional` on every consumer method
- idempotency guard first, business action second, `log.info` last

### SecurityConfig (resource-server, ADMIN-guarded paths)
**Source:** `services/messaging-service/src/main/java/com/smsreseller/messaging/config/SecurityConfig.java` (lines 1–78)
**Apply to:** notification-service SecurityConfig, identity-service SecurityConfig (extend with `/api/v1/admin/**`), wallet-service SecurityConfig (extend with `/api/v1/admin/**`), payment-service SecurityConfig (extend with `/api/v1/admin/bundles/**`).
- CSRF disabled, STATELESS session
- `jwtAuthenticationConverter()` reading `roles` claim — copy verbatim
- `hasRole("ADMIN")` for admin paths, `.anyRequest().authenticated()` for remainder

### Outbox Emit Pattern
**Source:** `services/wallet-service/src/main/java/com/smsreseller/wallet/outbox/OutboxEntry.java` + `services/messaging-service/src/main/java/com/smsreseller/messaging/outbox/OutboxRelay.java`
**Apply to:** `DeliveryReceiptService.checkCampaignCompletion()` upstream gap fix (D-12).
- Build `OutboxEntry` with `eventId`, `aggregateType`, `aggregateId`, `eventType`, `payload`
- Save in same `@Transactional` as the status update
- `OutboxRelay` `@Scheduled` polls unsent rows and publishes to exchange

### Flyway Seed Pattern
**Source:** `services/payment-service/src/main/resources/db/migration/V2__seed_sms_bundles.sql`
**Apply to:** `V5__seed_admin_user.sql` in identity-service.
- Use Flyway placeholder substitution for credentials (not hardcoded)
- `ON CONFLICT (email) DO NOTHING` for safe re-run

### Controller Error Handling
**Source:** `services/messaging-service/src/main/java/com/smsreseller/messaging/senderid/SenderIdAdminController.java` (lines 37–44)
**Apply to:** All new admin controllers in identity-service, wallet-service, payment-service.
```java
try { ... }
catch (IllegalStateException e) {
    if (e.getMessage().contains("not found")) return ResponseEntity.notFound().build();
    return ResponseEntity.status(409).body(Map.of("error", "invalid_state", "message", e.getMessage()));
}
```

### Testcontainers Integration Test Base
**Source:** `services/identity-service/src/test/java/com/smsreseller/identity/AbstractIntegrationTest.java` (lines 36–73)
**Apply to:** notification-service `AbstractNotificationIntegrationTest`, all service-level IT bases added in Phase 5.
- Static container start (NOT `@Testcontainers/@Container`) — avoids context-cache failures
- `@ServiceConnection` on Postgres, `@DynamicPropertySource` for RabbitMQ (no `@ServiceConnection` for RabbitMQ in current setup)
- `@ActiveProfiles({"stub", "test"})` — activate stubs for channels/push

---

## No Analog Found (NOVEL files)

Files with no close match in the codebase — planner must use RESEARCH.md patterns and UI-SPEC instead:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `apps/admin-web/app/(auth)/login/page.tsx` | component | request-response | First Next.js app in project; no frontend exists |
| `apps/admin-web/app/(auth)/login/actions.ts` | Server Action | request-response | Server Actions not used elsewhere |
| `apps/admin-web/middleware.ts` | middleware | request-response | No Next.js middleware exists in project |
| `apps/admin-web/app/(admin)/layout.tsx` | component | — | No React layout components exist |
| `apps/admin-web/app/(admin)/**/*.tsx` (6 screens) | component | request-response | No TSX/JSX files exist in project |
| `apps/admin-web/lib/api.ts` + `lib/auth.ts` | utility | request-response | No TypeScript utilities exist in project |
| `apps/admin-web/vitest.config.mts` + `playwright.config.ts` | config | — | No frontend test configs exist |
| `apps/admin-web/Dockerfile` | config | — | No existing admin-web Dockerfile (Phase 1 placeholder) |

**Planner must reference for NOVEL files:**
- RESEARCH.md Pattern 2 (httpOnly cookie login Server Action) — lines 323–360
- RESEARCH.md Pattern 3 (Server Component calling backend API) — lines 362–377
- RESEARCH.md Pattern 1 (multi-exchange consumer) — for notification-service consumer table
- UI-SPEC.md §Screen-by-Screen Interaction Contract — for each screen's element/copy spec
- UI-SPEC.md §Component Inventory — shadcn install command for Wave 0
- UI-SPEC.md §Design System — `npx shadcn@3.5 init`, zinc base, lucide-react icons

---

## Metadata

**Analog search scope:** `services/` (all 5 existing services), `services/*/src/test/`
**Files read:** 15 source files + 4 migration files
**Pattern extraction date:** 2026-06-21
