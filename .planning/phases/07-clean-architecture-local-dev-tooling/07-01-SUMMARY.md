---
phase: 07-clean-architecture-local-dev-tooling
plan: "01"
subsystem: backend/payment-service
tags: [archunit, clean-architecture, tdd, red-green]
dependency_graph:
  requires: []
  provides: [ARCH-01-enforcement-scaffold]
  affects: [services/payment-service]
tech_stack:
  added: [com.tngtech.archunit:archunit-junit5:1.4.1]
  patterns: [layeredArchitecture, ArchRule, TDD-RED-first]
key_files:
  created:
    - services/payment-service/src/test/java/com/smsreseller/payment/PaymentArchitectureTest.java
  modified:
    - gradle/libs.versions.toml
    - services/payment-service/build.gradle.kts
decisions:
  - "ArchUnit layeredArchitecture() over Gradle module-per-layer — behavior-neutral, runs in existing JUnit 5 harness at zero runtime cost"
  - "domain layer permitted to carry jakarta.persistence annotations — deliberate MVP compromise; full domain/persistence split deferred to rollout playbook"
  - "Test intentionally RED in Wave 1 — becomes GREEN when 07-03 moves classes into layer packages"
metrics:
  duration: "8m"
  completed: "2026-06-24"
  tasks: 2
  files: 3
requirements: [ARCH-01]
---

# Phase 7 Plan 01: ArchUnit Architecture Enforcement Scaffold Summary

ArchUnit 1.4.1 wired to payment-service test classpath; RED layered-architecture rule (ARCH-01) authored first — enforces inward dependency law once 07-03 moves classes into layer packages.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Add archunit-junit5 to version catalog and payment-service test deps | e47ea8f | gradle/libs.versions.toml, services/payment-service/build.gradle.kts |
| 2 | Author RED layered-architecture test | e972fb0 | services/payment-service/src/test/java/com/smsreseller/payment/PaymentArchitectureTest.java |

## TDD Gate Compliance

- RED gate: `test(07-01)` commit e972fb0 — both ArchUnit rules fail (2/2 tests failed) because no layer packages exist yet. Correct and expected.
- GREEN gate: will be established by plan 07-03 (package re-layer).

## Decisions Made

1. ArchUnit `layeredArchitecture()` chosen over Gradle module-per-layer separation — keeps this a pure package-move (behavior-neutral), runs inside the existing JUnit 5 harness, zero runtime cost.
2. `jakarta.persistence.*` annotations explicitly NOT forbidden in domain — pragmatic MVP compromise; a full domain/persistence split would require new mapping code and violate the behavior-neutral constraint. Documented in rule comments.
3. Test is intentionally RED at Wave 1 commit; becomes GREEN after 07-03 completes the package re-layer. This is the correct TDD RED-first sequence for ARCH-01.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. This plan produces a test-only artifact; no UI or data stubs introduced.

## Threat Flags

None. Test source changes only; no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- [x] gradle/libs.versions.toml contains `archunit = "1.4.1"` and `archunit-junit5` library entry
- [x] services/payment-service/build.gradle.kts contains `testImplementation(libs.archunit.junit5)`
- [x] PaymentArchitectureTest.java exists with `@AnalyzeClasses`, `layers` ArchRule, and `domainPurity` ArchRule
- [x] `compileTestJava` exits 0
- [x] Test runs RED (2 failures) — correct expected state
- [x] Commits e47ea8f and e972fb0 verified in git log
