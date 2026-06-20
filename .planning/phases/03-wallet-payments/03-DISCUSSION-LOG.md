# Phase 3: Wallet & Payments - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-20
**Phase:** 3-wallet-payments
**Areas discussed:** Credit consumption order, Late payment / reconciliation, Alerts & expiry warnings, Catalog & refunds

---

## Credit consumption order

| Option | Description | Selected |
|--------|-------------|----------|
| Expiry-soonest-first | Spend soonest-expiring lot first so bonus credits get used before lapsing; implies lot-based ledger | ✓ |
| Bonus-first always | Spend all bonus before purchased regardless of dates | |
| Purchased-first | Spend paid credits first; bonus often expires unused | |

**User's choice:** Expiry-soonest-first
**Notes:** Drives lot-based ledger design (each grant carries its own expires_at). → D-01.

---

## Late payment / reconciliation

| Option | Description | Selected |
|--------|-------------|----------|
| Credit anyway, idempotently | Reconciliation job honors late-confirmed payment, credits once on Azampay txn ref, flips to SUCCESS; one pending payment in flight max | ✓ |
| Auto-refund the late payment | Treat EXPIRED as final, refund late money | |
| Hold for manual review | Flag to admin queue | |

**User's choice:** Credit anyway, idempotently
**Notes:** Late-success is a normal path. Single-in-flight-payment rule folded in. → D-04, D-05, D-06.

---

## Alerts & expiry warnings

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed default, in-app + outbox event | System-wide default threshold; in-app now + emit event for Phase 5 fan-out | ✓ |
| User-configurable threshold now | Per-user threshold + settings UI this phase | |
| In-app only, no events | Dashboard compute only, defer all plumbing | |

**User's choice:** Fixed default, in-app + outbox event
**Notes:** Starting default 20 credits, tunable via config. → D-08.

---

## Catalog & refunds

| Option | Description | Selected |
|--------|-------------|----------|
| Credit back to wallet ledger (refunds) | Append-only ledger credit-back, idempotent, no money movement | ✓ |
| Money-back via Azampay | Real gateway cash reversal | |
| Admin-triggered only | Manual admin refunds | |

**User's choice:** Credit back to wallet ledger (refunds). Catalog defaulted to DB table seeded via Flyway migration (Claude's recommendation, not corrected by user).
**Notes:** Phase 3 builds refund mechanism; Phase 4 is the caller. → D-07, D-09.

## Claude's Discretion

- Exact ledger/reservation/lot table shapes, reconciliation poll interval, outbox reuse strategy, precise low-credit default value — left to research/planning within captured decisions.

## Deferred Ideas

- Money-back refunds via Azampay (post-launch)
- Per-user configurable low-credit threshold + settings UI
- Admin bundle/refund management & ledger inspection (Phase 5)
- Notification delivery / SMS-email fan-out (Phase 5)
- Credit sharing between users (out of scope per PROJECT.md)
