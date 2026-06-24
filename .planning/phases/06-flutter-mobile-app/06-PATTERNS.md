# Phase 6: Flutter Mobile App — Pattern Map

**Mapped:** 2026-06-22
**Files analyzed:** 30 (4 backend additions + 26 Flutter files/groups)
**Analogs found:** 4 / 4 backend files have analogs; 26 Flutter files are NOVEL

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `payment-service: PaymentController` + `GET /{id}` | controller | request-response | `PaymentController.java` (existing `GET /api/v1/payments`) | exact — same file, add one `@GetMapping("/{id}")` |
| `messaging-service: CreateCampaignRequest` + `contactIds[]` | model / DTO | CRUD | `CreateCampaignRequest.java` (existing record) | exact — same file, add one field |
| `messaging-service: CampaignService.executeSend` + flat-contact path | service | CRUD | `CampaignService.executeSend` (existing method) | exact — same method, branch on contactIds presence |
| `identity-service: SessionController` + `GET /auth/me` | controller | request-response | `SessionController.java` (existing `/auth/refresh`, `/auth/logout`) | exact — same file, add one `@GetMapping("/me")` |
| `notification-service: NotificationController` + `PATCH /{id}/read` | controller | request-response | `NotificationController.java` (existing `@GetMapping`) | role-match — same file, add one `@PatchMapping` |
| `apps/customer-app/` — ALL Flutter files | various (see below) | various | **NOVEL — no Flutter code exists** | n/a |

---

## Pattern Assignments — Backend Additions

### D-11: `GET /api/v1/payments/{id}` — single payment status

**Analog file:** `services/payment-service/src/main/java/com/smsreseller/payment/payment/PaymentController.java`

**What exists (lines 63–71) — `GET /api/v1/payments` list:**
```java
@GetMapping
public Page<PaymentDto> history(
        JwtAuthenticationToken auth,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    UUID userId = UUID.fromString(auth.getToken().getSubject());
    return paymentService.history(userId, PageRequest.of(page, size))
            .map(p -> PaymentDto.from(p, timeoutSeconds));
}
```

**IDOR pattern (lines 47–55) — copy for the new GET `/{id}`:**
```java
// userId is ALWAYS from the JWT subject — never from the path or request body
UUID userId = UUID.fromString(auth.getToken().getSubject());
```

**New endpoint to add (copy this pattern exactly):**
```java
/**
 * Returns a single payment by id, scoped to the authenticated user (D-11, PYMT-status).
 * Returns 404 if the payment does not belong to the requesting user (IDOR guard).
 */
@GetMapping("/{id}")
public ResponseEntity<PaymentDto> getById(
        JwtAuthenticationToken auth,
        @PathVariable UUID id) {
    UUID userId = UUID.fromString(auth.getToken().getSubject());
    return paymentService.findByIdAndUser(id, userId)
            .map(p -> PaymentDto.from(p, timeoutSeconds))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```

**Service method to add to `PaymentService.java`:**
```java
// Pattern: same as CampaignService.findByIdAndUser — uses (id, userId) compound lookup
@Transactional(readOnly = true)
public Optional<Payment> findByIdAndUser(UUID id, UUID userId) {
    return paymentRepository.findByIdAndUserId(id, userId);
}
```

**Repository method to add to `PaymentRepository.java`:**
```java
// Pattern: JPA derived query — same as CampaignRepository.findByIdAndUserId
Optional<Payment> findByIdAndUserId(UUID id, UUID userId);
```

**`PaymentDto` (lines 1–33) — no changes needed.** The `status` field (`payment.getStatus().name()`) already returns the string the Flutter countdown needs (`"PENDING"`, `"CONFIRMED"`, `"EXPIRED"`).

---

### D-12: Campaign `contactIds[]` targeting

**Analog file:** `services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CreateCampaignRequest.java`

