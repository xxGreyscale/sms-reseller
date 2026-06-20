# Phase 4: Contacts & Messaging - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Users manage contact lists (CRUD, named groups, CSV import with E.164 normalization +
dedup + suppression) and send verified bulk SMS campaigns with credit reserved before
dispatch, quorum-queue + DLX retry with exponential backoff, per-message delivery tracking,
and credit refund for permanently undeliverable messages. Plus custom alphanumeric sender-ID
requests with an approve/reject state machine and outcome notification.

Two new schemas/services: `contact-service` (contacts, groups, suppression, CSV import) and
`messaging-service` (campaigns, messages, sender IDs, SmsProvider, delivery tracking).

**Requirements:** CONT-01..09, MESG-01..10, SNDR-02/03/04.

**Not in this phase:** The admin approval *panel* UI (SNDR-03 screen) and notification
*delivery* fan-out — both Phase 5 (Next.js admin + notification module). The Flutter customer
UI — Phase 6. SNDR-01 (default numeric sender ID) already shipped in Phase 2.
</domain>

<decisions>
## Implementation Decisions

### Credit lifecycle (the core correctness contract)
- **D-01:** Credit flow per message: **RESERVE at campaign QUEUED → CONSUME when the SmsProvider
  accepts the message for delivery** (that is when the upstream cost is incurred). A message that
  is never accepted (suppressed, invalid, campaign cancelled, abandoned) → **RELEASE** the
  reservation. A message that is accepted-then-permanently-fails delivery → **REFUND** a
  credit-back lot (MESG-10). Net invariant: a user is charged only for messages actually handed
  to the carrier; no credits silently lost, no free sends.
- **D-02:** The ledger txn types from Phase 3 (`RESERVE | CONSUME | RELEASE | EXPIRE | REFUND`)
  cover this exactly — Phase 4 drives them, it does not redefine them.

### Cross-service credit integration (mechanism)
- **D-03:** **Reserve is a synchronous REST call** from messaging-service to wallet-service's
  reserve API on the user's "send campaign" request path: `reserve(userId, count, campaignId)`;
  campaign transitions to QUEUED only on success, else a clear "insufficient credits" error
  (MESG-03). This is the request path, NOT an AMQP consumer, so CLAUDE.md's "no sync HTTP inside
  AMQP consumers" rule is not violated. Wrap in Resilience4j (circuit breaker + timeout);
  `campaignId` is the idempotency key.
- **D-04:** **Consume / Release / Refund are driven by AMQP**, NOT sync HTTP — they happen inside
  the async send pipeline. messaging-service emits per-message events (e.g. `MessageAccepted`,
  `MessageReleased`, `MessageRefundDue`) on a topic exchange; wallet-service consumes them and
  applies CONSUME/RELEASE/REFUND **idempotently** (processed_events + ON CONFLICT DO NOTHING,
  the Phase 3 pattern). Keeps the wallet the single owner of the ledger; no ledger mirror in
  messaging. (Researcher/planner: confirm event names + the reservation→lot correlation so
  CONSUME/RELEASE target the right reserved lots.)

### Send pipeline & DLX retry
- **D-05:** **One queue message per recipient** (not batched) — enables per-message delivery
  status (MESG-07), isolated retry, and per-message refund.
- **D-06:** **Quorum queue + DLX**, `default-requeue-rejected: false` (CLAUDE.md) so poison
  messages route to the DLX instead of infinite-looping. **Up to 3 retries with exponential
  backoff (~1m / 5m / 15m).**
- **D-07:** **"Permanently undeliverable" = max retries exhausted OR the SmsProvider returns a
  hard-fail code** (invalid number, blocked, rejected). On permanent failure → message FAILED +
  refund-due event (D-01/D-04).

### Contacts
- **D-08:** **Dedup and suppression are scoped per-user globally** — a phone number is a single
  contact across the whole account (dedup on E.164 within the user), and a suppressed number is
  excluded from ALL future campaigns regardless of group (CONT-06/08, MESG-09).
- **D-09:** CSV import normalizes Tanzanian numbers to E.164 (07xx / 06xx → +255XXXXXXXXX,
  CONT-07), dedups, and returns an **import summary** (imported / duplicates skipped / invalid,
  CONT-09). Invalid rows are reported, not imported.

