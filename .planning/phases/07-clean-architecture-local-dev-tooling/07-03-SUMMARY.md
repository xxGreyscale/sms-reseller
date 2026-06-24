---
phase: "07"
plan: "03"
subsystem: payment-service
tags: [clean-architecture, archunit, refactoring, ports-adapters, tdd-green]
dependency_graph:
  requires: ["07-01"]
  provides: ["ARCH-01-green"]
  affects: ["payment-service/src/main/java"]
tech_stack:
  added: []
  patterns:
    - "Ports & Adapters (Hexagonal Architecture) for repository output ports"
    - "Dual-extension JPA repo: extends JpaRepository<T,ID> + port interface"
    - "ArchUnit layeredArchitecture().consideringOnlyDependenciesInLayers() enforcement"
key_files:
  created:
    - services/payment-service/CLEAN-ARCHITECTURE.md
    - services/payment-service/src/main/java/com/smsreseller/payment/application/port/PaymentRepositoryPort.java
    - services/payment-service/src/main/java/com/smsreseller/payment/application/port/BundleRepositoryPort.java
    - services/payment-service/src/main/java/com/smsreseller/payment/application/port/OutboxRepositoryPort.java
    - services/payment-service/src/main/java/com/smsreseller/payment/infrastructure/persistence/PaymentRepository.java
    - services/payment-service/src/main/java/com/smsreseller/payment/infrastructure/persistence/BundleRepository.java
    - services/payment-service/src/main/java/com/smsreseller/payment/infrastructure/persistence/OutboxRepository.java
  modified:
    - services/payment-service/src/main/java/com/smsreseller/payment/application/PaymentService.java
    - services/payment-service/src/main/java/com/smsreseller/payment/application/BundleAdminService.java
    - services/payment-service/src/main/java/com/smsreseller/payment/application/CallbackProcessor.java
    - services/payment-service/src/test/java/com/smsreseller/payment/CallbackProcessingIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentHistoryIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentInitiationIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentTimeoutIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/ReconciliationIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/bundle/AdminBundleCatalogIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/payment/PaymentByIdIT.java
decisions:
  - "Port interfaces named *RepositoryPort to avoid naming conflict with infrastructure JPA repos (both in scope simultaneously)"
  - "Infrastructure JPA repos declare @Override save/findById to resolve JPA generic vs port concrete type ambiguity"
  - "AzampayCallbackPayload placed in application/port (not infrastructure/callback) to keep WebhookSignatureValidator port self-contained"
  - "BundleController (presentation) retains direct reference to infrastructure.persistence.BundleRepository — presentation->infrastructure is allowed by the layered rule"
metrics:
  duration: "~90 minutes (resuming from prior session)"
  completed: "2026-06-24"
---

# Phase 07 Plan 03: Clean Architecture Re-layer (payment-service) Summary

**One-liner:** Re-layered payment-service into domain/application/infrastructure/presentation with Ports & Adapters for repositories, driving the ArchUnit ARCH-01 test to GREEN while keeping all 34 tests (33 passing + 1 skipped stub) intact.

---

## What Was Built

### Task 1 — Re-layer Classes and Drive ArchUnit GREEN

All 34 production classes were relocated from package-by-feature (`payment/`, `bundle/`, `callback/`,
`gateway/`, `outbox/`, `config/`, `reconciliation/`, `timeout/`) into four clean architecture layers:

| Layer | Package | Classes |
|-------|---------|---------|
| Domain | `domain/payment`, `domain/bundle`, `domain/outbox` | Payment, PaymentStatus, SmsBundle, OutboxEntry, PaymentConfirmedEvent + 3 exceptions |
| Application | `application/`, `application/port/` | PaymentService, BundleAdminService, CallbackProcessor + 6 port interfaces/records |
| Infrastructure | `infrastructure/persistence/`, `.gateway/`, `.callback/`, `.messaging/`, `.scheduling/`, `.config/` | 3 JPA repos, 2 gateways, StubSignatureValidator, OutboxRelay, 2 scheduled jobs, 3 Spring config classes |
| Presentation | `presentation/` | 3 controllers + 5 DTOs/request records |