**What exists (lines 14–30):**
```java
public record CreateCampaignRequest(
        String name,
        @NotBlank(message = "body must not be blank")
        String body,
        @NotBlank(message = "senderId must not be blank")
        @Size(max = 11, message = "senderId must be at most 11 characters")
        String senderId,
        @NotNull(message = "groupIds must not be null")
        @Size(min = 1, message = "at least one groupId is required")
        Set<UUID> groupIds,
        Instant scheduledAt
) {}
```

**What to change — relax the `groupIds` constraint and add `contactIds`:**
```java
public record CreateCampaignRequest(
        String name,
        @NotBlank(message = "body must not be blank")
        String body,
        @NotBlank(message = "senderId must not be blank")
        @Size(max = 11, message = "senderId must be at most 11 characters")
        String senderId,
        // groupIds is now nullable; either groupIds OR contactIds must be provided
        Set<UUID> groupIds,
        // D-12: flat-contact targeting for Flutter MVP (no groups)
        Set<UUID> contactIds,
        Instant scheduledAt
) {}
```

**Validation note:** Add a class-level `@AssertTrue` or service-layer guard:
```java
// In CampaignService.create() — copy the IllegalStateException pattern already used in cancel():
if ((request.groupIds() == null || request.groupIds().isEmpty()) &&
    (request.contactIds() == null || request.contactIds().isEmpty())) {
    throw new IllegalStateException("At least one groupId or contactId is required");
}
```

**`CampaignService.executeSend` analog (lines 178–242) — branch on contactIds:**

The existing flow calls `contactRecipientClient.getRecipientsForGroups(campaign.getGroupIds(), userId)` at line 182. Add a parallel `contactRecipientClient.getRecipientsByContactIds(campaign.getContactIds(), userId)` branch. The rest of the dispatch pipeline (reserve credits → persist OutboundMessage → publish AMQP) is identical — copy it unchanged.

```java
// Step 1: expand recipients — group path OR flat-contact path (D-12)
List<String> recipients;
if (campaign.getContactIds() != null && !campaign.getContactIds().isEmpty()) {
    recipients = contactRecipientClient.getRecipientsByContactIds(campaign.getContactIds(), userId);
} else {
    recipients = contactRecipientClient.getRecipientsForGroups(campaign.getGroupIds(), userId);
}
// ... remainder of executeSend is unchanged
```

**`Campaign` entity:** add a `contactIds` field using the same `@ElementCollection` pattern as `groupIds`.

**`ContactRecipientClient`:** add `getRecipientsByContactIds(Set<UUID> contactIds, UUID userId)` mirroring the existing `getRecipientsForGroups` method — same RestClient call pattern, different contact-service endpoint (`GET /api/v1/contacts/phones?ids=...` or a POST body).

---

### D-13: `GET /auth/me` — lightweight verification status read

**Analog file:** `services/identity-service/src/main/java/com/smsreseller/identity/auth/SessionController.java`

**Pattern to copy — `@PostMapping("/logout")` (lines 119–132): authenticated endpoint reading subject from `@AuthenticationPrincipal Jwt`:**
```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody Map<String, @NotBlank String> body) {
    UUID userId = UUID.fromString(jwt.getSubject());
    // ...
}
```

**New endpoint to add in `SessionController.java`:**
```java
/**
 * Lightweight status read — returns the caller's verification_status and userId
 * without rotating the refresh token (D-13, MOBL-02).
 *
 * <p>Used by the Flutter PENDING screen to poll verification state cheaply.
 * JWT-authenticated (anyRequest().authenticated() — no SecurityConfig change needed).
 */
@GetMapping("/me")
public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    return ResponseEntity.ok(new MeResponse(userId, user.getStatus().name()));
}

// Response DTO (add alongside SessionController or in web/dto/)
public record MeResponse(UUID userId, String status) {}
```

**SecurityConfig analog (lines 52–63) — `GET /auth/me` requires NO permit-all entry.** It must fall under `anyRequest().authenticated()` (the existing catch-all). No SecurityConfig change is needed. The `/auth/me` path is not in the `permitAll` list.

