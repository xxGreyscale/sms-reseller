---
phase: 05-notifications-admin-analytics
plan: "08"
subsystem: admin-web
tags: [nextjs, admin-web, auth, httponly-cookie, middleware, server-action, server-component, tdd, shadcn, tailwind]
dependency_graph:
  requires: [05-01, 05-03, 05-04]
  provides: [ADMN-01, ADMN-02, ADMN-03]
  affects:
    - 05-09 (builds on lib/api, lib/auth, lib/format, AppSidebar, admin shell — all established here)
tech_stack:
  added: []
  patterns:
    - httpOnly cookie admin JWT via 'use server' Server Action (RESEARCH.md Pattern 2)
    - lib/api typed fetch wrapper (Bearer from cookie, cache:no-store, ApiError)
    - async Server Component fetching backend with Bearer cookie (Pattern 3)
    - Next.js middleware.ts route protection (admin_token cookie → redirect /login)
    - Vitest unit testing for Server Actions + util functions (NOT async RSC — Pitfall 5)
    - Playwright E2E with route interception for full auth + search flows
    - Exclude e2e/ from Vitest via vitest.config.mts exclude array
key_files:
  created:
    - apps/admin-web/lib/api.ts
    - apps/admin-web/lib/auth.ts
    - apps/admin-web/lib/format.ts
    - apps/admin-web/lib/format.test.ts
    - apps/admin-web/app/(auth)/login/page.tsx
    - apps/admin-web/app/(auth)/login/actions.ts
    - apps/admin-web/app/(auth)/login/actions.test.ts
    - apps/admin-web/app/(admin)/layout.tsx
    - apps/admin-web/components/AppSidebar.tsx
    - apps/admin-web/app/(admin)/users/page.tsx
    - apps/admin-web/app/(admin)/users/UserSearch.tsx
    - apps/admin-web/app/(admin)/ledger/[userId]/page.tsx
    - apps/admin-web/app/(admin)/ledger/page.tsx
    - apps/admin-web/app/(admin)/sender-ids/page.tsx
    - apps/admin-web/app/(admin)/refunds/page.tsx
    - apps/admin-web/app/(admin)/bundles/page.tsx
    - apps/admin-web/app/(admin)/audit/page.tsx
    - apps/admin-web/e2e/auth.spec.ts
    - apps/admin-web/e2e/user-search.spec.ts
  modified:
    - apps/admin-web/middleware.test.ts (RED placeholder → real unit tests)
    - apps/admin-web/vitest.config.mts (added exclude: ['e2e/**'])
decisions:
  - "05-08: lib/api.ts typed fetch wrapper reads BACKEND_URL from env; Bearer token injected server-side via getToken() — no token exposure to browser"
  - "05-08: formatTzs uses Math.abs + sign prefix — handles negative deltas (debits) for ledger display"
  - "05-08: Vitest excludes e2e/ directory; Playwright tests run separately via npm run test:e2e — prevents Playwright test.describe() clash with Vitest globals"
  - "05-08: Users page URL-driven search — q searchParam drives Server Component re-fetch; UserSearch.tsx client component updates URL via router.push"
  - "05-08: Placeholder pages for sender-ids/refunds/bundles/audit prevent build failure; 05-09 implements these screens"
  - "05-08: Playwright tests use route interception to mock backend — no real backend required for E2E; cookie injection simulates authenticated state"
metrics:
  duration: "~30m"
  completed: "2026-06-22"
  tasks: 2
  files: 21
---

# Phase 05 Plan 08: Admin-Web Auth Shell + User Search + Ledger Screens Summary

**One-liner:** httpOnly cookie JWT auth via Server Action, middleware route protection, 6-item admin sidebar shell, and two read screens (user search + ledger inspection) with Vitest unit tests + Playwright E2E.

---

## What Was Built

### Task 1: Auth Foundation (lib/api, lib/auth, lib/format, middleware, login)

