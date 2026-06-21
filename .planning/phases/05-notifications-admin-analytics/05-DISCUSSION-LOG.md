# Phase 5: Notifications, Admin & Analytics - Discussion Log

> **Audit trail only.** Decisions are captured in CONTEXT.md — this log preserves alternatives considered.

**Date:** 2026-06-21
**Phase:** 5-notifications-admin-analytics
**Areas discussed:** Admin auth & identity, Push notification scope, Analytics delivery surface, Admin-web scope & audit log

---

## Admin auth & identity

| Option | Description | Selected |
|--------|-------------|----------|
| identity-service issues admin JWTs; admins seeded | Reuse Phase 2 JWT/RSA; admin login mints roles:[ADMIN]; seeded accounts | ✓ |
| Separate admin-service issues admin JWTs | Distinct issuer + key mgmt | |
| Same login, role flag on user | One login, ADMIN flag | |

**User's choice:** identity-service issues admin JWTs; admins seeded. → D-01, D-02.

---

## Push notification scope

| Option | Description | Selected |
|--------|-------------|----------|
| In-app log now; push behind mock interface, deferred | notification log + feed for all 6 events; StubPushChannel; real FCM Phase 6 | ✓ |
| Build real push (FCM) now | No client to receive until Phase 6 | |
| In-app only; no push interface yet | Retrofit channel in Phase 6 | |

**User's choice:** In-app log now; push behind mock interface, deferred. → D-03, D-04, D-05.

---

## Analytics delivery surface

| Option | Description | Selected |
|--------|-------------|----------|
| Backend read APIs now; per-owner queries, no cross-service joins | messaging owns delivery/operator; wallet owns spend; per-user endpoints; Flutter renders Phase 6 | ✓ |
| Dedicated analytics/query service | Centralized aggregation, heavier infra | |
| Build analytics UI in admin-web too | User-facing metrics built twice | |

**User's choice:** Backend read APIs now; per-owner queries. → D-06, D-07.

---

## Admin-web scope & audit log

| Option | Description | Selected |
|--------|-------------|----------|
| All 6 operator screens; audit = admin actions + consumed domain events | sender-ID queue, user search, ledger, refund, bundle mgmt, audit viewer; dual-source audit | ✓ |
| Admin actions only in audit log | 6 screens, audit = admin mutations only | |
| Trim to approval queue + refunds | Defers ADMN-02/03/06/07 | |

**User's choice:** All 6 operator screens; audit = admin actions + consumed domain events. → D-08, D-09, D-10.

## Claude's Discretion

- Notification log/feed schema, queue/binding names, audit entry schema, analytics DTOs, admin-web routing/Server-Action wiring, admin token TTL.

## Deferred Ideas

- Real push / FCM (Phase 6)
- Customer-facing analytics + notification UI (Phase 6)
- Dedicated analytics service (not MVP)
- Email/SMS notification channels (future)