**`TokenResponse.java` (lines 1–15) — the `status` field confirms pattern:**
```java
// status echoes verification_status so the client does not need to decode the JWT
public record TokenResponse(String accessToken, String refreshToken, String status) {}
// MeResponse follows the same intent — status without token rotation
```

---

### D-14 (optional): `PATCH /api/v1/notifications/{id}/read`

**Analog file:** `services/notification-service/src/main/java/com/smsreseller/notification/notification/NotificationController.java`

**Full existing controller (lines 1–48) — copy the IDOR + auth pattern:**
```java
@GetMapping
public ResponseEntity<Page<NotificationDto>> getMyNotifications(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        JwtAuthenticationToken auth) {
    UUID userId = UUID.fromString(auth.getToken().getSubject());
    // ... scope strictly to JWT subject
}
```

**`Notification` entity (line 50): `read` field already exists:**
```java
@Column(name = "read", nullable = false)
private boolean read = false;
```

**New endpoint pattern (copy `getMyNotifications` IDOR guard):**
```java
/**
 * Mark a single notification as read (D-14 optional).
 * IDOR guard: only marks notification if it belongs to the JWT subject.
 * Returns 204 on success, 404 if not found or not owned.
 */
@PatchMapping("/{id}/read")
public ResponseEntity<Void> markAsRead(
        @PathVariable UUID id,
        JwtAuthenticationToken auth) {
    UUID userId = UUID.fromString(auth.getToken().getSubject());
    boolean updated = notificationService.markAsRead(id, userId);
    return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
}
```

**`NotificationService.markAsRead` (add to `NotificationService.java`):**
```java
@Transactional
public boolean markAsRead(UUID id, UUID userId) {
    return notificationRepository.findByIdAndUserId(id, userId)
            .map(n -> { n.markRead(); notificationRepository.save(n); return true; })
            .orElse(false);
}
```

Add `markRead()` mutator to `Notification` entity (breaks `@Getter`-only pattern; add explicit setter or a package-private method). Add `findByIdAndUserId(UUID id, UUID userId)` to `NotificationRepository` (same derived-query pattern as PaymentRepository).

---

## Shared Patterns — Backend Additions

### IDOR / Owner-scope guard
**Source:** `PaymentController.java` lines 52, 67 and `CampaignController.java` lines 49, 62
```java
// Copy this exact line into every new GET/PATCH endpoint:
UUID userId = UUID.fromString(auth.getToken().getSubject());
// Then pass userId as a second arg to every repository/service call so the WHERE clause
// includes user_id = :userId. Never look up by id alone.
```

### 404 on missing-or-not-owned record
**Source:** `CampaignController.get()` lines 74–83
```java
return campaignService.findByIdAndUser(id, userId)
        .map(...)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
```

### JWT auth parameter style
- Controllers that need auth state use `JwtAuthenticationToken auth` as parameter (PaymentController, CampaignController).
- Controllers that need only the subject use `@AuthenticationPrincipal Jwt jwt` (SessionController logout).
- Both styles work; pick whichever is already used in the file being extended.

---

## Pattern Assignments — Flutter Files (NOVEL)

No Flutter code exists in this repository. All Flutter files listed below are NOVEL. The planner must reference RESEARCH.md architectural patterns and UI-SPEC screen contracts as the authoritative source — there is no codebase analog to copy from.

### Reference sources for all Flutter files

