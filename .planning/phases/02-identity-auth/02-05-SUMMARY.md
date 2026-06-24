---
phase: 02-identity-auth
plan: "05"
subsystem: identity-service
tags: [password-reset, email, mock-first, redis, single-use-token, revoke-all, wave-4]
dependency_graph:
  requires: [02-02, 02-04]
  provides: [forgot-password-endpoint, reset-password-endpoint, email-sender-interface]
  affects: []
tech_stack:
  added:
    - spring-boot-starter-mail (BOM-managed, catalog entry from 02-01; added to identity-service deps here)
  patterns:
    - Mock-first EmailSender interface + @Profile split (stub records link, prod sends via JavaMailSender) — D-13
    - Redis SETEX reset:{token}->userId with short TTL (30 min default) — D-11
    - Delete-before-apply token consumption: DEL key before password update prevents concurrent reuse — T-02-07
    - No email enumeration: /auth/forgot always returns 200 regardless of email existence — T-02-ENUM
    - revokeAll(userId) called immediately after password update (D-09, T-02-SESS)
    - StubEmailSender.getLastResetUrl() provides zero-SMTP test hook for PasswordResetIT
key_files:
  created:
    - services/identity-service/src/main/java/com/smsreseller/identity/password/EmailSender.java
    - services/identity-service/src/main/java/com/smsreseller/identity/password/StubEmailSender.java
    - services/identity-service/src/main/java/com/smsreseller/identity/password/RealEmailSender.java
    - services/identity-service/src/main/java/com/smsreseller/identity/password/PasswordResetService.java
    - services/identity-service/src/main/java/com/smsreseller/identity/auth/PasswordResetController.java
    - services/identity-service/src/main/java/com/smsreseller/identity/web/dto/ForgotPasswordRequest.java
    - services/identity-service/src/main/java/com/smsreseller/identity/web/dto/ResetPasswordRequest.java
  modified:
    - services/identity-service/build.gradle.kts (added libs.spring.boot.starter.mail)
    - services/identity-service/src/test/java/com/smsreseller/identity/PasswordResetIT.java (placeholder → 6 real assertions)
decisions:
  - "StubEmailSender stores last reset URL in AtomicReference — thread-safe, injectable, no framework overhead"
  - "Token deleted BEFORE password update (delete-before-apply): prevents a race where two concurrent /auth/reset calls both pass the GET check. The second DELETE returns false (key already gone) → 400"
  - "reset:{token} key holds the userId string (UUID.toString) — minimal data, no PII in Redis"
  - "resetBaseUrl configurable via app.password-reset.base-url so the link works across environments without code change"
  - "192-bit token (24 bytes, Base64URL without padding → 32 chars) — sufficient entropy per RESEARCH security domain guidance"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-19"
  tasks_completed: 2
  tasks_total: 2
  files_created: 7
  files_modified: 2
---

# Phase 02 Plan 05: Forgotten-Password Reset Summary

**One-liner:** Single-use, TTL-bounded email-link password reset (IDEN-07) via mock-first EmailSender interface (stub records link for tests, prod sends via SMTP), with delete-before-apply token consumption and revokeAll on success.

## What Was Built

Wave 4 password reset for the identity-service:

### Task 1 — EmailSender interface + StubEmailSender + RealEmailSender

**EmailSender** interface: `sendPasswordResetLink(String toEmail, String resetUrl)` — the single abstraction point (D-13).

**StubEmailSender** (`@Profile("stub")`):
- Stores the last reset URL in an `AtomicReference<String>`
- Exposes `getLastResetUrl()` so integration tests can extract the token without SMTP
- Logs at DEBUG level only — acceptable in dev/stub profile

**RealEmailSender** (`@Profile("prod")`):
- Uses `JavaMailSender` (autoconfigured by `spring-boot-starter-mail`)
- Sends a plain-text email with the reset link
- The reset URL is intentionally NOT logged (T-02-LOG / V7)

`spring-boot-starter-mail` added to `identity-service/build.gradle.kts` (catalog entry existed from 02-01).

### Task 2 — PasswordResetService + PasswordResetController + DTOs (TDD RED→GREEN)

**PasswordResetService**:
- `forgot(email)`: lookup user by email; if found, generate 192-bit SecureRandom Base64URL token, `SETEX reset:{token} → userId` with 30-min TTL, call `EmailSender.sendPasswordResetLink`. If user not found: silently return (no enumeration — T-02-ENUM).
- `reset(token, newPassword)`: GET `reset:{token}` → userId; if null: throw 400 (invalid/expired/already-used). DEL key BEFORE applying password (delete-before-apply — T-02-07 concurrent reuse prevention). BCrypt-encode + persist new password. Call `RefreshTokenService.revokeAll(userId)` (D-09).

**PasswordResetController**:
- `POST /auth/forgot` (`permitAll`): delegates to `forgot()`, always returns 200
- `POST /auth/reset` (`permitAll`): delegates to `reset()`, returns 200 on success / 400 on bad token

**DTOs**:
- `ForgotPasswordRequest`: `@Email @NotBlank email`
- `ResetPasswordRequest`: `@NotBlank token`, `@NotBlank @Size(min=8) newPassword` (D-12)

## TDD Gate Compliance

- RED commit `8e6f9fa`: `PasswordResetIT` rewritten — 6 real assertions, all failing because `PasswordResetService`/`PasswordResetController` did not exist
- GREEN commit `62e1335`: All 6 tests pass; full suite BUILD SUCCESSFUL (46 tests, 0 failed, 3 skipped)

## Deviations from Plan

None — plan executed exactly as written.

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-02-07 (brute force / token reuse) | Mitigated — 192-bit SecureRandom token, 30-min TTL, deleted on first use (delete-before-apply) |
| T-02-ENUM (email enumeration) | Mitigated — /auth/forgot returns 200 for both known and unknown emails (PasswordResetIT.forgotPassword_doesNotRevealEmailExistence passes) |
| T-02-SESS (stale sessions after reset) | Mitigated — revokeAll(userId) called immediately after password update (D-09); PasswordResetIT.successfulReset_revokesAllRefreshTokens passes |
| T-02-LOG (reset link in logs) | Mitigated — StubEmailSender logs at DEBUG under "stub" profile only; RealEmailSender never logs the URL |
| T-02-SC (spring-boot-starter-mail dep) | Accepted — BOM-managed; no additional risk beyond RESEARCH audit |

## Known Stubs

None — all code in this plan is fully wired and tested.

## Self-Check: PASSED

- [x] EmailSender.java is an interface containing `sendPasswordResetLink`
- [x] StubEmailSender annotated `@Profile("stub")`, exposes `getLastResetUrl()`
- [x] RealEmailSender annotated `@Profile("prod")`, does NOT log resetUrl
- [x] PasswordResetService.java exists, min_lines ≥ 30 (confirmed ~110 lines)
- [x] PasswordResetService calls `refreshTokenService.revokeAll(userId)` (grep confirms)
- [x] Redis key prefix is `reset:` (grep confirms `KEY_PREFIX = "reset:"`)
- [x] Token generated via `SecureRandom` (grep confirms)
- [x] `@Size(min = 8)` on `newPassword` in `ResetPasswordRequest` (grep confirms)
- [x] build.gradle.kts contains `libs.spring.boot.starter.mail`
- [x] `./gradlew :services:identity-service:test --no-daemon` — BUILD SUCCESSFUL, 6 PasswordResetIT assertions pass
- [x] Commits: c873ff5 (Task 1), 8e6f9fa (RED), 62e1335 (GREEN)
