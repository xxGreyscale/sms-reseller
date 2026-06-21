---
phase: 05-notifications-admin-analytics
plan: "09"
subsystem: admin-web
tags: [nextjs, admin-web, server-action, server-component, tdd, shadcn, tailwind, sender-id, refund, bundle, audit]
dependency_graph:
  requires: [05-08, 05-04, 05-05, 05-07]
  provides: [ADMN-04, ADMN-05, ADMN-06, ADMN-07]
  affects: []
tech_stack:
  added: []
  patterns:
    - Server Action mutation with Bearer cookie + revalidatePath (Pattern 2/3)
    - Destructive confirm Dialog gating irreversible mutations
    - Async Server Component fetching backend with Bearer cookie
    - Client component sub-tree for table row actions (approve/reject/edit/delete)
    - Inline JSON expand in audit table (client useState toggle)
key_files:
  created:
    - apps/admin-web/app/(admin)/sender-ids/actions.ts
    - apps/admin-web/app/(admin)/sender-ids/RejectDialog.tsx
    - apps/admin-web/app/(admin)/sender-ids/SenderIdTable.tsx
    - apps/admin-web/app/(admin)/sender-ids/SenderIdFilter.tsx
    - apps/admin-web/app/(admin)/refunds/actions.ts
    - apps/admin-web/app/(admin)/refunds/RefundForm.tsx
    - apps/admin-web/app/(admin)/bundles/actions.ts
    - apps/admin-web/app/(admin)/bundles/BundleDialog.tsx
    - apps/admin-web/app/(admin)/bundles/BundleTable.tsx
    - apps/admin-web/app/(admin)/audit/AuditFilters.tsx
    - apps/admin-web/app/(admin)/audit/AuditTable.tsx
    - apps/admin-web/app/(admin)/sender-ids/actions.test.ts
    - apps/admin-web/app/(admin)/bundles/actions.test.ts
    - apps/admin-web/e2e/sender-id-approval.spec.ts
  modified:
    - apps/admin-web/app/(admin)/sender-ids/page.tsx (placeholder → full implementation)
    - apps/admin-web/app/(admin)/refunds/page.tsx (placeholder → full implementation)
    - apps/admin-web/app/(admin)/bundles/page.tsx (placeholder → full implementation)
    - apps/admin-web/app/(admin)/audit/page.tsx (placeholder → full implementation)
    - apps/admin-web/src/components/ui/dialog.tsx (added DialogFooter + DialogDescription)
decisions:
  - "05-09: sender-ids page splits into Server Component (data fetch) + SenderIdTable client component — allows useState for approve/reject spinners and RejectDialog open state"
  - "05-09: refunds page is pure client (RefundForm) — no server data fetch needed; confirmation Dialog prevents double-submit (T-05-27)"
  - "05-09: bundles page uses async Server Component for initial list + BundleTable client for CRUD modals — avoids passing server-only functions to client"
  - "05-09: audit AuditTable uses useState for inline JSON expand — no Dialog needed per UI-SPEC"
  - "05-09: DialogFooter added to dialog.tsx (05-08 scaffold omitted it) — required by all confirmation Dialogs"
metrics:
  duration: "~25m"
  completed: "2026-06-22"
  tasks: 2
  files: 19
---

# Phase 05 Plan 09: Admin-Web Action Screens Summary

**One-liner:** Four admin action screens (sender-ID approval queue, manual refund, bundle catalog CRUD, audit log) with Server Actions, destructive confirmation Dialogs, Toast feedback, and revalidatePath refresh — completing the 6-screen operator panel.

---

## What Was Built

### Task 1 + Task 2: All 4 Action Screens (executed together, TDD RED→GREEN)

**TDD RED** (commit cd7482e): Wrote failing unit tests for sender-ID actions and bundle actions, plus Playwright E2E spec for sender-ID approval.

**TDD GREEN** (commit fe2f080): Implemented all 4 screens with passing tests and build.

---

### Sender-ID Approval Queue (`/(admin)/sender-ids`) — ADMN-04