**lib/api.ts** — typed fetch wrapper:
- Reads `BACKEND_URL` from env (default `http://localhost:8080`)
- Injects `Authorization: Bearer {token}` from the httpOnly cookie on every request
- Sets `cache: 'no-store'` on all requests (admin data must be fresh)
- Throws `ApiError(status, message)` on non-2xx responses
- `searchUsers(q, page, size)` → `GET /api/v1/admin/users`
- `getLedger(userId, page, size)` → `GET /api/v1/admin/ledger/{userId}`

**lib/auth.ts** — `getToken()` reads `admin_token` httpOnly cookie via `next/headers`

**lib/format.ts** — formatting utilities:
- `formatTzs(amount)` — `12000` → `"12,000 TZS"`, handles negatives
- `formatRelativeDate(isoDate)` — relative human-readable ("2 hours ago", "3 days ago")
- `formatDateTime(isoDate)` — locale date+time string for ledger/audit tables

**app/(auth)/login/actions.ts** — `'use server'` `adminLogin` Server Action:
- Posts to `POST /api/v1/auth/admin/login`
- On 200: sets `admin_token` cookie (httpOnly, sameSite:lax, secure:prod, maxAge:3600), redirects to `/sender-ids`
- On 401/403: returns `{ error: 'Invalid email or password. Check your credentials and try again.' }`
- On 5xx/network: returns `{ error: 'Login failed due to a server error...' }`

**app/(auth)/login/page.tsx** — `'use client'` login form:
- 400px centered Card (zinc-50 bg, zinc-200 border)
- Email (type=email, autocomplete=email) + Password (type=password, autocomplete=current-password)
- "Sign in" Button (full-width, zinc-900 accent); "Signing in…" disabled state
- Inline red-600 error area with role="alert"

**middleware.ts** (already existed, RED test made GREEN):
- Reads `admin_token` cookie; redirects to `/login` if absent
- Matcher covers all admin route patterns

### Task 2: Admin Shell + Screens + Playwright E2E

**components/AppSidebar.tsx**:
- Fixed 240px, zinc-900 background
- 6 nav items (exact UI-SPEC order): Users, Ledger, Sender IDs, Refunds, Bundle Catalog, Audit Log
- Active item: zinc-50 bg + zinc-900 text; inactive: zinc-400 text + zinc-800 hover
- Admin email + "Sign out" link at bottom

**app/(admin)/layout.tsx** — two-column shell:
- 240px AppSidebar + fluid main with 56px top bar
- zinc-50 page background, 32px top padding, 24px horizontal padding (px-6)

**app/(admin)/users/page.tsx** (async Server Component):
- Reads `q` + `page` from searchParams
- No query → "Search for a user" empty state
- Calls `searchUsers(q, page, 20)`; fetch error → amber-600 alert
- No results → "No users found" empty state
- Results → Table (Full Name, Email, Phone, Status Badge, Registered, "View Ledger" link)
- Simple prev/next pagination for multi-page results

**app/(admin)/users/UserSearch.tsx** (client component):
- Search input + "Search Users" Button
- Updates URL `?q=&page=0` via `router.push` to trigger Server Component re-fetch

**app/(admin)/ledger/[userId]/page.tsx** (async Server Component):
- Calls `getLedger(userId, 0, 50)`
- "← Back to users" link (zinc-600)
- Fetch error → amber-600 alert
- No transactions → "No transactions" empty state  
- Transactions → Table in ScrollArea (Date, Type Badge, Description, Delta colored, Reference ID)
- Credit types (GRANT/REFUND/RELEASE): green-600 delta, default badge
- Debit types (CONSUME/EXPIRE/RESERVE): red-600 delta, destructive badge

**Playwright E2E specs** (route interception — no real backend needed):
- `e2e/auth.spec.ts`: login page renders, unauthenticated redirect, successful login → /sender-ids, invalid credentials → error
- `e2e/user-search.spec.ts`: empty state, search by email shows result, sidebar shows all 6 nav items

