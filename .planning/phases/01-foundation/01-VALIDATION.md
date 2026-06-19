---
phase: 1
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-18
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Testcontainers (Spring Boot 3.5 managed) |
| **Config file** | `build.gradle.kts` — `useJUnitPlatform()` in each subproject |
| **Quick run command** | `./gradlew :shared-security:test :shared-observability:test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~120 seconds (Testcontainers startup) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared-security:test :shared-observability:test`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | INFR-01, INFR-02 | — | N/A | integration | `./gradlew test` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | INFR-03 | — | N/A | build | `./gradlew build` | ❌ W0 | ⬜ pending |
| 1-03-01 | 03 | 2 | INFR-01 | — | N/A | compose | `docker compose up -d && docker compose ps` | ❌ W0 | ⬜ pending |
| 1-04-01 | 04 | 2 | INFR-04, INFR-05 | — | Secrets not in Git | config | `grep -r "password\|secret\|token" k8s/ --include="*.yaml" \| grep -v Secret` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `shared-security/src/test/java/` — directory + placeholder test stub
- [ ] `shared-observability/src/test/java/` — directory + placeholder test stub
- [ ] Gradle wrapper in root `gradlew` — must be executable

*Wave 0 installs the test skeleton so subsequent tasks have a green baseline.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `docker compose up` starts all 3 services clean | INFR-02 | Requires Docker daemon and image pull | Run `docker compose up -d`; verify `docker compose ps` shows all healthy |
| Kustomize overlay renders without errors | INFR-01 | Requires `kubectl kustomize` installed | Run `kubectl kustomize k8s/overlays/dev` and inspect output |
| GitHub Actions CI triggers on PR | INFR-03 | Requires GitHub repo and PR creation | Open a test PR; verify workflow runs and passes in Actions tab |
| Sealed Secrets kubeseal encrypts a secret | INFR-05 | Requires `kubeseal` CLI and cluster access | Run `kubeseal` against a test secret YAML; verify encrypted output |
| OTel traces appear in collector output locally | INFR-04 | Requires running Docker Compose stack | Check `docker compose logs otel-collector` for trace data |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