**app/(admin)/sender-ids/page.tsx** (async Server Component):
- Fetches `GET /api/v1/internal/sender-ids` with status filter from searchParams (default: PENDING)
- Renders stat Card "Pending Review: N" (24px semibold, zinc-900)
- Error: amber-600 alert per UI-SPEC
- Delegates table rendering to `SenderIdTable` client component

**app/(admin)/sender-ids/SenderIdTable.tsx** (client component):
- Table columns: Requested By, Sender ID (monospace), Submitted (relative date), Status (Badge), Actions
- Pending rows: Approve (zinc-900 accent) + Reject (destructive outline) buttons with aria-labels
- Empty state (Pending + 0 items): CheckCircle icon, "Queue is clear" / "No sender ID requests are pending review."
- Approve: calls `approveSenderId` → sonner toast "Sender ID approved." / error toast

**app/(admin)/sender-ids/RejectDialog.tsx** (client component):
- shadcn Dialog: heading "Reject Sender ID", Textarea "Reason for rejection (required)", 500-char max, counter
- Submit: "Confirm Rejection" (destructive fill) — calls `rejectSenderId`
- Cancel: closes without action

**app/(admin)/sender-ids/actions.ts** (`'use server'`):
- `approveSenderId(id)`: POST `/api/v1/internal/sender-ids/{id}/approve` + revalidatePath('/sender-ids')
- `rejectSenderId(id, reason)`: validates non-empty reason, POST `/{id}/reject {reason}` + revalidatePath
- Bearer token via `getToken()` from lib/auth

**app/(admin)/sender-ids/SenderIdFilter.tsx** (client component):
- Select dropdown: Pending / Approved / Rejected / All
- Updates URL via `router.push` → triggers Server Component re-fetch

---

### Manual Refund (`/(admin)/refunds`) — ADMN-05

**app/(admin)/refunds/page.tsx**: wraps RefundForm (no server data fetch needed)

**app/(admin)/refunds/RefundForm.tsx** (client component):
- Fields: User Email or ID (Input), Amount TZS (number, min=1), Reason (Textarea, 10-500 chars)
- "Issue Refund" → client validation → opens confirmation Dialog
- Dialog: "Refund {amount} TZS to {userEmail}? This cannot be undone." — "Confirm Refund" (destructive) + Cancel
- Success: sonner toast "Refund issued successfully. {amount} TZS credited to {userEmail}." + form reset
- Backend errors mapped to UI-SPEC copy (404 → "No user found…", 422 → "Refund amount exceeds the allowed policy limit…")

**app/(admin)/refunds/actions.ts** (`'use server'`):
- `issueRefund({userIdentifier, amount, reason})`: validates inputs, POST `/api/v1/wallet/refunds`
- Maps 404/422 backend status to UI-SPEC error strings

---

### Bundle Catalog (`/(admin)/bundles`) — ADMN-07

**app/(admin)/bundles/page.tsx** (async Server Component):
- Fetches `GET /api/v1/admin/bundles`; error → amber-600 alert
- Delegates to `BundleTable` client component

**app/(admin)/bundles/BundleTable.tsx** (client component):
- Table columns: Bundle Name, SMS Count, Price TZS (formatTzs), Active (Badge), Actions
- "Add Bundle" button (top-right) opens BundleDialog in create mode
- Edit: opens BundleDialog with pre-filled fields
- Delete: destructive outline button (Trash2 icon + aria-label) → confirm Dialog "Delete {bundleName}? Users will no longer be able to purchase this bundle." → deleteBundle action
- Empty state: "No bundles configured" / "Add the first SMS bundle…"

**app/(admin)/bundles/BundleDialog.tsx** (client component):
- Add/Edit Dialog: Name, SMS Count (min=1), Price TZS (min=1), Active checkbox
- Submit: "Save Bundle" (accent fill); error shown inline

**app/(admin)/bundles/actions.ts** (`'use server'`):
- `createBundle(input)`: validates non-positive smsCount/priceTzs, POST `/api/v1/admin/bundles` + revalidatePath
- `updateBundle(id, input)`: validates, PUT `/api/v1/admin/bundles/{id}` + revalidatePath
- `deleteBundle(id)`: DELETE `/api/v1/admin/bundles/{id}` + revalidatePath

