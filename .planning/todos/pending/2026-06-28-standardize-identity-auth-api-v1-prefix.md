---
created: 2026-06-28T21:57:45.047Z
title: Standardize identity customer-auth on /api/v1/auth prefix
area: auth
files:
  - services/identity-service/src/main/java/com/smsreseller/identity/auth/RegistrationController.java:18
  - services/identity-service/src/main/java/com/smsreseller/identity/auth/SessionController.java:48
  - apps/customer-app/lib/features/auth/auth_api.dart:50
  - apps/customer-app/lib/features/auth/auth_api.dart:83
  - apps/customer-app/lib/core/auth/auth_notifier.dart:80
  - apps/customer-app/lib/core/dio/auth_interceptor.dart:77
  - scripts/gateway/nginx.conf
---

## Problem

The identity service's customer-auth controllers map to a bare `/auth` base path
(`RegistrationController` and `SessionController` both `@RequestMapping("/auth")` →
`/auth/register`, `/auth/login`, `/auth/me`, `/auth/refresh`), while **everything else
in the system is under `/api/v1/*`** — including identity's own admin endpoints
(`/api/v1/auth/admin`), wallet, payments, contacts, campaigns, etc.

The Flutter customer app matches the service (`auth_api.dart` / `auth_notifier.dart` /
`auth_interceptor.dart` call bare `/auth/*`), so app↔service is internally consistent —
but the **edge router only knows `/api/v1/*`**. This surfaced in local dev: the gateway
(`scripts/gateway/nginx.conf`) 404'd `/auth/register` until a stop-gap `location /auth/`
route was added (commit 08b2f5f).

**This is not just a local-dev issue.** Production uses Traefik routing by `/api/v1/*`
path (CLAUDE.md). With auth at `/auth/*`, the customer register/login/me/refresh
endpoints would be **unreachable in production**, since Traefik would have no matching
route (unless a special `/auth` rule is also added there). It's a latent prod bug masked
locally by the nginx stop-gap.

## Solution

Standardize identity customer-auth on the `/api/v1/auth` prefix to match the rest of the
API, then remove the special-case edge routing:

1. `RegistrationController` + `SessionController`: change `@RequestMapping("/auth")` →
   `@RequestMapping("/api/v1/auth")`. Watch for collision with the existing
   `AdminLoginController` (`/api/v1/auth/admin`) — distinct sub-paths, should be fine.
2. Update the 4 Flutter call sites to `/api/v1/auth/*` (register, login, me, refresh).
3. Update any identity-service tests that assert the `/auth/*` paths.
4. Remove the stop-gap `location /auth/` route from `scripts/gateway/nginx.conf`
   (the existing `/api/v1/auth/` route then covers everything).
5. Confirm production Traefik/ingress manifests route `/api/v1/auth/*` (they should
   already, via the `/api/v1` rule) — no `/auth` special case needed.

Verify end-to-end: customer register/login works through the gateway on :8080 and the
admin login (`/api/v1/auth/admin/login`) still works.

**Note:** the temporary gateway `/auth/` route (08b2f5f) is the thing to delete once this
is done — leaving both is harmless locally but the goal is a single consistent prefix.
