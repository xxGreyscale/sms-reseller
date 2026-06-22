---
phase: 05-notifications-admin-analytics
verified: 2026-06-22T00:00:00Z
status: passed
score: 16/16 must-haves verified
overrides_applied: 0
gap_closure: >
  ADMN-04 gap CLOSED 2026-06-22. Added GET /api/v1/internal/sender-ids (ADMIN-guarded,
  paged, newest-first, optional status filter) + repository queries; aligned admin-web
  sender-ids screen from the non-existent PENDING value to the real REQUESTED status
  (display label remains "Pending"). Proven by SenderIdAdminListIT (admin lists REQUESTED
  newest-first; non-admin 403). messaging suite + admin-web Vitest 28/28 + build all green.
  Also fixed an omitted commit: UserRepository.searchByEmailOrPhone (ADMN-02) was left
  uncommitted by 05-03, breaking identity-service compilation on main — now committed.
gaps:
  - truth: "Admin can view the sender-ID approval queue (ADMN-04)"
    status: resolved
    reason: >
      The admin-web sender-ids page (app/(admin)/sender-ids/page.tsx) calls
      GET /api/v1/internal/sender-ids to populate the queue table, but
      SenderIdAdminController (messaging-service) maps to /api/v1/internal/sender-ids
      with only two POST endpoints ({id}/approve, {id}/reject). No @GetMapping at root
      exists. At runtime the page throws an API error and renders the error fallback UI.
      ADMN-04 requires "view … the sender ID approval queue" — the view half is broken.
    artifacts:
      - path: "services/messaging-service/src/main/java/com/opendesk/messaging/senderid/SenderIdAdminController.java"
        issue: "Missing GET / handler at /api/v1/internal/sender-ids for paginated queue listing"
      - path: "apps/admin-web/app/(admin)/sender-ids/page.tsx"
        issue: "Calls GET /api/v1/internal/sender-ids which returns 405/404 — no matching backend endpoint"
    missing:
      - "Add @GetMapping at SenderIdAdminController root: paged findAll (or findByStatus) returning Page<SenderIdResponse>, ADMIN-guarded via SecurityConfig /api/v1/internal/**"
      - "SenderIdRepository needs findByStatusOrderByCreatedAtDesc or equivalent for paged admin listing"
human_verification:
  - test: "Open /sender-ids in admin-web with a running backend. Confirm the queue table loads, shows pending rows, Approve and Reject buttons function, and the Reject dialog requires a reason."
    expected: "Queue populates; Approve turns row green / removes it from pending; Reject with blank reason is blocked; with reason shows success toast."
    why_human: "Requires a running Next.js dev server + Spring Boot messaging-service; cannot verify UI rendering or toast/dialog behavior with grep."
  - test: "Confirm login page redirects to /users after successful ROLE_ADMIN login and sidebar nav renders all sections."
    expected: "Auth cookie set; middleware allows access to all /(admin) routes; sidebar links visible."
    why_human: "Middleware redirect and cookie behaviour require a running browser session."
---

# Phase 05: Notifications, Admin Panel & Analytics — Verification Report