The critical architectural fix: application services previously depended directly on
`infrastructure.persistence.*Repository` classes (violating the inward dependency rule). Three
output port interfaces (`PaymentRepositoryPort`, `BundleRepositoryPort`, `OutboxRepositoryPort`)
were added to `application/port`. Application services now inject port interfaces. Infrastructure
JPA repos implement both `JpaRepository` and the corresponding port — Spring DI wires them at
runtime.

**ArchUnit result:** `PaymentArchitectureTest` — 2 tests, 0 failures (was 24 violations).
**Behavior oracle:** 34 tests — 33 passed, 1 skipped (RefundIT placeholder), 0 failures.

### Task 2 — CLEAN-ARCHITECTURE.md

Wrote `services/payment-service/CLEAN-ARCHITECTURE.md` documenting:
- Full package tree with responsibility annotations
- Dependency rules table (enforced by ArchUnit)
- Port + Adapter pattern with dual-extension JPA repo explanation
- AzampayCallbackPayload placement rationale
- MVP pragmatic compromises (JPA-as-domain, direct repo in BundleController)
- 8-step rollout playbook for applying this pattern to other services

---

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `d5f059a` | chore | cherry-picked 07-01: archunit dependency in build.gradle.kts |
| `f6b0d4b` | test | cherry-picked 07-01: PaymentArchitectureTest (RED) |
| `1330599` | docs | cherry-picked 07-01: 07-01-SUMMARY.md |
| `63d69e5` | refactor | re-layer payment-service + port interfaces (ArchUnit GREEN) |
| `97496ed` | docs | CLEAN-ARCHITECTURE.md reference and rollout playbook |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Port interface naming conflict**
- **Found during:** Task 1
- **Issue:** Port interfaces initially named `PaymentRepository`, `BundleRepository`, `OutboxRepository` — same simple names as the infrastructure JPA repos. When both were in scope during compilation, `import com.smsreseller.payment.application.port.PaymentRepository` in application services conflicted with the infrastructure class of the same simple name.
- **Fix:** Renamed port interfaces to `PaymentRepositoryPort`, `BundleRepositoryPort`, `OutboxRepositoryPort` to eliminate the naming ambiguity.
- **Files modified:** All three port interface files + all three infrastructure repos + three application service files.
- **Commit:** `63d69e5`

**2. [Rule 1 - Bug] JPA generic vs port concrete type ambiguity**
- **Found during:** Task 1 — `compileTestJava` errors for `save` (main) and `findById` (tests)
- **Issue:** `JpaRepository.save` has signature `<S extends T> S save(S entity)` while `PaymentRepositoryPort.save` has `Payment save(Payment payment)`. When `PaymentRepository` extended both, the compiler flagged `save` and `findById` as ambiguous.
- **Fix:** Added explicit `@Override Payment save(Payment payment)` and `@Override Optional<Payment> findById(UUID id)` declarations in `infrastructure.persistence.PaymentRepository`. Same pattern for `OutboxRepository` (`save` only) and `BundleRepository` (`save` only).
- **Files modified:** `infrastructure/persistence/PaymentRepository.java`, `OutboxRepository.java`, `BundleRepository.java`
- **Commit:** `63d69e5`

---

## Known Stubs

None. All pre-existing stubs (`RefundIT @Disabled`) were unchanged from prior state.

---

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. This plan
is a pure package reorganization — no trust boundary changes.

---

## Self-Check

- [x] `services/payment-service/CLEAN-ARCHITECTURE.md` — exists
- [x] Commit `63d69e5` — exists (`git log --oneline` confirmed)
- [x] Commit `97496ed` — exists
- [x] ArchUnit `PaymentArchitectureTest`: 2 tests GREEN
- [x] Integration tests: 33 passed, 1 skipped, 0 failures

## Self-Check: PASSED
