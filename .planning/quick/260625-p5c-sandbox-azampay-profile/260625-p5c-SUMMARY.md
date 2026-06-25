---
quick_id: 260625-p5c
description: Add a sandbox Spring profile to payment-service pairing real Azampay gateway with stub signature validator
date: 2026-06-25
status: complete
result: pass
---

# Quick Task 260625-p5c — Summary

## Outcome: ✅ PASS

A `sandbox` Spring profile now lets payment-service run the **real** Azampay gateway
against the sandbox base-url without production merchant onboarding or the HMAC
validator. Verified booting clean under the profile.

## Changes

| File | Change |
|------|--------|
| `AzampayPaymentGateway.java` | `@Profile("prod")` → `@Profile({"prod","sandbox"})` + javadoc |
| `AzampayTokenProvider.java` | `@Profile("prod")` → `@Profile({"prod","sandbox"})` + javadoc |
| `StubSignatureValidator.java` | `@Profile("stub")` → `@Profile({"stub","sandbox"})` + javadoc + log msg; documents the defense-in-depth that keeps the permissive validator safe |
| `application.yml` | Clarified the `app.azampay` block: real gateway runs under `prod` **and** `sandbox`; `SPRING_PROFILES_ACTIVE=sandbox` + real creds exercises live sandbox |
| `.env.example` | Documented how to enable the sandbox flow (profile + `AZAMPAY_*` creds) |

`StubPaymentGateway` deliberately left `@Profile("stub")` only — under `sandbox` the real
Azampay gateway is the sole `PaymentGateway` bean.

## Bean wiring (profile = `sandbox`)

- `AzampayPaymentGateway` (real) — active ✅
- `AzampayTokenProvider` (real) — active ✅
- `StubSignatureValidator` (always-true) — active ✅ (satisfies `CallbackController`)
- `StubPaymentGateway` — NOT active ✅
- No duplicate `PaymentGateway` / `WebhookSignatureValidator` beans.
- `prod` profile still has **no** `WebhookSignatureValidator` bean → remains intentionally
  blocked until a real `HmacSignatureValidator(@Profile("prod"))` is written.

## Why the permissive validator is safe in sandbox

`CallbackController` rejects any `utilityRef` not present in the `payments` table, and wallet
crediting is idempotent (`INSERT … ON CONFLICT DO NOTHING`). A forged or duplicated sandbox
callback therefore cannot mis-credit even though signature validation is a no-op.

## Verification

- `./gradlew :services:payment-service:compileJava` — ✅ compiles.
- Boot under sandbox profile (Compose infra up, `.env` sourced for DB creds):
  - Log: `The following 1 profile is active: "sandbox"`
  - `Started PaymentServiceApplication in 4.6s`
  - `curl :8084/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`
  - No missing/duplicate-bean error — wiring correct.
- Stack torn down clean.

## How to walk the real sandbox flow now

1. Put real Azampay **sandbox** creds in `.env` (`AZAMPAY_CLIENT_ID`, `AZAMPAY_CLIENT_SECRET`,
   `AZAMPAY_APP_NAME`); `AZAMPAY_BASE_URL` already defaults to the sandbox.
2. Run payment-service with `SPRING_PROFILES_ACTIVE=sandbox` (other services can stay on `dev`).
3. Initiate a purchase → real STK push to the test MSISDN → real callback hits
   `CallbackController` → wallet credited.

## Notes / follow-ups

- The runtime boot failing on my first attempt was an env mistake (didn't source `.env`,
  so no DB password) — not a code issue. Fixed by sourcing `.env`, as start.sh does.
- Re-confirmed the `start.sh`/`stop.sh` gradle-orphan behaviour: killing the `bootRun`
  launcher PID leaves the forked JVM holding the port (cleaned via lsof). Same follow-up
  already logged in quick task 260625-nzn.
- Production cutover still needs: merchant onboarding, the documented HMAC scheme, and a
  real `HmacSignatureValidator(@Profile("prod"))`. Phase 0 criterion #2 stays partial but
  is now *exercisable* end-to-end in sandbox.