**Phase Goal:** All user-facing notifications fire reliably from domain events, the Next.js admin panel gives operators full platform visibility and control, and users can view delivery and spend analytics.
**Requirements:** NOTF-01..06, ADMN-01..07, ANLX-01..03.
**Verified:** 2026-06-22
**Status:** gaps_found — 1 blocker (ADMN-04 queue listing missing backend endpoint)
**Re-verification:** No — initial verification.

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                           | Status      | Evidence                                                                                                          |
|----|-----------------------------------------------------------------|-------------|-------------------------------------------------------------------------------------------------------------------|
| 1  | NOTF-01: UserVerified → NIDA_VERIFIED notification, once only  | ✓ VERIFIED  | `IdentityEventConsumer.onUserVerified` + `ProcessedEventRepository.tryInsert` ON CONFLICT DO NOTHING             |
| 2  | NOTF-02: PaymentConfirmed → PAYMENT_CONFIRMED notification      | ✓ VERIFIED  | `PaymentEventConsumer.onPaymentConfirmed` + idempotency gate                                                     |
| 3  | NOTF-03: LowCreditAlert → LOW_CREDIT notification               | ✓ VERIFIED  | `WalletEventConsumer.onLowCreditAlert` + idempotency gate                                                        |
| 4  | NOTF-04: ExpiryWarning → EXPIRY_WARNING notification            | ✓ VERIFIED  | `WalletEventConsumer.onExpiryWarning` + idempotency gate                                                         |
| 5  | NOTF-05: CampaignCompleted → CAMPAIGN_COMPLETED notification    | ✓ VERIFIED  | `MessagingEventConsumer.onCampaignCompleted` + idempotency gate; CampaignCompleted outbox in messaging-service   |
| 6  | NOTF-06: SenderIdDecided → SENDER_ID_DECIDED notification       | ✓ VERIFIED  | `MessagingEventConsumer.onSenderIdDecided` + idempotency gate; approve/reject branches both handled              |
| 7  | Notification feed is JWT-scoped (user A cannot see user B rows) | ✓ VERIFIED  | `NotificationController` derives userId from `auth.getToken().getSubject()` never from query param              |
| 8  | ADMN-01: Admin login issues ROLE_ADMIN JWT                      | ✓ VERIFIED  | `AdminLoginService.login` checks `UserRole.ADMIN`, calls `jwtIssuer.issueAdminToken`; JwtIssuer sets `roles:[ROLE_ADMIN]` |
| 9  | ADMN-02: Admin searches users by term                           | ✓ VERIFIED  | `AdminUserSearchController GET /api/v1/admin/users`; SecurityConfig guards `/api/v1/admin/**` with hasRole("ADMIN") |
| 10 | ADMN-03: Admin inspects any user's ledger                       | ✓ VERIFIED  | `AdminLedgerController GET /api/v1/admin/ledger/{userId}`; not subject-scoped                                    |
| 11 | ADMN-04: Admin views sender-ID queue and approves/rejects       | ✗ FAILED    | Approve/reject POSTs exist; **GET listing at /api/v1/internal/sender-ids is absent** — admin-web page will error |
| 12 | ADMN-05: Admin executes manual refunds                          | ✓ VERIFIED  | `RefundController POST /api/v1/wallet/refunds`; wallet SecurityConfig allows ROLE_ADMIN via anyRequest().authenticated(); admin-web `refunds/actions.ts` calls it |
| 13 | ADMN-06: Admin views full audit log (dual-source)               | ✓ VERIFIED  | `DomainEventAuditConsumer` (passive, idempotent, 4 events) + `AdminMutationController POST /api/v1/admin/audit/record`; `AuditController GET /api/v1/admin/audit` with date/actor filters |
| 14 | ADMN-07: Admin manages bundle catalog (CRUD)                    | ✓ VERIFIED  | `AdminBundleController` GET/POST/PUT/DELETE at `/api/v1/admin/bundles`; ADMIN-guarded via payment-service SecurityConfig |
| 15 | ANLX-01: User views per-campaign delivery stats                 | ✓ VERIFIED  | `CampaignAnalyticsController GET /api/v1/analytics/campaigns/{id}/stats`; JWT-scoped, returns 404 on non-ownership |
| 16 | ANLX-02: User views credit usage + spend trend                  | ✓ VERIFIED  | `CreditUsageController GET /api/v1/analytics/credit-usage`; JWT-scoped; wallet SecurityConfig guards `/api/v1/analytics/**` |
| 17 | ANLX-03: User views operator-level delivery rates               | ✓ VERIFIED  | `CampaignAnalyticsController GET /api/v1/analytics/operator-rates`; `OutboundMessageRepository.findOperatorRatesByUser` uses operator column (D-13) |

**Score:** 15/16 truths verified (ADMN-04 queue-listing half failed).

---

## Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `services/notification-service/src/.../consumer/IdentityEventConsumer.java` | ✓ VERIFIED | Passive @QueueBinding, idempotency gate, creates NIDA_VERIFIED |
| `services/notification-service/src/.../consumer/PaymentEventConsumer.java` | ✓ VERIFIED | PAYMENT_CONFIRMED consumer with idempotency |
| `services/notification-service/src/.../consumer/WalletEventConsumer.java` | ✓ VERIFIED | LOW_CREDIT + EXPIRY_WARNING consumers |
| `services/notification-service/src/.../consumer/MessagingEventConsumer.java` | ✓ VERIFIED | CAMPAIGN_COMPLETED + SENDER_ID_DECIDED |
| `services/notification-service/src/.../idempotency/ProcessedEventRepository.java` | ✓ VERIFIED | `ON CONFLICT DO NOTHING` native query; `tryInsert` returns boolean |
| `services/notification-service/src/.../notification/NotificationController.java` | ✓ VERIFIED | JWT-scoped feed; size clamped to 100 |
| `services/identity-service/src/.../admin/AdminLoginService.java` | ✓ VERIFIED | Rejects non-ADMIN roles; BCrypt verify; constant-time error message |
| `services/identity-service/src/.../admin/AdminUserSearchController.java` | ✓ VERIFIED | ADMIN-guarded via SecurityConfig |
| `services/wallet-service/src/.../admin/AdminLedgerController.java` | ✓ VERIFIED | Paged ledger, not subject-scoped |
| `services/messaging-service/src/.../senderid/SenderIdAdminController.java` | ✗ PARTIAL | Has approve/reject POST only — **no GET listing endpoint** |
| `services/wallet-service/src/.../refund/RefundController.java` | ✓ VERIFIED | Idempotent refund; wallet SecurityConfig allows ROLE_ADMIN |
| `services/admin-service/src/.../audit/AuditController.java` | ✓ VERIFIED | Filterable, paged, ADMIN-guarded |
| `services/admin-service/src/.../consumer/DomainEventAuditConsumer.java` | ✓ VERIFIED | 4 passive listeners; idempotency; writes via AuditService |
| `services/admin-service/src/.../audit/AdminMutationController.java` | ✓ VERIFIED | POST /api/v1/admin/audit/record for D-09a seam |
| `services/payment-service/src/.../bundle/AdminBundleController.java` | ✓ VERIFIED | Full CRUD; ADMIN-guarded |
| `services/messaging-service/src/.../analytics/CampaignAnalyticsController.java` | ✓ VERIFIED | ANLX-01 (campaign stats) + ANLX-03 (operator rates) |
| `services/wallet-service/src/.../analytics/CreditUsageController.java` | ✓ VERIFIED | ANLX-02 spend trend, JWT-scoped |
| `apps/admin-web/app/(admin)/sender-ids/page.tsx` | ✗ PARTIAL | UI exists; fetch calls non-existent GET endpoint — will throw at runtime |
| `apps/admin-web/app/(admin)/refunds/actions.ts` | ✓ VERIFIED | POSTs to /api/v1/wallet/refunds with Bearer token |
| `apps/admin-web/app/(admin)/sender-ids/actions.ts` | ✓ VERIFIED | approveSenderId + rejectSenderId POST to correct backend paths |
| `apps/admin-web/lib/api.ts` | ✓ VERIFIED | Typed fetch wrapper; Bearer token via `getToken()`; cache no-store |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| IdentityEventConsumer | identity.events | passive @QueueBinding ignoreDeclarationExceptions | ✓ WIRED | Routing key identity.UserVerified |
| MessagingEventConsumer | messaging.events | passive @QueueBinding | ✓ WIRED | Keys messaging.CampaignCompleted + messaging.SenderIdDecided |
| PaymentEventConsumer | payment.events | passive @QueueBinding | ✓ WIRED | Key payment.PaymentConfirmed |
| WalletEventConsumer | wallet.events | passive @QueueBinding | ✓ WIRED | Keys wallet.LowCreditAlert + wallet.ExpiryWarning |
| DomainEventAuditConsumer | identity/messaging/wallet events | passive @QueueBinding × 4 | ✓ WIRED | Separate queues per event type |
| admin-web sender-ids/page.tsx | GET /api/v1/internal/sender-ids | fetch in Server Component | ✗ NOT_WIRED | Endpoint does not exist in SenderIdAdminController |
| admin-web sender-ids/actions.ts | POST /api/v1/internal/sender-ids/{id}/approve|reject | Server Action fetch | ✓ WIRED | Matches SenderIdAdminController POST handlers |
| admin-web refunds/actions.ts | POST /api/v1/wallet/refunds | Server Action fetch | ✓ WIRED | Matches RefundController |
| JwtIssuer.issueAdminToken | ROLE_ADMIN claim | `claim("roles", List.of("ROLE_ADMIN"))` | ✓ WIRED | All resource-server SecurityConfigs read `roles` claim |

---

## TDD Red-Before-Green Verification

All plans show atomic commit triples in git log: `test(05-0N): RED` → `feat(05-0N): GREEN` → `docs(05-0N)`. Confirmed for plans 02 through 09. The 05-03 resume-boundary note in context (pre-converted RED test reused) is confirmed legitimate: commit `955c4e2 test(05-03): RED` exists before `8eaea04 feat(05-03): GREEN`.

---

## Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `notification-service/.../push/StubPushChannel.java` | No-op push implementation | INFO | Expected — D-04 deferred real FCM to post-MVP; stub is named and documented |
| `messaging-service/.../sms/StubSmsProvider.java` | No-op SMS send | INFO | Expected — Phase 4 pattern, documented |

No unreferenced TBD/FIXME/XXX markers found in phase 5 source files.

---

## Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| NOTF-01 (UserVerified → notification) | ✓ SATISFIED | IdentityEventConsumer + idempotency gate + IT (UserVerifiedConsumerIT) |
| NOTF-02 (PaymentConfirmed → notification) | ✓ SATISFIED | PaymentEventConsumer + IT |
| NOTF-03 (LowCreditAlert → notification) | ✓ SATISFIED | WalletEventConsumer + IT |
| NOTF-04 (ExpiryWarning → notification) | ✓ SATISFIED | WalletEventConsumer + IT |
| NOTF-05 (CampaignCompleted → notification) | ✓ SATISFIED | MessagingEventConsumer + CampaignCompleted outbox in messaging-service |
| NOTF-06 (SenderIdDecided → notification) | ✓ SATISFIED | MessagingEventConsumer + IT |
| ADMN-01 (Admin login, ROLE_ADMIN JWT) | ✓ SATISFIED | AdminLoginService + JwtIssuer.issueAdminToken + AdminLoginIT |
| ADMN-02 (Admin user search) | ✓ SATISFIED | AdminUserSearchController + AdminUserSearchIT |
| ADMN-03 (Admin ledger inspection) | ✓ SATISFIED | AdminLedgerController + AdminLedgerIT |
| ADMN-04 (Sender-ID queue view + approve/reject) | ✗ BLOCKED | Approve/reject POSTs work; **GET queue listing endpoint absent** — admin-web page broken |
| ADMN-05 (Manual refunds) | ✓ SATISFIED | RefundController; admin-web refunds/actions.ts; RefundIT |
| ADMN-06 (Audit log) | ✓ SATISFIED | Dual-source: DomainEventAuditConsumer + AdminMutationController; AuditController viewer; AuditLogIT |
| ADMN-07 (Bundle catalog CRUD) | ✓ SATISFIED | AdminBundleController + AdminBundleCatalogIT |
| ANLX-01 (Per-campaign delivery stats) | ✓ SATISFIED | CampaignAnalyticsController /campaigns/{id}/stats + CampaignAnalyticsIT |
| ANLX-02 (Credit usage spend trend) | ✓ SATISFIED | CreditUsageController + CreditUsageAnalyticsIT |
| ANLX-03 (Operator-level delivery rates) | ✓ SATISFIED | CampaignAnalyticsController /operator-rates + OperatorRateAnalyticsIT |

---

## Gaps Summary

**1 blocker — ADMN-04 sender-ID queue listing**

The admin-web Sender IDs page (`app/(admin)/sender-ids/page.tsx`) fetches `GET /api/v1/internal/sender-ids?status=PENDING&page=0&size=50`. `SenderIdAdminController` in messaging-service is mapped to `/api/v1/internal/sender-ids` but exposes only `POST /{id}/approve` and `POST /{id}/reject`. There is no `@GetMapping` at the root path, no `findByStatus` query in `SenderIdRepository`, and no alternative service that exposes this listing.

At runtime, the page will hit a `405 Method Not Allowed` (or `404` if the path is routed differently) and render the error fallback (`"Failed to load data. Refresh the page…"`). Approve/reject Server Actions do work correctly since those POST endpoints exist.

**Remediation (minimal):**
1. Add `findByStatusOrderByCreatedAtDesc(SenderIdStatus status, Pageable pageable)` to `SenderIdRepository`.
2. Add `@GetMapping` to `SenderIdAdminController` returning a `Page<SenderIdResponse>` (filter by `?status` param; default `PENDING`).
3. Messaging-service SecurityConfig already guards `/api/v1/internal/**` with `hasRole("ADMIN")` — no SecurityConfig change needed.
4. Add or extend an IT to assert the listing endpoint returns the expected page.

All 15 other must-haves (NOTF-01..06, ADMN-01/02/03/05/06/07, ANLX-01/02/03) are VERIFIED with substantive implementations and corresponding integration tests.

---

## Human Verification Required

### 1. Sender-ID Queue UI (after fix)

**Test:** After adding the GET listing endpoint, open `/sender-ids` in admin-web with a running backend. Submit a sender-ID request as a regular user, then log in as admin and visit the Sender IDs page.
**Expected:** Pending request appears in the table. Clicking Approve changes its status and shows a success toast. Clicking Reject with an empty reason is blocked; entering a reason and confirming moves the request to rejected state.
**Why human:** UI rendering, toast/dialog interaction, and status badge colour cannot be verified with grep.

### 2. Admin Login Flow

**Test:** Open `/login` in admin-web, enter seeded admin credentials, submit.
**Expected:** Token stored in `admin_token` cookie; redirect to `/users`; sidebar nav visible with all sections (Users, Ledger, Sender IDs, Audit, Bundles, Refunds).
**Why human:** Middleware redirect, cookie setting, and sidebar visibility require a live browser session.

---

_Verified: 2026-06-22_
_Verifier: Claude (gsd-verifier)_
