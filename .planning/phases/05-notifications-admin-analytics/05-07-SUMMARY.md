---
phase: "05"
plan: "07"
subsystem: admin-audit
tags: [tdd, audit-log, dual-source, idempotency, rabbitmq, append-only, admn-06, d-09]
dependency_graph:
  requires: [05-01]
  provides:
    - admin-service audit module (AuditEntry, AuditService, AuditController)
    - POST /api/v1/admin/audit/record — mutation seam (D-09a) for admin-web 05-09
    - GET /api/v1/admin/audit — filterable paged viewer, ADMIN-guarded (ADMN-06)
    - DomainEventAuditConsumer — passive idempotent consumer from identity/messaging/wallet exchanges (D-09b)
    - V1 processed_events + V2 audit_entries Flyway migrations
  affects:
    - admin-web 05-09 (calls POST /api/v1/admin/audit/record after each mutation)
    - identity.events / messaging.events / wallet.events (consumed passively)
tech_stack:
  added:
    - JPA auditing (@EnableJpaAuditing, @CreatedDate) for server-set audit timestamps
    - @JdbcTypeCode(SqlTypes.JSON) for JSONB column compatibility in Hibernate 6
    - Flyway enabled in test profile (creates admin schema for Testcontainers tests)
    - Native SQL search query (avoids lower(bytea) PostgreSQL null-parameter type error)
  patterns:
    - Append-only audit table (no UPDATE/DELETE exposed in AuditRepository)
    - ProcessedEventRepository.tryInsert — INSERT ON CONFLICT DO NOTHING idempotency gate
    - ignoreDeclarationExceptions=true on all @Exchange annotations (passive consumer)
    - hasRole(ADMIN) on /api/v1/admin/** via SecurityConfig
key_files:
  created:
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AuditEntry.java
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AuditRepository.java
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AuditService.java
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AuditController.java
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AuditEntryDto.java
    - services/admin-service/src/main/java/com/opendesk/admin/audit/AdminMutationController.java
    - services/admin-service/src/main/java/com/opendesk/admin/config/SecurityConfig.java
    - services/admin-service/src/main/java/com/opendesk/admin/config/RabbitMqConfig.java
    - services/admin-service/src/main/java/com/opendesk/admin/consumer/DomainEventAuditConsumer.java
    - services/admin-service/src/main/java/com/opendesk/admin/idempotency/ProcessedEvent.java
    - services/admin-service/src/main/java/com/opendesk/admin/idempotency/ProcessedEventRepository.java
    - services/admin-service/src/main/resources/db/migration/V1__create_processed_events.sql
    - services/admin-service/src/main/resources/db/migration/V2__create_audit_entries.sql
    - services/admin-service/src/test/java/com/opendesk/admin/TestKeys.java
    - services/admin-service/src/test/java/com/opendesk/admin/JwtTestHelper.java
    - services/admin-service/src/test/java/com/opendesk/admin/AdminTestConfiguration.java
  modified:
    - services/admin-service/src/main/java/com/opendesk/admin/AdminServiceApplication.java (@EnableJpaAuditing + @EnableScheduling)
    - services/admin-service/src/test/java/com/opendesk/admin/audit/AuditLogIT.java (real assertions replacing Assumptions.abort() placeholder)
    - services/admin-service/src/test/resources/application-test.yml (Flyway enabled for admin schema creation in Testcontainers)
decisions:
  - "Flyway enabled in test profile to create admin schema — ddl-auto=create-drop cannot create a schema from @Table(schema=admin) annotations alone"
  - "Native SQL for AuditRepository.search() to avoid lower(bytea) PostgreSQL type error when null is passed as a text parameter in JPQL"
  - "@JdbcTypeCode(SqlTypes.JSON) on AuditEntry.details to map String field to JSONB column in Hibernate 6"
  - "DomainEventAuditConsumer uses raw Message + ObjectMapper.readValue for flexible event parsing across 4 exchanges with heterogeneous payload shapes"
  - "ignoreDeclarationExceptions=true on every @Exchange — admin-service owns no exchanges (passive consumer per D-14)"
metrics:
  duration: "30m"
  completed: "2026-06-22"
  tasks: 2
  files: 19
---

# Phase 05 Plan 07: Admin Audit Log (ADMN-06) Summary

**One-liner:** Append-only dual-source audit log for admin-service — admin mutations via POST /api/v1/admin/audit/record (D-09a) and domain events from identity/messaging/wallet exchanges via idempotent passive consumer (D-09b), with ADMIN-guarded filterable viewer GET /api/v1/admin/audit.

---

## What Was Built

### Audit Schema

**V1__create_processed_events.sql:** Idempotency guard table (`admin.processed_events`, PRIMARY KEY on `event_id`). Same pattern as wallet-service and notification-service.

**V2__create_audit_entries.sql:** Append-only audit log table (`admin.audit_entries`):
- `id UUID` (generated), `timestamp TIMESTAMPTZ DEFAULT now()` (server-set), `actor VARCHAR` (admin email or "system"), `action VARCHAR` (monospace label), `target VARCHAR` (aggregate id), `details JSONB` (UI Details column)
- Index on `timestamp DESC` for default viewer sort; index on `actor` for filter
- No UPDATE/DELETE ever touch this table (T-05-17)

### Task 1: Audit Model + Viewer API + Mutation Seam (D-09a)

**AuditEntry:** JPA entity with `@CreatedDate` for server-set timestamp, `@JdbcTypeCode(SqlTypes.JSON)` for JSONB field.

**AuditRepository:** Append-only interface — exposes native paged search (nullable from/to/actor filters, newest-first) but no update/delete methods on audit_entries. `JpaRepository.deleteAll()` is present but only called in test teardown.

**AuditService:**
- `recordMutation(actor, action, target, details)` — builds and saves an AuditEntry
- `search(from, to, actor, page, size)` — delegates to native query, maps to DTOs

**AuditController:** `GET /api/v1/admin/audit` — optional query params `from`, `to`, `actor`, `page`, `size`. Returns `Page<AuditEntryDto>`.

**AdminMutationController:** `POST /api/v1/admin/audit/record` — admin-web calls this after each mutation (D-09a seam). Returns 204 No Content.

**SecurityConfig:** `/api/v1/admin/**` → `hasRole("ADMIN")`; STATELESS; roles-claim converter (reads `roles` JWT claim, maps to `SimpleGrantedAuthority`).

### Task 2: Passive Idempotent Domain-Event Consumer (D-09b)

**ProcessedEvent + ProcessedEventRepository:** Verbatim port from wallet-service pattern. `tryInsert()` uses `INSERT INTO admin.processed_events ... ON CONFLICT DO NOTHING` to atomically gate duplicate events (T-05-18).

**RabbitMqConfig:** Jackson2JsonMessageConverter wired to both `RabbitTemplate` and `SimpleRabbitListenerContainerFactory`. Admin-service declares no exchanges (passive consumer, D-14).

**DomainEventAuditConsumer:** 4 `@RabbitListener` methods, each with `ignoreDeclarationExceptions="true"` (T-05-20, RESEARCH.md Pitfall 1):
- `admin.identity.UserVerified` on `identity.events / identity.UserVerified`
- `admin.messaging.SenderIdDecided` on `messaging.events / messaging.SenderIdDecided`
- `admin.wallet.PaymentConfirmed` on `wallet.events / wallet.PaymentConfirmed`
- `admin.wallet.RefundGranted` on `wallet.events / wallet.RefundGranted`

Each handler is `@Transactional`. Flow: `tryInsert(eventId)` → early return if duplicate → `auditService.recordMutation("system", eventType, aggregateId, payloadJson)`.

### Test Infrastructure

**JwtTestHelper + TestKeys + AdminTestConfiguration:** JWT minting test helpers (same pattern as wallet-service). `createAdminToken()` for ROLE_ADMIN assertions; `createUserToken()` for 403 check.

### AuditLogIT (GREEN — all 4 assertions pass)

| Test | Source | Result |
|------|--------|--------|
| `mutationRecordEndpointAppendsAuditRowAndViewerReturnsIt` | D-09a | PASS |
| `auditViewerRequiresAdminRole` | T-05-19 | PASS |
| `domainEventConsumptionCreatesAuditEntryIdempotently` | D-09b | PASS |
| `auditEntriesAreAppendOnly` | T-05-17 | PASS |

---

## Audit Entry Schema (for admin-web 05-09 UI contract)

`GET /api/v1/admin/audit` returns `Page<AuditEntryDto>`:
```json
{
  "content": [{
    "id": "uuid",
    "timestamp": "2026-06-22T00:00:00Z",
    "actor": "admin@opendesk.co",
    "action": "SENDER_ID_APPROVED",
    "target": "uuid-of-sender-id",
    "details": "{\"decision\":\"approved\"}"
  }],
  "totalElements": 1,
  "totalPages": 1
}
```

`POST /api/v1/admin/audit/record` body (admin-web mutation seam):
```json
{
  "actor": "admin@opendesk.co",
  "action": "SENDER_ID_APPROVED",
  "target": "uuid",
  "details": "{...}"
}
```

---

## Deviations from Plan

### Auto-Fixed Issues

**1. [Rule 3 - Blocking] Flyway enabled in test profile to create admin schema**
- **Found during:** Task 1 test run
- **Issue:** Test profile used `ddl-auto=create-drop` + `flyway.enabled=false`. Hibernate `create-drop` cannot create a PostgreSQL schema from `@Table(schema="admin")` annotations — schema must pre-exist. Container DB starts with no `admin` schema.
- **Fix:** Changed `application-test.yml` to `flyway.enabled=true`, `create-schemas: true`, `ddl-auto=validate`. Flyway V1+V2 migrations create the schema and tables.
- **Files modified:** `src/test/resources/application-test.yml`

**2. [Rule 1 - Bug] Native SQL replaces JPQL to avoid lower(bytea) PostgreSQL type error**
- **Found during:** Task 1 GET /api/v1/admin/audit test assertion
- **Issue:** JPQL `LOWER(CONCAT('%', :actor, '%'))` when `:actor` is null causes PostgreSQL to infer `bytea` type for the null parameter, then fail `lower(bytea)` — function doesn't exist.
- **Fix:** Replaced with a native SQL query using `CAST(:actor AS text)` which correctly types the null parameter.
- **Files modified:** `AuditRepository.java`

**3. [Rule 1 - Bug] @JdbcTypeCode(SqlTypes.JSON) added to AuditEntry.details**
- **Found during:** Task 2 consumer test
- **Issue:** Hibernate 6 maps `String` fields with `columnDefinition="jsonb"` as `character varying`, causing `ERROR: column "details" is of type jsonb but expression is of type character varying`.
- **Fix:** Added `@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)` on the `details` field.
- **Files modified:** `AuditEntry.java`

---

## Known Stubs

None. All production paths are fully implemented and tested.

---

## Threat Flags

No new security surface beyond what the plan's threat model covers. All 4 T-05-17 through T-05-20 mitigations are implemented and verified.

---

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED (test commit) | 8ab04a8 | PASSED — compilation failure confirmed (AuditRepository/AuditEntry not yet exist) |
| GREEN (feat commit) | 1104f4d | PASSED — all 4 AuditLogIT assertions pass |
| REFACTOR | n/a | No cleanup needed |

---

## Self-Check

| Check | Result |
|-------|--------|
| AuditEntry.java exists | PASSED |
| AuditRepository.java exists | PASSED |
| AuditService.java exists | PASSED |
| AuditController.java exists | PASSED |
| AdminMutationController.java exists | PASSED |
| DomainEventAuditConsumer.java exists | PASSED |
| V1__create_processed_events.sql exists | PASSED |
| V2__create_audit_entries.sql contains CREATE TABLE audit_entries | PASSED |
| SecurityConfig.java exists | PASSED |
| AuditLogIT 4/4 tests GREEN | PASSED |
| RED commit 8ab04a8 exists | PASSED |
| GREEN commit 1104f4d exists | PASSED |

## Self-Check: PASSED
