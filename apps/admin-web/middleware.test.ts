/**
 * Unit tests for middleware.ts — ADMN-01 route protection.
 *
 * Strategy: We cannot use the full Next.js edge runtime in Vitest/jsdom.
 * Instead, we construct a lightweight mock of NextRequest and call the
 * middleware function directly, asserting on the returned NextResponse.
 */
import { describe, it, expect, vi } from 'vitest'
import { middleware } from './middleware'

// Minimal NextRequest mock — provides cookies.get() and a url property
function makeRequest(hasCookie: boolean, path = '/users'): any {
  return {
    url: `http://localhost:3000${path}`,
    cookies: {
      get: (name: string) => {
        if (name === 'admin_token' && hasCookie) {
          return { value: 'mock-jwt-token' }
        }
        return undefined
      },
    },
    nextUrl: new URL(`http://localhost:3000${path}`),
  }
}

describe('middleware', () => {
  it('redirects to /login when admin_token cookie is absent', () => {
    const req = makeRequest(false, '/users')
    const response = middleware(req)
    // NextResponse.redirect sets a Location header or is a redirect response
    expect(response).toBeDefined()
    // The redirect response has a non-ok status (3xx) and Location header
    const location = response.headers?.get('Location') ?? response.headers?.get('location')
    expect(location).toContain('/login')
  })

  it('allows request through when admin_token cookie is present', () => {
    const req = makeRequest(true, '/users')
    const response = middleware(req)
    expect(response).toBeDefined()
    // NextResponse.next() has status 200
    expect(response.status).toBe(200)
  })

  it('redirects to /login for ledger route without cookie', () => {
    const req = makeRequest(false, '/ledger/some-uuid')
    const response = middleware(req)
    const location = response.headers?.get('Location') ?? response.headers?.get('location')
    expect(location).toContain('/login')
  })
})