| Source | What it provides |
|--------|-----------------|
| `06-RESEARCH.md § Architecture Patterns` | Riverpod auth state (Pattern 1), Dio QueuedInterceptor (Pattern 2), go_router guards (Pattern 3), NIDA PENDING poll (Pattern 4), Hive CE cache (Pattern 5), l10n ARB (Pattern 6), Azampay countdown (Pattern 7), SMS char counter (Pattern 8) |
| `06-RESEARCH.md § Standard Stack` | Exact package versions and pubspec.yaml structure |
| `06-RESEARCH.md § Common Pitfalls` | 7 pitfalls with exact avoidance instructions |
| `06-UI-SPEC.md § Screen-by-Screen Visual Contracts` | Per-screen widget structure, spacing, copy keys |
| `06-UI-SPEC.md § Component Inventory` | 12 custom widgets with usage rules |
| `06-UI-SPEC.md § Copywriting Contract` | All ARB key names in EN + SW |
| `06-UI-SPEC.md § Interaction Contracts` | Loading state flows, polling intervals, guard order |

### Flutter file groups and their RESEARCH.md / UI-SPEC pointers

#### Wave 0 — Scaffold + Cross-cutting

| File | NOVEL | Key Reference |
|------|-------|---------------|
| `apps/customer-app/pubspec.yaml` | NOVEL | RESEARCH.md § Standard Stack → pubspec.yaml structure |
| `apps/customer-app/l10n.yaml` | NOVEL | RESEARCH.md Pattern 6 § `l10n.yaml`; UI-SPEC § l10n Architecture |
| `apps/customer-app/lib/l10n/app_en.arb` | NOVEL | UI-SPEC § Copywriting Contract (all ARB keys listed) |
| `apps/customer-app/lib/l10n/app_sw.arb` | NOVEL | UI-SPEC § Copywriting Contract (Swahili column) |
| `apps/customer-app/lib/main.dart` | NOVEL | RESEARCH.md § Hive CE Initialization code example; Pattern 1 (`ProviderScope` wrap) |
| `apps/customer-app/lib/core/dio/dio_client.dart` + `auth_interceptor.dart` | NOVEL | RESEARCH.md Pattern 2 (QueuedInterceptor full code); Pitfall 1, Pitfall 2 |
| `apps/customer-app/lib/core/hive/hive_boxes.dart` | NOVEL | RESEARCH.md § Hive CE Initialization; Pattern 5 (box layout: `balance`, `contacts`, `campaigns`, `notifications`) |
| `apps/customer-app/lib/core/router/app_router.dart` | NOVEL | RESEARCH.md Pattern 3 (go_router redirect guards full code); UI-SPEC § Interaction Contracts → go_router Navigation Guards |
| `apps/customer-app/lib/core/auth/auth_notifier.dart` + `auth_state.dart` | NOVEL | RESEARCH.md Pattern 1 (Freezed sealed AuthState + AsyncNotifier full code); Pitfall 3 |

#### Wave 1 — Auth + Session screens

| File | NOVEL | Key Reference |
|------|-------|---------------|
| `lib/features/onboarding/splash_screen.dart` | NOVEL | UI-SPEC § Splash Screen visual contract |
| `lib/features/onboarding/onboarding_screen.dart` | NOVEL | UI-SPEC § Onboarding — 3-slide PageView; ARB keys `onboardingGetStarted`, slide copy |
| `lib/features/auth/register_screen.dart` | NOVEL | UI-SPEC § Register — field order, `FilledButton`, inline errors; ARB `registerSubmitButton` |
| `lib/features/auth/nida_pending_screen.dart` | NOVEL | UI-SPEC § NIDA Pending — locked route, pulsing circle, auto-poll; RESEARCH.md Pattern 4 |
| `lib/features/auth/login_screen.dart` | NOVEL | UI-SPEC § Login — email + password, `errorLoginFailed` inline |

#### Wave 2 — Dashboard + Payments

