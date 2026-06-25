---
phase: 07-clean-architecture-local-dev-tooling
plan: "04"
subsystem: devx
tags: [local-dev, startup-scripts, jwt-keys, spring-boot-config]
dependency_graph:
  requires: ["07-02", "07-03"]
  provides: ["DEVX-01"]
  affects: [all-8-services, admin-web]
tech_stack:
  added: []
  patterns:
    - Spring relaxed env binding for datasource/redis/rabbitmq (no hardcoded secrets)
    - Dev-profile application-dev.yml per service
    - RSA public key committed to src/main/resources/keys/ (public only; private stays in test-keys)
    - Compose healthcheck polling in start.sh (no sleep-based wait)
key_files:
  created:
    - services/catalog-service/src/main/resources/application.yml
    - services/*/src/main/resources/application-dev.yml (8 files)
    - services/*/src/main/resources/keys/jwt-public.pem (8 files)
    - scripts/start.sh
    - scripts/stop.sh
  modified: []
decisions:
  - Identity-service dev profile overrides jwt.private-key-location to file: reference pointing at test-keys/jwt-private.pem so no private key is committed to src/main/resources
  - Per-service SERVER_PORT and SPRING_DATASOURCE_URL set inline in start.sh loop (not in .env) — keeps .env.example clean and avoids duplicating the port map
  - start.sh uses a for-loop over 8 service names with individual background bootRun invocations (not --parallel or a combined task) per research guidance
  - stop.sh iterates .logs/pids and kills each PID individually then runs docker compose down
metrics:
  duration: "~20 minutes"
  completed: "2026-06-25"
  tasks: 2
  files: 19
requirements_satisfied: [DEVX-01]
---

# Phase 07 Plan 04: Dev-Profile Config + Start/Stop Scripts Summary

One-command full-stack boot for all 8 Spring Boot services and admin-web, with per-service dev-profile YAML, RSA public JWT keys committed to main resources, and matching teardown.

## What Was Built

### Task 1 — Dev-profile config and JWT public keys

**Audit findings:**
| Service | Missing before | Fixed |
|---------|----------------|-------|
| catalog-service | Base `application.yml` entirely absent; no keys dir | Created `application.yml` + `application-dev.yml` + `keys/jwt-public.pem` |
| identity-service | No `application-dev.yml`; no `keys/` in main resources | Created both; dev profile points private-key at test-keys via `file:` ref |
| wallet-service | No `application-dev.yml`; no `keys/` in main resources | Created both |
| payment-service | No `application-dev.yml`; no `keys/` in main resources | Created both |
| contact-service | No `application-dev.yml`; no `keys/` in main resources | Created both |
| messaging-service | No `application-dev.yml`; no `keys/` in main resources | Created both |
| notification-service | No `application-dev.yml`; no `keys/` in main resources | Created both |
| admin-service | No `application-dev.yml`; no `keys/` in main resources | Created both |

**Security verification:** `grep -rq 'PRIVATE KEY' services/*/src/main/resources/keys/` returns non-zero — no private key committed to any main resources keys directory. The RSA public key reused from `identity-service/src/test/resources/test-keys/jwt-public.pem` (same keypair across all services, consistent with Phase 2 pattern).

**Identity-service dev private key:** The `application-dev.yml` overrides `app.jwt.private-key-location` to `file:services/identity-service/src/test/resources/test-keys/jwt-private.pem`. This keeps the private key out of `src/main/resources` while making it accessible to `bootRun` launched from the repo root.

**application-dev.yml pattern (all non-identity services):**
```yaml
server:
  port: ${SERVER_PORT:8081}  # default for standalone; start.sh always sets this

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/<svc>}
    username: ${SPRING_DATASOURCE_USERNAME:smsreseller}
    password: ${SPRING_DATASOURCE_PASSWORD:smsreseller}
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:guest}
    password: ${SPRING_RABBITMQ_PASSWORD:guest}
```

### Task 2 — start.sh and stop.sh

**start.sh sequence:**
1. Fail fast if `.env` missing (`cp .env.example .env` hint printed)
2. `set -a; source .env; set +a`
3. `docker compose up -d`
4. Poll `docker compose ps --format` until postgres, redis, rabbitmq are all `healthy` (120s timeout; interval 3s; no raw sleep in happy path)
5. Loop over 8 services, launching each as a separate `./gradlew :services:<svc>-service:bootRun` background process with `SERVER_PORT` and `SPRING_DATASOURCE_URL` injected; PID recorded to `.logs/pids`
6. Launch `admin-web` via `(cd apps/admin-web && npm run dev)` background; PID recorded
7. Print port/log summary

**stop.sh sequence:**
1. Iterate `.logs/pids`, kill each live PID (ignore-missing via `kill -0` check)
2. `docker compose down`

**Port map:**

| Service | Port |
|---------|------|
| identity | 8081 |
| catalog | 8082 |
| wallet | 8083 |
| payment | 8084 |
| contact | 8085 |
| messaging | 8086 |
| notification | 8087 |
| admin | 8088 |
| admin-web | 3000 |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None relevant to this plan. All service stubs pre-exist from earlier phases (StubPaymentGateway, StubSmsProvider, etc.) and are unchanged.

## Threat Flags

None. All threat mitigations from the plan's threat model are in place:
- T-07-03: Only public keys committed to `src/main/resources/keys/` — verified by grep
- T-07-04: `.env` gitignored (from 07-02); start.sh sources it without echoing
- T-07-05: Changes scoped to `application-dev.yml` only; base `application.yml` prod behavior unchanged

## Self-Check

- [x] `services/catalog-service/src/main/resources/application.yml` — created
- [x] `services/*/src/main/resources/keys/jwt-public.pem` — 8 files created
- [x] `services/*/src/main/resources/application-dev.yml` — 8 files created
- [x] `scripts/start.sh` — created, chmod+x, syntax valid
- [x] `scripts/stop.sh` — created, chmod+x, syntax valid
- [x] No `PRIVATE KEY` in any `src/main/resources/keys/` path
- [x] Task 1 commit: `6ad1e6e`
- [x] Task 2 commit: `98ae0da`

## Self-Check: PASSED
