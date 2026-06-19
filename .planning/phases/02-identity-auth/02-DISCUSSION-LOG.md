# Phase 2: Identity & Auth - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-19
**Phase:** 2-Identity & Auth
**Areas discussed:** Phase scope (Identity vs Catalog), Unverified user UX, NIDA stub behavior, Session model, Login & password policy

---

## Phase scope — Identity vs Catalog mismatch

The phase was named "Identity & Catalog" with a goal mentioning the bundle catalog, but
none of its 9 requirements (IDEN-01…08, SNDR-01) covered catalog; catalog-view (PYMT-01)
was already assigned to Phase 3.

| Option | Description | Selected |
|--------|-------------|----------|
| Identity only | Focus Phase 2 on the 9 IDEN/SNDR requirements; rename to Identity & Auth; catalog → Phase 3 | ✓ |
| Add catalog read here | Build catalog module now; pull PYMT-01 forward into Phase 2 | |
| Keep as-is, decide later | Leave roadmap untouched, resolve at planning | |

**User's choice:** Identity only.
**Notes:** ROADMAP updated and committed (`9ae20d4`) — phase renamed "Identity & Auth",
goal reworded, scope note added pointing catalog to Phase 3. PYMT-01 already lived in
Phase 3, so no requirement reassignment was needed.

---

## Unverified user UX

| Option | Description | Selected |
|--------|-------------|----------|
| Logged in, but walled | Session issued on register; app shell visible; features gated until VERIFIED | ✓ |
| Blocked until verified | No usable session until VERIFIED; "we're verifying" wall | |
| Logged in, full read-only | Session issued; browse everything read-only; writes gated | |

**User's choice:** Logged in, but walled.
**Notes:** Implies the JWT must carry a verification-status claim (CONTEXT D-02) so
downstream modules enforce the wall locally.

---

## NIDA stub behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Configurable outcomes | Stub supports success/rejection/timeout/unavailable via config or magic NIN | ✓ |
| Always succeed | Stub only auto-verifies; IDEN-08 unit-tested by mocking | |
| Success + one failure toggle | Auto-verify default + single global flag to force unavailable | |

**User's choice:** Configurable outcomes.
**Notes:** Makes the IDEN-08 degraded path (PENDING → background retry → VERIFIED) fully
demoable before real NIDA access.

---

## Session / multi-device model

| Option | Description | Selected |
|--------|-------------|----------|
| Multi-device, revoke current | Multiple devices; logout revokes only that device; refresh rotation | ✓ |
| Multi-device, revoke all | Multiple devices; logout/reset revokes all sessions | |
| Single session | One active session; new login kills old | |

**User's choice:** Multi-device, revoke current.
**Notes:** Refresh tokens rotate on each use. Default applied (not explicitly asked):
password reset revokes ALL sessions — flagged in CONTEXT D-09 for reconsideration.

---

## Login credentials & password policy

| Option | Description | Selected |
|--------|-------------|----------|
| Email + password | Email login; reset via email; min 8 chars; lockout after ~5 fails | ✓ |
| Phone or email + password | Login with either identifier; more code; reset-by-SMS option | |
| Email + password, no lockout | Email login, no lockout at MVP (rely on Traefik rate-limit) | |

**User's choice:** Email + password.
**Notes:** Registration still captures both phone + email; email is the login identifier.

---

## Claude's Discretion

- Exact lockout thresholds, cooldown duration, password-strength specifics.
- Magic-NIN suffix convention and stub config surface.
- Refresh-token storage (Redis vs Postgres) — CLAUDE.md leans Redis.
- Email provider selection for password-reset emails (mock-first interface expected).

## Deferred Ideas

- Bundle catalog (definitions + read API) → Phase 3 (PYMT-01).
- Custom alphanumeric sender IDs + admin approval (SNDR-02/03/04) → Phase 4.
- Reset-by-SMS alternative → needs SMS provider (mocked until Phase 4); email-only at MVP.
- Reconsider password-reset-revokes-all-sessions (D-09) if too aggressive.
