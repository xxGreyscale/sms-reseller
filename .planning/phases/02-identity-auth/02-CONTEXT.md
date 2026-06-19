# Phase 2: Identity & Auth - Context

**Gathered:** 2026-06-19
**Status:** Ready for planning

<domain>
## Phase Boundary

A user can register (phone + email), submit their NIN and be verified asynchronously
via NIDA (mock-first), receive 50 free SMS credits on verification, log in with
persistent multi-device sessions, log out, reset a forgotten password, and be assigned
a default numeric sender ID — and every downstream module can validate the JWTs this
module issues locally via the shared-security library, with no runtime call back to
identity.

**Requirements:** IDEN-01 … IDEN-08, SNDR-01 (9 total).

**Explicitly OUT of scope (deferred to other phases):**
- The bundle catalog (definitions + read API) — owned by Phase 3 via PYMT-01.
- Custom alphanumeric sender IDs and admin approval (SNDR-02/03/04) — Phase 4.
- Wallet/credit ledger mechanics — Phase 3 consumes the "50 free credits granted"
  event but owns the ledger itself.

</domain>

<decisions>
## Implementation Decisions

### Unverified user experience (IDEN-02, IDEN-08)
- **D-01:** "Logged in, but walled." On successful registration the user immediately
  receives a session (access + refresh JWT) and is in PENDING_VERIFICATION status. They
  can log in and see the app shell, but every feature (send, buy, etc.) shows a
  "verification pending" gated state until status flips to VERIFIED.
- **D-02:** The JWT MUST carry the verification status as a claim (e.g.
  `verification_status: PENDING_VERIFICATION | VERIFIED`) so downstream modules can
  enforce the wall locally without calling identity at runtime. This is the mechanism
  that makes the "logged in but walled" model work across the modular monolith.
- **D-03:** When NIDA verifies (or background retry succeeds), status transitions to
  VERIFIED, 50 free SMS credits are granted (IDEN-03), and the default numeric sender
  ID is assigned (SNDR-01). Newly issued/refreshed tokens then carry the VERIFIED claim.

### NIDA stub behavior (mock-first)
- **D-04:** `NidaVerificationService` interface with `StubNidaVerificationService`
  (`@Profile("stub")`, default in dev/staging) and `RealNidaVerificationService`
  (`@Profile("prod")`). Locked by roadmap.
- **D-05:** The stub supports **configurable outcomes** — success / rejection / timeout /
  unavailable — switchable via config and/or a "magic NIN" convention (e.g. a NIN
  ending in a reserved suffix maps to a specific outcome). This makes the IDEN-08
  degraded path (NIDA down → stay PENDING → background retry → eventual VERIFIED)
  fully demoable end-to-end before real NIDA access exists. Default outcome is
  auto-verify after ~3s.

### Session & login policy (IDEN-04, IDEN-05, IDEN-06)
- **D-06:** Tokens: 15-minute access JWT + 7-day refresh token (locked by roadmap).
- **D-07:** **Multi-device, revoke-current** session model. Each device holds its own
  refresh token; a user can be signed in on phone + web simultaneously. Logout (IDEN-06)
  revokes only the current device's session.
- **D-08:** Refresh tokens **rotate on each use** (a used refresh token is invalidated and
  a new one issued) — standard refresh-token-rotation for replay protection.
- **D-09:** A successful **password reset revokes ALL sessions** for that user (security
  default). Flag for reconsideration if the user objects later.

### Login credentials & password rules (IDEN-04, IDEN-07)
- **D-10:** Login is **email + password**. Registration still captures both phone and
  email, but email is the login identifier (matches IDEN-04 as written).
- **D-11:** Password reset (IDEN-07) is via **email link** with a time-limited,
  single-use token.
- **D-12:** Password policy: minimum 8 characters with a basic strength check. Account
  **lockout after ~5 failed attempts** for a short cooldown window (brute-force
  mitigation). Exact thresholds are planner/researcher discretion.