### Scheduling
- **D-10:** Scheduled campaigns (MESG-04) are dispatched by a **DB-polled `@Scheduled` job**
  (consistent with the scheduled jobs already in identity/wallet/payment), not a delayed queue.
  Cancellation (MESG-05) flips campaign state before the poller picks it up; the poller only
  dispatches campaigns still in SCHEDULED state at/after their dispatch time.

### Sender-ID requests (Phase 4 / Phase 5 boundary)
- **D-11:** Phase 4 builds the **backend**: user submits a custom alphanumeric sender-ID request,
  the `REQUESTED → APPROVED | REJECTED` state machine, an **internal approve/reject API**, and an
  **outbox notification event** on outcome (SNDR-04). The admin approval **panel UI** (SNDR-03)
  is Phase 5 (Next.js admin app). TCRA approval is an out-of-band manual step reflected by the
  admin decision — not automated here.

### Mock-first (locked by roadmap)
- **D-12:** `SmsProvider` interface with `StubSmsProvider` (`@Profile("stub")`, default — records
  sends in-memory, simulates delivery receipts after a configurable delay, supports
  success/hard-fail/transient-fail outcomes) and `RealSmsProvider` (`@Profile("prod")`, wired when
  upstream provider is contracted). Full campaign send + delivery tracking demoable before any SMS
  provider is signed. Mirrors the Phase 2 NIDA / Phase 3 Azampay stub pattern.

### Resolved from research open questions (2026-06-21)
- **D-13:** `ReservationResult` from Phase 3 is `{List<UUID> lotIds, int reservedCount}` — `lotIds`
  is **per distinct lot**, NOT one UUID per credit. An N-credit reservation can span multiple lots.
  So the planner must **allocate reserved credits to messages** and reconcile CONSUME/RELEASE/REFUND
  against lots in aggregate — naive "1 message = 1 lotId" is wrong. Each `outbound_messages` row
  carries the `lotId` it draws from; wallet applies per-lot deltas idempotently. The planner owns
  the allocation algorithm (e.g. fill messages from lots in the reserved order).
- **D-14:** Recipient expansion uses a **REST call to contact-service** at campaign dispatch, with
  recipients **cached into `outbound_messages` rows at creation** (snapshot — no later cross-service
  read, honoring CLAUDE.md "denormalize at write time"). Suppression (D-08) is applied during this
  expansion so suppressed numbers never become messages (MESG-09).
- **D-15:** wallet-service does **not** yet expose consume/release/refund-by-lot — Phase 4 adds the
  wallet-side AMQP consumers + the `LotService` consume/release operations that apply the per-lot
  deltas for `MessageAccepted` (RESERVE→CONSUME), `MessageReleased` (RESERVE→release), and
  `MessageRefundDue` (CONSUME→REFUND credit-back), all idempotent via `processed_events`.

### Claude's Discretion
- Exact event names + payloads for the consume/release/refund AMQP contract, delivery-receipt
  ingestion shape (stub-simulated now; real DLR webhook later mirroring the Azampay callback),
  campaign/message state-machine enums, character-counter/encoding logic (MESG-02), and the
  reservation→lot correlation needed so CONSUME/RELEASE hit the right reserved credits.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Upstream contracts (Phase 3 — consumed by this phase)
- `.planning/phases/03-wallet-payments/03-02-SUMMARY.md` — `ReservationService.reserve(userId,
  count, referenceId) → ReservationResult{lotIds, reservedCount}`; `LotService.creditBack(userId,
  credits, referenceId)`; ledger txn types `RESERVE/CONSUME/RELEASE/EXPIRE/REFUND`; pessimistic
  reservation semantics. **D-01/D-03/D-04 build directly on this.**
- `.planning/phases/03-wallet-payments/03-06-SUMMARY.md` — idempotent refund credit-back +
  `processed_events` idempotency pattern + outbox/relay to mirror for messaging events.
- `.planning/phases/03-wallet-payments/03-04-SUMMARY.md` — `ProcessedEventRepository.tryInsert`
  (INSERT ON CONFLICT DO NOTHING) idempotent AMQP consumer pattern for wallet's consume/refund.

### Roadmap & requirements
- `.planning/ROADMAP.md` §"Phase 4: Contacts & Messaging" — goal, 22 requirements, 6 success
  criteria, mock-first SmsProvider contract.
- `.planning/REQUIREMENTS.md` — CONT-01..09, MESG-01..10, SNDR-02/03/04 acceptance criteria.

