# Phase 3: Wallet & Payments - Context

**Gathered:** 2026-06-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can purchase SMS bundles via Azampay mobile money and their credit balance is
atomically updated through an append-only ledger, with zero possibility of double-crediting
or negative balance. Covers: bundle catalog (PYMT-01), Azampay STK push purchase flow with
2-minute countdown + EXPIRED state (PYMT-02/03/04/05/07), idempotent callbacks + transactional
outbox + reconciliation (PYMT-06), refunds (PYMT-08), balance + append-only transaction history
(WLET-01/02), pessimistic-lock credit reservation preventing negative balance (WLET-03),
low-credit alerts (WLET-04), expiry warnings + expiry enforcement (WLET-05/06/07).

**Not in this phase:** Admin bundle/refund management UI and notification fan-out delivery
(Phase 5). Campaign credit reservation at send time and the refund *trigger* from failed
campaigns (Phase 4) — Phase 3 builds the refund *mechanism* (ledger credit-back), Phase 4
calls it. The Flutter customer UI (Phase 6).

</domain>

<decisions>
## Implementation Decisions

### Credit ledger & consumption
- **D-01:** Credit consumption is **expiry-soonest-first**. When a user holds both bonus
  credits (30-day expiry) and purchased credits (12-month expiry), sends/reservations draw
  from the soonest-expiring lot first, so bonus/promo credits are used before they lapse.
  This implies a **lot-based ledger**: each credit grant is a lot carrying its own
  `expires_at`; reservation and consumption walk lots in ascending expiry order.
- **D-02:** Ledger is **append-only** (locked by CLAUDE.md). Balance is derived, never a
  mutable column. Reservation uses pessimistic `SELECT FOR UPDATE` (WLET-03) to refuse any
  reservation that would take available balance below zero.
- **D-03:** Expiry windows (locked): purchased credits expire **12 months** from purchase
  (WLET-06); bonus credits (NIDA 50-credit grant, promotions) expire **30 days** (WLET-07).
  Expired lots must be excluded from available balance and swept (scheduled job).

### Payment flow & reconciliation
- **D-04:** If a payment times out (**EXPIRED** at the 2-minute mark) but the Azampay
  reconciliation/polling job later confirms it actually succeeded, **credit the wallet anyway,
  idempotently** — money left the customer's account, so honor it. The reconciliation job
  credits exactly once (idempotent on the Azampay transaction reference via the
  `processed_events`/`ON CONFLICT DO NOTHING` pattern), flips the payment to SUCCESS, and
  emits a confirmation outbox event. Late-success is a normal path, not an error.
- **D-05:** A user may have **only ONE pending payment in flight at a time** — a new purchase
  attempt while one is pending is rejected with a clear message (prevents double-charge and
  reconciliation ambiguity).
- **D-06:** Payment lifecycle states: PENDING → (SUCCESS | EXPIRED | FAILED), with EXPIRED
  reachable late→SUCCESS via reconciliation (D-04). No infinite spinner — the 2-min countdown
  always resolves to a terminal state in the UI contract (PYMT-03/07).

### Refunds
- **D-07:** Refunds (PYMT-08) are issued as a **credit back to the wallet ledger** — an
  append-only credit entry reversing the charge/reservation, idempotent, with no real money
  movement. Money-back via Azampay is deferred (see Deferred Ideas). Phase 3 builds the
  refund *mechanism* (a callable, idempotent ledger credit-back); Phase 4 (failed campaigns)
  is the caller.

### Alerts & expiry warnings
- **D-08:** Low-credit alerts (WLET-04) and 7-day expiry warnings (WLET-05) use a **fixed
  system-wide default threshold** at MVP (starting default: **20 credits**, tunable via config
  — no per-user configuration UI this phase). Surface them **in-app/on the dashboard now**, and
  **emit an outbox event** so Phase 5's notification module can fan out to SMS/email later
  without retrofitting event emission.

### Bundle catalog
- **D-09:** The bundle catalog (PYMT-01) is a **DB table seeded via a Flyway migration**, not
  hardcoded — so Phase 5's admin UI can edit bundles without a code change. Seed the locked
  MVP pricing (see canonical refs): Taster 50/FREE, Starter 200/3,200, Growth 1,000/14,500,
  Pro 5,000/65,000, Scale 20,000/240,000 TZS. The Taster (FREE) bundle is the NIDA-verify
  grant, not an Azampay purchase. All amounts stored as **BIGINT TZS cents** (CLAUDE.md).

### Mock-first (locked by roadmap)
- **D-10:** `PaymentGateway` interface with `StubPaymentGateway` (`@Profile("stub")`, default
  in dev/staging — configurable success/failure/timeout outcomes, mirroring the NIDA stub
  pattern from Phase 2 D-05) and `AzampayPaymentGateway` (`@Profile("prod")`, wired when the
  merchant account arrives). Full purchase flow incl. countdown + EXPIRED must be demoable
  before Azampay credentials exist.