---

## TDD Gate Compliance

| Gate | Commit | Tests | Status |
|------|--------|-------|--------|
| RED | eae16c5 | format.test.ts (7), actions.test.ts (3), middleware.test.ts (3) | PASS |
| GREEN | b98c8a6 + d4cb7bf | All 13 tests pass | PASS |
| REFACTOR | N/A — no duplication identified | — | EXEMPT |

---

## Deviations from Plan

### Auto-Fixed Issues

**1. [Rule 3 - Blocking] Vitest collected Playwright e2e/ specs causing test failure**
- **Found during:** Task 2 test run
- **Issue:** Vitest globbed `e2e/auth.spec.ts` and `e2e/user-search.spec.ts`, importing `@playwright/test` in a Vitest/jsdom context. Playwright's `test.describe()` is incompatible with Vitest globals — caused "Playwright Test did not expect test.describe() to be called here" error.
- **Fix:** Added `exclude: ['e2e/**', 'node_modules/**']` to `vitest.config.mts`
- **Files modified:** `vitest.config.mts`
- **Commit:** d4cb7bf

### Notes for 05-09

- `lib/api.ts` provides `searchUsers` and `getLedger` — 05-09 will add `approveSenderId`, `rejectSenderId`, `issueRefund`, `getBundles`, `createBundle`, `updateBundle`, `deleteBundle`, `getAuditLog`
- `components/AppSidebar.tsx` is complete — no changes needed in 05-09
- `app/(admin)/layout.tsx` is complete — no changes needed in 05-09
- Placeholder pages in `sender-ids/`, `refunds/`, `bundles/`, `audit/` are replaced by 05-09

---

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `/sender-ids` placeholder page | app/(admin)/sender-ids/page.tsx | ADMN-04 implemented in 05-09 |
| `/refunds` placeholder page | app/(admin)/refunds/page.tsx | ADMN-05 implemented in 05-09 |
| `/bundles` placeholder page | app/(admin)/bundles/page.tsx | ADMN-07 implemented in 05-09 |
| `/audit` placeholder page | app/(admin)/audit/page.tsx | ADMN-06 implemented in 05-09 |
| Ledger "Balance After" column | ledger/[userId]/page.tsx | `LedgerEntryDto` has no `balanceAfter` field — shows referenceId instead. 05-09 can add balanceAfter to LedgerEntryDto if needed |

---

## Threat Surface Scan

All security mitigations from the plan's threat_model were applied:
- T-05-21: `httpOnly: true` on `admin_token` cookie in `actions.ts` — JS cannot read token
- T-05-22: `middleware.ts` redirects unauthenticated requests; backend `hasRole("ADMIN")` is source of truth
- T-05-23: `cookies().set()` only inside `'use server'` `adminLogin` action (Pitfall 2 honored)

No new threat surface beyond plan's threat_model.

---

## Self-Check

| Check | Result |
|-------|--------|
| lib/api.ts exists | FOUND |
| lib/auth.ts exists | FOUND |
| lib/format.ts exists | FOUND |
| middleware.ts contains admin_token | FOUND |
| actions.ts contains httpOnly | FOUND |
| actions.ts posts to /api/v1/auth/admin/login | FOUND |
| app/(admin)/users/page.tsx fetches /api/v1/admin/users | FOUND |
| ledger/[userId]/page.tsx fetches /api/v1/admin/ledger | FOUND |
| AppSidebar.tsx has exactly 6 nav items | FOUND (Users/Ledger/Sender IDs/Refunds/Bundle Catalog/Audit Log) |
| npm run test -- --run: 13 tests passed | PASSED |
| npm run build: SUCCESS (11 routes) | PASSED |
| RED commit eae16c5 | FOUND |
| GREEN commits b98c8a6 + d4cb7bf | FOUND |

## Self-Check: PASSED
