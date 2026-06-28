---
quick_id: 260629-1fb
description: Standardize identity customer-auth on /api/v1/auth and align all client endpoints
date: 2026-06-29
status: complete
result: pass
---

# Quick Task 260629-1fb — Summary

## Outcome: ✅ PASS

Identity customer-auth is now under `/api/v1/auth`, every client path matches the canonical
`/api/v1/*` contract, and the temporary gateway stop-gap is gone. Resolves the
`2026-06-28-standardize-identity-auth-api-v1-prefix` todo and the latent prod-routing bug.

## Full client↔server audit (the core of this task)

| Surface | Finding | Action |
|---------|---------|--------|
| Server controllers | Only 3 used bare `/auth` (Registration, Session, PasswordReset); all else `/api/v1/*` | Moved the 3 → `/api/v1/auth` |
| **SecurityConfig** | 5 permitAll matchers listed `/auth/*` | Moved → `/api/v1/auth/*` (else endpoints would 401) |
| **admin-web** | Every call already `/api/v1/*` | **No change needed** |
| Flutter customer-app | 4 sites called bare `/auth/*` | register/login/me/refresh → `/api/v1/auth/*` |
| Other Flutter calls (bundles, campaigns, contacts, payments, wallet) | Already `/api/v1/*` and map to real server routes | No change |

No client was calling any path the server doesn't expose, once auth was aligned.

## Changes (21 files, commit d5fc2ce)

- **identity main**: `RegistrationController`, `SessionController`, `PasswordResetController`
  (`@RequestMapping` → `/api/v1/auth`), `SecurityConfig` (5 permitAll matchers), + javadoc/DTO
  comment sweep.
- **identity tests**: 9 ITs updated to the new paths (Registration, Login, AuthMe, Logout,
  PasswordReset, Lockout, NidaDegraded, AdminUserSearch + AdminLogin left correct).
- **Flutter**: `auth_api.dart`, `auth_notifier.dart`, `auth_interceptor.dart`.
- **gateway**: removed the stop-gap `location /auth/` route (08b2f5f); single `/api/v1/auth/`
  route now covers customer + admin auth.

## Safe-replacement technique

`/api/v1/auth/admin` contains the substring `/auth/admin`, so replacements were anchored to
the literal delimiter (`"/auth/`, `'/auth/`, ` /auth/`, `>/auth/`) — none of which match the
quote/space-prefixed admin literal. Verified: zero double-prefixes, admin paths intact.

## Verification

- `./gradlew :services:identity-service:compileJava compileTestJava` — ✅
- **identity-service test suite — BUILD SUCCESSFUL in 28s, 0 failures** (all 9 auth ITs).
- `nginx -t` on the cleaned config — ✅.
- Live through the running gateway (`:8080`), after recreating gateway + restarting identity:
  - **Real registration** `POST /api/v1/auth/register` → **200** with `userId` +
    `PENDING_VERIFICATION` + JWT.
  - `POST /api/v1/auth/admin/login` → **200** (admin still works).
  - Old `POST /auth/register` → **404** (bare path correctly gone).
  - `400` (not 401) on an empty register body confirmed the permitAll matchers moved correctly.

## Notes

- The stack stayed live; identity-service was restarted on :8081 with the new build. Customer
  app on :8090 should now register successfully against `/api/v1/auth/*`.
- Production parity: Traefik's `/api/v1/*` routing now covers customer auth — the prod-routing
  gap is closed, not just the local gateway.
