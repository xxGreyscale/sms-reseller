# Phase 6: Flutter Mobile App - Discussion Log

> **Audit trail only.** Decisions are captured in CONTEXT.md — this log preserves alternatives considered.

**Date:** 2026-06-22
**Phase:** 6-flutter-mobile-app
**Areas discussed:** Push / FCM scope, Store publishing scope, Localization, Offline / connectivity UX

---

## Push / FCM scope

| Option | Description | Selected |
|--------|-------------|----------|
| Feed polling now; FCM deferred | Consume GET /api/v1/notifications + poll status/balance; FCM fast-follow | ✓ |
| Build real FCM push now | Firebase + APNs + device tokens + RealPushChannel | |

**User's choice:** Feed polling now; FCM deferred. → D-01.

---

## Store publishing scope

| Option | Description | Selected |
|--------|-------------|----------|
| Submit-ready builds + metadata + submission; live gated externally | Signed AAB/IPA + metadata + submit; approval external | ✓ |
| Treat actual live/approved as in-scope | Blocks phase on Apple/Google review | |
| Build + metadata only, no submission | Leaves MOBL-09 unmet | |

**User's choice:** Submit-ready + submission; live gated externally. → D-02, D-03.

---

## Localization

| Option | Description | Selected |
|--------|-------------|----------|
| Bilingual English + Swahili day one | Flutter l10n ARB, locale-aware + toggle | ✓ |
| English-only at MVP, Swahili fast-follow | l10n structured, English strings only | |
| Swahili-first, English secondary | Swahili default | |

**User's choice:** Bilingual English + Swahili from day one. → D-04.

---

## Offline / connectivity UX

| Option | Description | Selected |
|--------|-------------|----------|
| Cache-read + clear retry/error; online writes; go_router | Hive read cache; online-only writes; declarative routing | ✓ |
| Full offline-write sync (queue + reconcile) | Heavy, risks unintended sends | |
| Online-only, no caching | Unusable on dropped connection | |

**User's choice:** Cache-read + online-write; go_router. → D-05, D-06.

## Claude's Discretion

- Riverpod provider structure, Dio interceptor (JWT attach + refresh-on-401), Hive schema, go_router tree, polling intervals, l10n key naming, theming from UI-SPEC.

## Deferred Ideas

- Real FCM push (fast-follow)
- Contact groups, CSV import, scheduling, full analytics (MOBL-V2)
- Offline-write sync/queue
