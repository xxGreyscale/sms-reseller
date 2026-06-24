/**
 * Unit tests for the adminLogin Server Action.
 *
 * We cannot call the Server Action directly in Vitest (it relies on next/headers cookies()
 * which requires the Next.js SSR runtime). Instead we test the underlying logic by mocking
 * fetch and the cookies module, and verifying the expected branch outcomes.
 *
 * Pattern: import the action function after setting up the mocks, then call it.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Mock next/headers before any imports that use it
vi.mock('next/headers', () => ({
  cookies: vi.fn(() => ({
    set: vi.fn(),
    get: vi.fn(),
  })),
}))

// Mock next/navigation redirect
vi.mock('next/navigation', () => ({
  redirect: vi.fn((url: string) => { throw new Error(`NEXT_REDIRECT:${url}`) }),
}))

describe('adminLogin Server Action', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sets httpOnly cookie and redirects to /sender-ids on successful login', async () => {
    const mockCookieSet = vi.fn()
    const { cookies } = await import('next/headers')
    vi.mocked(cookies).mockReturnValue({
      set: mockCookieSet,
      get: vi.fn(),
    } as any)

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ accessToken: 'test-jwt-token' }),
    } as any)

    const { adminLogin } = await import('./actions')

    const formData = new FormData()
    formData.set('email', 'admin@smsreseller.co.tz')
    formData.set('password', 'secret')

    await expect(adminLogin(formData)).rejects.toThrow('NEXT_REDIRECT:/sender-ids')

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/admin/login'),
      expect.objectContaining({ method: 'POST' })
    )
    expect(mockCookieSet).toHaveBeenCalledWith(
      'admin_token',
      'test-jwt-token',
      expect.objectContaining({ httpOnly: true })
    )
  })

  it('returns error message on 401 unauthorized', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
    } as any)

    const { adminLogin } = await import('./actions')

    const formData = new FormData()
    formData.set('email', 'admin@smsreseller.co.tz')
    formData.set('password', 'wrong')

    const result = await adminLogin(formData)
    expect(result).toEqual({
      error: 'Invalid email or password. Check your credentials and try again.',
    })
  })

  it('returns server error message on 500', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
    } as any)

    const { adminLogin } = await import('./actions')

    const formData = new FormData()
    formData.set('email', 'admin@smsreseller.co.tz')
    formData.set('password', 'secret')

    const result = await adminLogin(formData)
    expect(result).toEqual({
      error: 'Login failed due to a server error. Please try again in a moment.',
    })
  })
})