### Email delivery (mock-first implication)
- **D-13:** Password-reset emails (IDEN-07) require an email-sending capability. Since
  the project is mock-first for external providers, email sending should follow the same
  pattern: an interface with a stub implementation (logs/records the link in dev) and a
  real implementation behind a profile. Researcher to confirm the email provider choice.

### Claude's Discretion
- Exact lockout thresholds, cooldown duration, password-strength rule specifics.
- Magic-NIN suffix convention and stub config surface for D-05.
- Refresh-token storage mechanism (Redis vs Postgres) — note CLAUDE.md already lists
  Redis for sessions/OTP; researcher to confirm.
- Email provider selection for D-13.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` §"Phase 2: Identity & Auth" — goal, locked mock-first NIDA
  decision, success criteria, scope note (catalog → Phase 3).
- `.planning/REQUIREMENTS.md` — IDEN-01…IDEN-08 and SNDR-01 definitions; SNDR-02/03/04
  ownership (Phase 4) and PYMT-01 ownership (Phase 3) for boundary clarity.

### Project standards
- `CLAUDE.md` — Spring Boot 3.5.9 / Java 21 stack, JWT pattern ("identity issues, all
  other services validate"), shared-security library contract, Redis for sessions/OTP,
  Resilience4j circuit-breaker + spring-retry for NIDA, NIDA integration guidance
  (timeouts, no caching of verification results, Redis session state, UTF-8 Swahili
  names), `jakarta.*` not `javax.*`.

### Phase 1 foundation (built)
- `libs/shared-security/` — the JWT-validation library every downstream module uses;
  this phase's identity-service is the JWT *issuer* that it validates against.
- `services/identity-service/build.gradle.kts` — the module skeleton this phase fills in.
- `gradle/libs.versions.toml` — version catalog (spring-security, oauth2-resource-server,
  spring-retry, resilience4j, data-redis, testcontainers all already pinned).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `libs/shared-security` (Phase 1): the library downstream modules use to validate JWTs.
  Identity-service must issue tokens whose claims (incl. `verification_status` from D-02)
  this library can read. The two must agree on the claim schema.
- Version catalog already has: `spring-boot-starter-security`,
  `spring-boot-starter-oauth2-resource-server`, `spring-retry`,
  `resilience4j-spring-boot3`, `spring-boot-starter-data-redis`,
  `spring-boot-starter-test`, `testcontainers-postgresql` — no new catalog entries
  expected for the core of this phase.

### Established Patterns
- Mock-first interface + `@Profile("stub")` / `@Profile("prod")` split (used here for
  NIDA per D-04/D-05, and likely for email per D-13).
- Per-service Flyway migrations (`flyway-core` + `flyway-database-postgresql` already on
  identity-service) — this phase creates the identity schema's first migrations.

### Integration Points
- Identity-service is the sole JWT issuer; all 8 other modules validate via
  shared-security (no runtime callback) — D-02's claim must be self-contained.
- "50 free credits granted on verification" (IDEN-03) is an event the Phase 3 wallet
  module will consume — keep the verification→credit-grant boundary clean (event/outbox,
  not a synchronous cross-module call).

</code_context>

<specifics>
## Specific Ideas

- Verification status as a JWT claim is the load-bearing design choice (D-02) — it keeps
  the "logged in but walled" UX enforceable everywhere without coupling modules to identity.
- The configurable NIDA stub (D-05) is specifically to make IDEN-08's degraded path a
  demoable, testable first-class flow rather than a unit-test-only concern.

</specifics>

<deferred>
## Deferred Ideas

- **Bundle catalog (definitions + read API)** — moved to Phase 3 (PYMT-01). Confirmed
  during scope review; ROADMAP updated and committed (`9ae20d4`).
- **Custom alphanumeric sender IDs + admin approval** (SNDR-02/03/04) — Phase 4.
- **Reset-by-SMS** as an alternative to email reset — would need the SMS provider
  (mocked until Phase 4); email-only reset at MVP.
- Reconsider D-09 (password reset revoking all sessions) if it proves too aggressive UX.

</deferred>

---

*Phase: 2-Identity & Auth*
*Context gathered: 2026-06-19*
