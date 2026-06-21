---
phase: 05-notifications-admin-analytics
plan: "03"
subsystem: identity-service
tags: [admin-auth, jwt, role-admin, user-search, flyway, tdd]
dependency_graph:
  requires: [05-01]
  provides: [ADMN-01, ADMN-02]
  affects: [admin-web (05-08 consumes admin login + user search), messaging-service (hasRole ADMIN guards satisfied)]
tech_stack:
  added: []
  patterns:
    - JwtIssuer.issueAdminToken mirrors issueAccessToken — same RSA encoder, ROLE_ADMIN, 60-min TTL
    - Flyway V5 placeholder seed — ${adminEmail}/${adminPasswordHash} from env/K8s Secret, ON CONFLICT DO NOTHING
    - SecurityConfig jwtAuthenticationConverter reads roles claim — enables hasRole("ADMIN") on /api/v1/admin/**
    - AdminLoginService explicit role check — USER role rejected at admin login endpoint (T-05-05)
key_files:
  created:
    - services/identity-service/src/main/java/com/opendesk/identity/user/UserRole.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/AdminLoginRequest.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/AdminLoginService.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/AdminLoginController.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/UserSummaryDto.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/AdminUserService.java
    - services/identity-service/src/main/java/com/opendesk/identity/admin/AdminUserSearchController.java
    - services/identity-service/src/main/resources/db/migration/V4__add_role_and_full_name_to_users.sql
    - services/identity-service/src/main/resources/db/migration/V5__seed_admin_user.sql
  modified:
    - services/identity-service/src/main/java/com/opendesk/identity/token/JwtIssuer.java
    - services/identity-service/src/main/java/com/opendesk/identity/config/SecurityConfig.java
    - services/identity-service/src/main/java/com/opendesk/identity/user/User.java
    - services/identity-service/src/main/java/com/opendesk/identity/user/UserRepository.java
    - services/identity-service/src/main/resources/application.yml
decisions:
  - "05-03: AdminLoginService loads user by email and explicitly checks role=ADMIN before BCrypt verify — prevents role escalation via /api/v1/auth/admin/login (T-05-05)"
  - "05-03: SecurityConfig jwtAuthenticationConverter reads roles claim as SimpleGrantedAuthority — hasRole('ADMIN') works with ROLE_ADMIN in token without extra prefix stripping"
  - "05-03: phone column made nullable for admin account — enforced NOT NULL at application registration layer, not DB constraint"
  - "05-03: V5 Flyway placeholder seed pattern (${adminEmail}/${adminPasswordHash}) from env vars — no BCrypt hash in VCS (T-05-07)"
  - "05-03: AdminUserSearchIT gets ROLE_USER token via /auth/login (alice user) to prove 403 rejection"
metrics:
  duration: "~25m"
  completed: "2026-06-22"
  tasks: 2
  files: 14
---

# Phase 05 Plan 03: Admin JWT Issuance + User Search Summary

**One-liner:** Admin login endpoint issues ROLE_ADMIN JWT via Flyway-seeded credentials; ADMIN-gated user search by email/phone underpins admin-web operator tooling.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| RED  | AdminLoginIT + AdminUserSearchIT | 955c4e2 | AdminLoginIT, AdminUserSearchIT, UserRole, User (role/fullName), V4 migration |
| GREEN T1 | JwtIssuer.issueAdminToken + admin login | 8eaea04 | JwtIssuer, AdminLoginController, AdminLoginService, AdminLoginRequest, V5 seed, SecurityConfig, application.yml |
| GREEN T2 | ADMIN-guarded user search | 8eaea04 | AdminUserSearchController, AdminUserService, UserSummaryDto, UserRepository |

## What Was Built

### Admin Login (ADMN-01)
- `POST /api/v1/auth/admin/login` — accepts `{email, password}`, validates BCrypt, asserts `role=ADMIN`, returns `{accessToken}` with `roles:["ROLE_ADMIN"]` and 60-minute TTL
- `JwtIssuer.issueAdminToken(UUID)` — same RSA encoder as `issueAccessToken`, no `verification_status` claim, TTL from `app.jwt.admin-token-ttl-minutes` (default 60)
- `SecurityConfig` updated: `permitAll` on admin login path; `jwtAuthenticationConverter` reads `roles` claim as `SimpleGrantedAuthority` — satisfies existing `hasRole("ADMIN")` guards in messaging-service and others without changes

### Seeded Admin Account (D-02)
- `V4__add_role_and_full_name_to_users.sql` — adds `role VARCHAR(20) NOT NULL DEFAULT 'USER'`, `full_name VARCHAR(255)`, and makes `phone` nullable
- `V5__seed_admin_user.sql` — Flyway placeholder seed with `${adminEmail}` and `${adminPasswordHash}` from env `ADMIN_EMAIL` / `ADMIN_PASSWORD_HASH`; `ON CONFLICT (email) DO NOTHING`; no BCrypt hash in VCS
- `application.yml` — `flyway.placeholders.adminEmail` and `flyway.placeholders.adminPasswordHash` read from env with safe defaults

### User Search for Admin (ADMN-02)
- `GET /api/v1/admin/users?q={term}&page=0&size=20` — ROLE_ADMIN gated by SecurityConfig `/api/v1/admin/**`
- `AdminUserService.search()` — delegates to `UserRepository.searchByEmailOrPhone()` (case-insensitive LIKE on email OR phone); maps to `UserSummaryDto`
- `UserSummaryDto` — exposes `id, fullName, email, phone, verificationStatus, createdAt`; no `passwordHash` (T-05-08 mitigated)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Functionality] User entity missing role/fullName fields**
- **Found during:** GREEN implementation
- **Issue:** Plan referenced `role=ADMIN` on User entity but no `role` or `fullName` field existed; `phone` was NOT NULL preventing admin seed
- **Fix:** Added `UserRole` enum, `role` and `fullName` fields to User entity, `phone` made nullable; V4 migration for real DB schema
- **Files modified:** User.java, V4 migration (new)
- **Commit:** 955c4e2 (in RED commit — needed for test compilation)

**2. [Rule 2 - Missing Functionality] AdminLoginIT missing @BeforeEach admin user seed**
- **Found during:** RED review — prior uncommitted test had no setup, relied on phantom @DynamicPropertySource comment
- **Fix:** Added `@BeforeEach setUp()` inserting admin user + regular user via `UserRepository` directly (Flyway disabled in tests)
- **Files modified:** AdminLoginIT.java
- **Commit:** 955c4e2

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED  | 955c4e2 `test(05-03): RED — AdminLoginIT + AdminUserSearchIT` | PASS |
| GREEN | 8eaea04 `feat(05-03): GREEN — admin login + user search` | PASS |
| REFACTOR | N/A — no duplication identified | EXEMPT |

## Threat Surface Scan

| Flag | File | Description |
|------|------|-------------|
| threat_flag: admin-login-endpoint | AdminLoginController.java | New unauthenticated surface: POST /api/v1/auth/admin/login — mitigated by BCrypt verify + role assertion (T-05-05, T-05-06 accepted) |
| threat_flag: admin-data-surface | AdminUserSearchController.java | Admin can query all users by email/phone — ROLE_ADMIN gated by SecurityConfig; UserSummaryDto excludes passwordHash (T-05-08) |

## Known Stubs

None — all admin login and user search paths are wired end-to-end.

## Self-Check: PASSED

Files created/exist:
- `services/identity-service/src/main/java/com/opendesk/identity/admin/AdminLoginController.java` — FOUND
- `services/identity-service/src/main/java/com/opendesk/identity/admin/AdminLoginService.java` — FOUND
- `services/identity-service/src/main/java/com/opendesk/identity/admin/AdminUserSearchController.java` — FOUND
- `services/identity-service/src/main/resources/db/migration/V5__seed_admin_user.sql` — FOUND
- `services/identity-service/src/main/resources/db/migration/V4__add_role_and_full_name_to_users.sql` — FOUND

Commits:
- 955c4e2 — test(05-03): RED
- 8eaea04 — feat(05-03): GREEN

All identity-service tests: BUILD SUCCESSFUL
