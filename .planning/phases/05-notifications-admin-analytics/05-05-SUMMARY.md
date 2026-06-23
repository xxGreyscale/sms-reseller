---
phase: 05-notifications-admin-analytics
plan: "05"
subsystem: payment-service
tags: [admin, bundle-catalog, crud, security, tdd]
dependency_graph:
  requires: [05-01]
  provides: [ADMN-07]
  affects: [05-09-admin-web]
tech_stack:
  added: []
  patterns:
    - ROLE_ADMIN guard via jwtAuthenticationConverter reading roles claim
    - BundleAdminService transactional create/update/delete pattern
    - Controller error-handling via IllegalStateException with not-found string check
key_files:
  created:
    - services/payment-service/src/main/java/com/smsreseller/payment/bundle/AdminBundleController.java
    - services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleSaveRequest.java
    - services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleAdminService.java
  modified:
    - services/payment-service/src/main/java/com/smsreseller/payment/config/SecurityConfig.java
    - services/payment-service/src/test/java/com/smsreseller/payment/bundle/AdminBundleCatalogIT.java
    - services/payment-service/src/test/java/com/smsreseller/payment/JwtTestHelper.java
decisions:
  - SecurityConfig jwtAuthenticationConverter reads roles claim directly (ROLE_ADMIN string) — consistent with messaging-service pattern from 05-PATTERNS.md
  - BundleAdminService wraps BundleRepository rather than embedding logic in controller — testable and follows service-layer pattern
  - AdminBundleController returns 201 Created with Location header on POST
  - update/delete return 404 via IllegalStateException catch matching SenderIdAdminController pattern
metrics:
  duration: 15m
  completed: "2026-06-22"
  tasks: 1
  files: 6
---

# Phase 05 Plan 05: Admin Bundle Catalog CRUD Summary

**One-liner:** ROLE_ADMIN-guarded create/update/delete on sms_bundles via /api/v1/admin/bundles with @Positive/@Min(1) validation and jwtAuthenticationConverter roles-claim authority mapping.

## What Was Built

AdminBundleController at `/api/v1/admin/bundles` implementing full CRUD for the payment-service SMS bundle catalog (ADMN-07). The existing public read catalog (`/api/v1/bundles`, PYMT-01) is untouched.

### Endpoints (all require ROLE_ADMIN)

| Method | Path | Response | Notes |
|--------|------|----------|-------|
| GET | /api/v1/admin/bundles | 200 List<BundleDto> | Includes inactive bundles |
| POST | /api/v1/admin/bundles | 201 BundleDto | Creates new bundle; isPurchasable=true by default |
| PUT | /api/v1/admin/bundles/{id} | 200 BundleDto / 404 | Updates name/smsCount/priceTzs/active |
| DELETE | /api/v1/admin/bundles/{id} | 204 / 404 | Hard delete from sms_bundles |

### Security

- SecurityConfig: `/api/v1/admin/bundles/**` → `hasRole("ADMIN")`
- `jwtAuthenticationConverter()` reads `roles` claim from JWT (e.g. `["ROLE_ADMIN"]`) and maps to `SimpleGrantedAuthority`
- ROLE_USER tokens receive 403; unauthenticated requests receive 401

### Validation (BundleSaveRequest)

- `@NotBlank` on `name`
- `@Min(1)` on `smsCount` (T-05-13)
- `@Positive` on `priceTzs` — raw TZS BIGINT per D-11 (T-05-13)
- `boolean active` — controls customer catalog visibility

## TDD Gate Compliance

- RED commit: `ecc6d97` — `test(05-05): RED — AdminBundleCatalogIT failing assertions for ADMN-07 CRUD`
- GREEN commit: `8ad6d12` — `feat(05-05): GREEN — AdminBundleController CRUD + BundleSaveRequest + SecurityConfig ADMIN guard (ADMN-07)`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all endpoints write to the real sms_bundles table via BundleRepository.

## Threat Flags

None — all threats in plan's threat model (T-05-12, T-05-13) are mitigated by implementation.

## Self-Check: PASSED

Files verified:
- FOUND: services/payment-service/src/main/java/com/smsreseller/payment/bundle/AdminBundleController.java
- FOUND: services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleSaveRequest.java
- FOUND: services/payment-service/src/main/java/com/smsreseller/payment/bundle/BundleAdminService.java

Commits verified:
- ecc6d97 (RED test)
- 8ad6d12 (GREEN implementation)

Tests: AdminBundleCatalogIT 9/9 pass; full payment-service test suite green (no regressions).
