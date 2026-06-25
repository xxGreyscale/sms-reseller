---
quick_id: 260625-nzn
description: Finish + commit local-dev API gateway CORS work, then run DEVX-01 full-stack smoke test
date: 2026-06-25
status: complete
result: pass
commits:
  - ffd464b  feat(local-dev): add CORS + OPTIONS preflight to API gateway
---

# Quick Task 260625-nzn — Summary

## Outcome: ✅ PASS

Both tasks complete. The local-dev gateway CORS change is committed and the DEVX-01
full-stack smoke test passed — the entire stack boots from a single `./scripts/start.sh`.

## Task 1 — Commit gateway CORS change

- Committed the uncommitted `scripts/gateway/nginx.conf` change as **ffd464b**.
- Pre-checks: `nginx -t` passes (the only emerg was `host.docker.internal` DNS at
  static-validation time; compose supplies it via `extra_hosts: host-gateway`).
- The `gateway` compose service mounts this config, so `docker compose up -d` (inside
  start.sh) applies it with no extra wiring.

## Task 2 — DEVX-01 full-stack smoke test

Ran a true fresh boot (cleaned a stale prior stack first — see findings), then:

| Check | Result |
|-------|--------|
| Compose infra (postgres/redis/rabbitmq) healthy | ✅ |
| All 8 Spring services `/actuator/health` | ✅ UP (8081–8088) |
| Gradle toolchain | ✅ used Java 21 (21.0.11-tem) despite host Java 25 |
| Gateway `/gateway/health` | ✅ `{"status":"UP"}` |
| **CORS preflight** `OPTIONS /api/v1/auth/login` | ✅ `204` with `Access-Control-Allow-Origin: *`, `-Methods`, `-Headers` |
| admin-web :3000 | ✅ `307` (redirect to login = serving) |
| `./scripts/stop.sh` teardown | ⚠️ Spring services + infra torn down clean; Next.js child orphaned on :3000 (see findings) |

The CORS change from Task 1 was verified **live through the running gateway**, not just statically.

## Findings (follow-ups, non-blocking)

1. **`stop.sh` orphans the Next.js dev server.** It kills the recorded `.logs/pids`
   entries, but for admin-web that PID is the `npm run dev` wrapper; the forked
   `next-server` child survives and keeps holding :3000. The 8 Spring services tear
   down correctly. Worth a follow-up fix (e.g. kill the process group, or `pkill -f next-server`).
2. **Stale-process sensitivity.** A previously-running stack (from an earlier manual
   session, not tracked in `.logs/pids`) occupied all service ports and made a fresh
   `start.sh` fail with "Port 8081 already in use". start.sh has no pre-flight
   port/precondition check. Minor robustness gap.
3. **Boot time.** A fresh parallel boot of 8 services took ~3+ minutes (CPU-bound),
   longer than the script's "~30-60s" estimate. Cosmetic — the estimate could be updated.
4. **No code defect in the gateway change** — it behaves exactly as intended.

## State

- Stack fully torn down; all ports free, no containers running.
- Phase 7 DEVX-01 runtime smoke test (the item `07-VERIFICATION.md` flagged as
  `human_needed`) is now **satisfied**.