| File | NOVEL | Key Reference |
|------|-------|---------------|
| `lib/features/dashboard/dashboard_screen.dart` | NOVEL | UI-SPEC § Dashboard — `SliverAppBar`, `BalanceCard`, recent campaigns (≤5), FAB |
| `lib/features/dashboard/balance_provider.dart` | NOVEL | RESEARCH.md Pattern 5 (Hive cache-read + online-write full code) |
| `lib/features/payments/bundle_catalog_screen.dart` | NOVEL | UI-SPEC § Bundle Catalog — `BundleCard`, ascending price order, TZS formatting |
| `lib/features/payments/stk_purchase_screen.dart` | NOVEL | UI-SPEC § STK Purchase; RESEARCH.md Pattern 7 (CountdownWidget full code); D-11 polling `GET /api/v1/payments/{id}` |
| `lib/features/payments/payment_provider.dart` | NOVEL | RESEARCH.md Pattern 7; RESEARCH.md § Architecture Diagram `PaymentProvider` |

#### Wave 3 — Contacts + Campaigns

| File | NOVEL | Key Reference |
|------|-------|---------------|
| `lib/features/contacts/contact_list_screen.dart` | NOVEL | UI-SPEC § Contact List — `SliverAppBar` + `SearchBar`, `ContactListTile`, `StaleIndicator` |
| `lib/features/contacts/add_contact_screen.dart` | NOVEL | UI-SPEC § Add Contact — online-only write, `LoadingOverlay` |
| `lib/features/contacts/contacts_provider.dart` | NOVEL | RESEARCH.md Pattern 5 (Hive cache pattern applied to contacts box) |
| `lib/features/campaigns/composer_screen.dart` | NOVEL | UI-SPEC § Campaign Composer — recipient picker modal, `SmsCharCounter`, D-12 `contactIds[]` payload |
| `lib/features/campaigns/history_screen.dart` | NOVEL | UI-SPEC § Campaign History — infinite scroll, `CampaignListTile`, `CampaignStatusChip` |
| `lib/features/campaigns/detail_screen.dart` | NOVEL | UI-SPEC § Campaign Detail — stat tiles, per-message `ListView` |
| `lib/shared/widgets/sms_char_counter.dart` | NOVEL | RESEARCH.md Pattern 8 (GSM-7 / UCS-2 SmsCounter Dart class) |
| `lib/features/notifications/notification_feed_screen.dart` | NOVEL | UI-SPEC § Notification Feed — type→icon map, unread tileColor, 30 s poll |
| `lib/features/notifications/notification_provider.dart` | NOVEL | RESEARCH.md § Architecture Diagram `NotificationFeedProvider`; UI-SPEC § Notification Feed Polling |

#### Test harness

| File | NOVEL | Key Reference |
|------|-------|---------------|
| `test/core/dio/auth_interceptor_test.dart` | NOVEL | RESEARCH.md § Widget Test with mock Dio (full code example); mocktail pattern |
| `test/core/router/redirect_guard_test.dart` | NOVEL | UI-SPEC § go_router Navigation Guards; RESEARCH.md § Validation Architecture MOBL test map |
| `test/features/**/*_test.dart` | NOVEL | RESEARCH.md § Widget Test with mock Dio; § Phase Requirements → Test Map |
| `integration_test/app_test.dart` | NOVEL | RESEARCH.md § Test Framework — `integration_test: sdk: flutter` (NOT pub.dev package); Pitfall 6 |

---

## No Analog Found

Flutter-specific files have no analog anywhere in the repository (first Flutter app). This is expected and documented per the phase mandate. The planner must reference RESEARCH.md + UI-SPEC instead of codebase excerpts for all Flutter plan actions.

| File Group | Role | Data Flow | Reason |
|------------|------|-----------|--------|
| All `apps/customer-app/lib/**` | screen / provider / widget / utility | request-response, event-driven, cache | First Flutter app in the monorepo — no prior Flutter code |
| `apps/customer-app/test/**` + `integration_test/**` | test | n/a | First Flutter test harness — use `flutter_test` SDK + `mocktail` per RESEARCH.md |

---

## Metadata

**Analog search scope:** `services/payment-service`, `services/messaging-service`, `services/identity-service`, `services/notification-service`
**Java files scanned:** ~60
**Flutter files classified:** 26 groups (all NOVEL)
**Pattern extraction date:** 2026-06-22
