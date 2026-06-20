# Phase 4: Contacts & Messaging - Discussion Log

> **Audit trail only.** Do not use as input to planning/research/execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-21
**Phase:** 4-contacts-messaging
**Areas discussed:** Credit lifecycle timing, Send pipeline & DLX retry, Cross-service reservation, Contacts + sender-ID scope

---

## Credit lifecycle timing

| Option | Description | Selected |
|--------|-------------|----------|
| Consume on provider-accept; refund on later fail | RESERVE@QUEUED → CONSUME on accept; RELEASE if never accepted; REFUND if accepted-then-fails | ✓ |
| Consume only on delivery confirmation | Hold RESERVED until DLR success | |
| Consume immediately at QUEUED | Deduct up front, refund failures | |

**User's choice:** Consume on provider-accept; refund on later fail. → D-01, D-02.
**Notes:** Invariant — charge only for messages handed to carrier; no lost credits, no free sends.

---

## Send pipeline & DLX retry

| Option | Description | Selected |
|--------|-------------|----------|
| Per-message + 3 retries exp backoff | One queue msg per recipient; quorum+DLX; 3 retries ~1m/5m/15m; permanent = max attempts or hard-fail code | ✓ |
| Per-message + 5 retries | Longer retry window | |
| Batched sends | Coarse retry/tracking | |

**User's choice:** Per-message + 3 retries exp backoff. → D-05, D-06, D-07.

---

## Cross-service reservation

| Option | Description | Selected |
|--------|-------------|----------|
| Synchronous REST to wallet reserve API | Request-path sync reserve(userId,count,campaignId); QUEUED on success | ✓ |
| AMQP request/reply | Decoupled but adds correlation/timeout complexity | |
| Messaging tracks own reservation | Local mirror, risks drift | |

**User's choice:** Synchronous REST (request path, not AMQP consumer). → D-03.
**Notes:** Derived D-04 — consume/release/refund go via AMQP events (not sync HTTP in async pipeline), wallet consumes idempotently.

---

## Contacts + sender-ID scope

| Option | Description | Selected |
|--------|-------------|----------|
| Request + state machine + events here; admin UI Phase 5 | Backend SNDR-02/03/04 now; admin panel Phase 5 | ✓ |
| Everything except UI screen | Same, explicit no-frontend | |
| Defer all sender-ID to Phase 5 | Move SNDR-02/03/04 out | |

**User's choice:** Request + state machine + events here; admin UI in Phase 5. → D-11.
**Notes:** Dedup/suppression per-user-global (D-08) and DB-polled @Scheduled dispatch (D-10) defaulted by Claude, not corrected by user.

## Claude's Discretion

- Event names/payloads for consume/release/refund AMQP contract, delivery-receipt ingestion shape, campaign/message state enums, char-counter/encoding logic, reservation→lot correlation.

## Deferred Ideas

- Admin approval panel UI (Phase 5)
- Notification delivery fan-out (Phase 5)
- Real SMS provider + real DLR webhook (mock-first until contracted)
- Mobile-app contact groups + scheduling (post-launch; backend built now)
- Message templates (future unless a requirement surfaces)
