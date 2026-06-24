---
phase: 07-clean-architecture-local-dev-tooling
plan: "02"
subsystem: local-dev-tooling
tags: [docker-compose, postgres, redis, rabbitmq, env, devx]
dependency_graph:
  requires: []
  provides: [compose-stack, per-service-databases, env-template]
  affects: [07-04-start-script]
tech_stack:
  added: []
  patterns:
    - docker-entrypoint-initdb.d for per-service logical DB creation
    - Compose v2 schema (no version: key)
    - Spring relaxed binding via exported env vars
key_files:
  created:
    - compose.yaml
    - scripts/db-init/01-create-service-databases.sql
    - .env.example
  modified: []
decisions:
  - compose.yaml mounts scripts/db-init into /docker-entrypoint-initdb.d:ro so 8 logical DBs are created on first Postgres boot without needing a separate provisioning step
  - Per-service SERVER_PORT and SPRING_DATASOURCE_URL intentionally absent from .env.example — the start script (07-04) derives these from a port-map and URL template to avoid 8x2 duplicated lines
  - Named volumes (pgdata/redisdata/rabbitdata) isolate local dev data from container lifecycle
metrics:
  duration: "~10 minutes"
  completed: "2026-06-24"
  tasks: 3
  files: 3
requirements: [DEVX-02, DEVX-03]
---

# Phase 07 Plan 02: Local Infra Docker Compose + Env Template Summary

One-liner: Compose stack (PG16/Redis7/Rabbit3) with healthchecks, named volumes, per-service logical DB init, and committed .env.example template with safe sandbox defaults.

## What Was Built

- `compose.yaml` — Compose v2 schema, three services (postgres:16, redis:7, rabbitmq:3-management), each with healthchecks and named volumes. Postgres mounts `./scripts/db-init` into `/docker-entrypoint-initdb.d:ro` for automatic DB creation.
- `scripts/db-init/01-create-service-databases.sql` — Creates 8 logical databases (identity, catalog, wallet, payment, contact, messaging, notification, admin) owned by the Postgres superuser on first container boot.
- `.env.example` — Documents all env vars consumed by compose and by Spring relaxed binding (POSTGRES_USER/PASSWORD/DB, RABBITMQ_USER/PASSWORD, SPRING_DATASOURCE_*, SPRING_DATA_REDIS_*, SPRING_RABBITMQ_*, AZAMPAY_*). Placeholder-only Azampay values.

## Verification Results

| Check | Result |
|-------|--------|
| `docker compose config` exits 0 | PASS |
| `grep -c healthcheck compose.yaml` returns 3 | PASS (3) |
| All 3 services reach healthy state | PASS (verified — ~15s) |
| `grep -c CREATE DATABASE` returns 8 | PASS (8) |
| Fresh volume: all 8 DB names present in pg_database | PASS |
| AZAMPAY_CLIENT_ID, SPRING_DATASOURCE_USERNAME, POSTGRES_USER in .env.example | PASS |
| `.env` gitignored | PASS |
| `.env.example` NOT gitignored | PASS |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. This plan creates infrastructure configuration files, not application code.

## Threat Flags

None. `.env.example` carries placeholder Azampay credentials only. `.env` is gitignored (lines 16-18 of .gitignore). No private keys or real credentials introduced.

## Self-Check: PASSED

- compose.yaml: EXISTS
- scripts/db-init/01-create-service-databases.sql: EXISTS
- .env.example: EXISTS
- Commits: 220b112, 599d240, c4cc0b2 — all verified in git log
