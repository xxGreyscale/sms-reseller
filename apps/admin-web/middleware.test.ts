// Wave 0 RED placeholder — made GREEN by plan 05-08
// Requirement: ADMN-01 — admin login middleware: no admin_token cookie → redirect to /login

/**
 * RED placeholder: verifies that the middleware redirects requests without an
 * admin_token cookie to /login.
 *
 * This test will FAIL because it exercises the middleware contract:
 * the assertion below is the intended post-GREEN behaviour, but importing
 * and calling Next.js middleware in a Vitest/jsdom environment requires
 * mocking the NextRequest/NextResponse pair — left as a stub for plan 05-08.
 *
 * Wave 0 purpose: ensure the Vitest harness collects this file and reports it.
 */

describe('middleware', () => {
  it('redirects to /login when admin_token cookie is absent', () => {
    // Stub — production middleware exists at middleware.ts
    // Full test implementation lands in plan 05-08 alongside the login flow.
    // For Wave 0: assert false to mark this test RED (Nyquist compliance).
    expect(false).toBe(true)
  })
})