---

### Audit Log (`/(admin)/audit`) — ADMN-06

**app/(admin)/audit/page.tsx** (async Server Component):
- Fetches `GET /api/v1/admin/audit?from=&to=&actor=&page=&size=50` from searchParams
- ScrollArea wrapping AuditTable; prev/next pagination for multi-page results
- Error: amber-600 alert

**app/(admin)/audit/AuditFilters.tsx** (client component):
- From / To date Inputs + Actor Select (All / Admin mutations / System events)
- "Apply Filters" → updates URL query params → Server Component re-fetch

**app/(admin)/audit/AuditTable.tsx** (client component):
- Columns: Timestamp (full ISO), Actor, Action (monospace in zinc-100 code), Target, Details
- Details: Expand/Collapse toggle → inline `<pre>` JSON block (zinc-100 bg, 12px monospace)
- Empty state: "No audit events" / "No events match the selected filters. Widen the date range or clear filters."

---

## TDD Gate Compliance

| Gate | Commit | Tests | Status |
|------|--------|-------|--------|
| RED | cd7482e | sender-ids actions (7), bundle actions (8), Playwright E2E (3) | PASS — tests failed (actions.ts missing) |
| GREEN | fe2f080 | 28 tests total (all pass) | PASS |
| REFACTOR | N/A — code is clean | — | EXEMPT |

---

## Deviations from Plan

### Auto-Fixed Issues

**1. [Rule 2 - Missing Critical Functionality] DialogFooter missing from dialog.tsx scaffold**
- **Found during:** Build (Task 1 GREEN)
- **Issue:** `dialog.tsx` created in 05-08 did not export `DialogFooter` or `DialogDescription`. All four confirmation Dialogs (Reject sender-ID, Confirm Refund, Delete bundle) require `DialogFooter` for proper button layout.
- **Fix:** Added `DialogFooter` and `DialogDescription` components to `dialog.tsx` and its export list
- **Files modified:** `apps/admin-web/src/components/ui/dialog.tsx`
- **Commit:** fe2f080

---

## Known Stubs

None — all 4 screens are fully wired to backend APIs. The placeholder pages from 05-08 have been replaced.

---

## Threat Surface Scan

All threat mitigations from the plan's threat_model applied:
- T-05-24: Server Actions attach `Authorization: Bearer {token}` from `getToken()` (httpOnly cookie); backend `hasRole("ADMIN")` is source of truth
- T-05-25: `createBundle`/`updateBundle` validate `smsCount >= 1` and `priceTzs >= 1` client-side + in Server Action; backend `@Positive/@Min(1)` provides second layer
- T-05-26: Admin mutations call backend endpoints that record to audit log (05-07 admin-service)
- T-05-27: Refund requires confirmation Dialog before submission; backend idempotency via 03-06 mechanism

No new threat surface beyond plan's threat_model.

---

## Self-Check

| Check | Result |
|-------|--------|
| sender-ids/actions.ts exists | FOUND |
| sender-ids/actions.ts POSTs to /api/v1/internal/sender-ids | FOUND |
| sender-ids/actions.ts calls revalidatePath('/sender-ids') | FOUND |
| refunds/actions.ts POSTs to /api/v1/wallet/refunds | FOUND |
| bundles/actions.ts targets /api/v1/admin/bundles (POST/PUT/DELETE) | FOUND |
| audit/page.tsx fetches /api/v1/admin/audit | FOUND |
| Reject Dialog uses "Confirm Rejection" copy | FOUND |
| Refund Dialog uses "This cannot be undone." copy | FOUND |
| Bundle delete Dialog uses "Users will no longer be able to purchase this bundle." | FOUND |
| formatTzs used for bundle prices | FOUND |
| aria-labels on icon-only buttons | FOUND |
| npm run test -- --run: 28 tests passed | PASSED |
| npm run build: SUCCESS (11 routes) | PASSED |
| RED commit cd7482e | FOUND |
| GREEN commit fe2f080 | FOUND |

## Self-Check: PASSED