### Claude's Discretion
- Exact ledger table shape (lots, reservations, transactions), reconciliation poll interval
  (CLAUDE.md suggests every ~2 min for payments older than 5 min), outbox table/relay reuse vs
  per-service copy, and the precise low-credit default value — all left to research/planning
  within the decisions above.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pricing & product
- `.planning/PROJECT.md` §"Pricing Model" — LOCKED bundle definitions and TZS prices; expiry
  windows (12mo purchased / 30d bonus). Seed source for the catalog (D-09).
- `.planning/ROADMAP.md` §"Phase 3: Wallet & Payments" — goal, 15 requirements, 5 success
  criteria, mock-first contract.
- `.planning/REQUIREMENTS.md` — WLET-01..07, PYMT-01..08 acceptance criteria.

### Patterns & constraints
- `CLAUDE.md` §"Azampay Integration" — RestClient + Resilience4j circuit breaker, webhook
  signature validation, polling recovery / reconciliation job, idempotency via Azampay txn ref
  + `INSERT ... ON CONFLICT DO NOTHING`, TZS BIGINT cents.
- `CLAUDE.md` §"What NOT to Do" + §"Patterns" — append-only ledger, no cross-service joins,
  transactional outbox, Flyway `V{N}__` per-service migrations + `flyway-database-postgresql`.

### Upstream contract from Phase 2
- `.planning/phases/02-identity-auth/02-06-SUMMARY.md` — the `UserVerified(50 credits)` event
  published by identity-service `OutboxRelay` to RabbitMQ topic exchange `identity.events`.
  Phase 3's wallet service is the **consumer**: on this event, grant a 50-credit **bonus lot**
  (30-day expiry, D-03) idempotently. This is the wallet's first inbound integration.
- `.planning/phases/02-identity-auth/02-CONTEXT.md` — D-03 (50 free credits on verify),
  mock-first stub conventions (D-04/D-05) reused by D-10 here.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Transactional outbox + scheduled relay**: identity-service `outbox/OutboxEntry`,
  `OutboxRepository`, `OutboxRelay` (Phase 2, 02-06) — direct template for the payment-service
  outbox publishing payment-confirmed / credit-topped-up events.
- **Mock-first interface+@Profile pattern**: `NidaVerificationService` / `StubNida...` /
  `RealNida...` and `EmailSender` / `StubEmailSender` (Phase 2) — exact shape to mirror for
  `PaymentGateway` (D-10).
- **Resilience4j circuit breaker usage**: `RealNidaVerificationService` `@CircuitBreaker` +
  `@Retryable` with explicit timeouts — template for `AzampayPaymentGateway`.
- **Testcontainers base**: `AbstractIntegrationTest` (PG16 `@ServiceConnection` + Redis 7) —
  reuse for wallet/payment service ITs; placeholder-IT-per-requirement convention from 02-01.
- **JWT trust**: `libs/shared-security` `NimbusJwtDecoder` contract — wallet/payment services
  validate Phase 2 JWTs (no callback to identity).

### Established Patterns
- One logical DB + one Flyway migration set per service; `jakarta.*` imports; virtual threads;
  Actuator probes on main port; RabbitMQ topic exchange for events; RED→GREEN TDD (tdd_mode on).

### Integration Points
- **Inbound (AMQP):** consume `UserVerified` from `identity.events` → grant 50 bonus credits.
- **Inbound (webhook):** public Azampay callback endpoint (signature-validated, idempotent).
- **Outbound (AMQP):** publish payment-confirmed / low-credit / expiry-warning events for
  Phase 5 notifications.
- **Outbound (query, Phase 4):** wallet exposes reservation API consumed by messaging at send.

</code_context>

<specifics>
## Specific Ideas

- Build order within the phase likely: ledger/wallet core (lots + reservation + balance) →
  catalog seed → payment flow (stub gateway) + countdown/EXPIRED state machine → idempotent
  callback + reconciliation job → refund mechanism → alerts/expiry sweep + outbox events.
- The Phase 2 `UserVerified` consumer is the natural first vertical slice — it proves the
  ledger grant path end-to-end against a real Phase 2 event.

</specifics>

<deferred>
## Deferred Ideas

- **Money-back refunds via Azampay** (real cash reversal through the gateway) — heavy for MVP;
  revisit post-launch. Phase 3 ships ledger credit-back only (D-07).
- **Per-user configurable low-credit threshold + settings UI** — deferred; MVP uses a fixed
  default (D-08).
- **Admin bundle/refund management & ledger inspection UI** — Phase 5.
- **Notification delivery (SMS/email fan-out)** — Phase 5; Phase 3 only emits the outbox events.
- **Credit sharing between users** — explicitly out of scope per PROJECT.md.

</deferred>

---

*Phase: 3-Wallet & Payments*
*Context gathered: 2026-06-20*