### Patterns & constraints
- `CLAUDE.md` §"Patterns" + §"What NOT to Do" — RabbitMQ topic exchange, quorum queues, DLX +
  `default-requeue-rejected:false`, NO sync HTTP inside AMQP consumers (fire-and-forget events;
  denormalize at write time), Resilience4j for upstream SMS provider, one schema/migration set per
  service, jakarta.*, Flyway V{N}__ + flyway-database-postgresql, opencsv/commons-csv for CSV.
- `.planning/PROJECT.md` §schemas — `contact` and `messaging` are two of the 8 schemas; mobile-app
  contact groups + scheduling are deferred to post-launch (backend still builds them).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Transactional outbox + scheduled relay**: identity/wallet/payment `OutboxEntry` +
  `OutboxRepository` + `OutboxRelay` — direct template for messaging's per-message and
  sender-ID notification events.
- **Idempotent AMQP consumer**: wallet `processed_events` + `ProcessedEventRepository.tryInsert`
  + `@RabbitListener @QueueBinding` — template for wallet consuming messaging's consume/release/
  refund events (D-04) and for any messaging-side consumers.
- **Mock-first interface+@Profile triple**: NidaVerificationService / Azampay PaymentGateway —
  exact shape for `SmsProvider` (D-12), incl. configurable outcomes + Resilience4j on prod impl.
- **Scheduled job pattern**: `VerificationRetryJob` / payment `ReconciliationJob` /
  `PaymentTimeoutJob` (`@Scheduled(fixedDelayString)`, bounded page, per-item try/catch) —
  template for the scheduled-campaign dispatcher (D-10) and any sweep jobs.
- **Resource-server SecurityConfig + IDOR guard**: payment/wallet `SecurityConfig` (STATELESS,
  CSRF off, validates Phase 2 JWT via libs/shared-security; userId from `auth.getSubject()`).
- **Testcontainers base**: `AbstractWalletIntegrationTest` / `AbstractPaymentIntegrationTest`
  (PG16 @ServiceConnection + Redis + RabbitMQ) — mirror for contact/messaging service ITs +
  placeholder-IT-per-requirement convention (Wave 0).

### Established Patterns
- One logical DB + one Flyway migration set per service; AMQP topic exchanges per service
  (`identity.events`, `payment.events`, `wallet.events` → add `messaging.events`); TDD RED→GREEN
  (tdd_mode on); raw TZS BIGINT for money; SMS credits are integer counts.

### Integration Points
- **Outbound sync (REST):** messaging → wallet `reserve` on the send request path (D-03).
- **Outbound async (AMQP):** messaging → wallet consume/release/refund events (D-04); messaging →
  notification events for delivery summaries + sender-ID outcomes (Phase 5 consumes).
- **Inbound (webhook, mock now):** delivery receipts from the SMS provider (stub-simulated;
  real DLR webhook later, mirroring Azampay callback).
- **Cross-service read:** messaging needs the user's sender IDs (default numeric from Phase 2 +
  approved alphanumeric from this phase) and contact recipients from contact-service.

</code_context>

<specifics>
## Specific Ideas

- Likely build order: contact-service (CRUD + groups + CSV import/dedup/normalize/suppression) and
  messaging foundation (campaign/message entities + SmsProvider stub) early; then the reserve→
  dispatch→DLX-retry→delivery-tracking pipeline; then consume/release/refund AMQP integration with
  wallet; then sender-ID request/approval; sweep/scheduler last.
- The reserve→QUEUED→send→accept(CONSUME)→deliver/fail(REFUND/RELEASE) loop is the spine — get the
  per-message state machine + the wallet event contract right first.
</specifics>

<deferred>
## Deferred Ideas

- **Admin approval panel UI** for sender-ID requests (SNDR-03 screen) — Phase 5.
- **Notification delivery** (SMS/email/in-app fan-out of delivery summaries + sender-ID outcomes) —
  Phase 5; Phase 4 only emits the events.
- **Real SMS provider** + real delivery-receipt webhook — when upstream is contracted (mock-first).
- **Mobile-app contact groups + campaign scheduling** — deferred to post-launch per PROJECT.md;
  backend still builds them this phase.
- **Message templates** — listed under the messaging schema in PROJECT.md but not in Phase 4
  requirements; treat as future unless a requirement surfaces.

</deferred>

---

*Phase: 4-Contacts & Messaging*
*Context gathered: 2026-06-21*
