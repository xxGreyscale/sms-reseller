/**
 * Vitest unit tests for sender-ID Server Actions (ADMN-04)
 * RED phase — tests fail until actions.ts is implemented.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock next/headers (used by getToken via lib/auth)
vi.mock('next/headers', () => ({
  cookies: () => ({
    get: (name: string) => (name === 'admin_token' ? { value: 'mock-token' } : undefined),
  }),
}))

// Mock next/cache (revalidatePath)
vi.mock('next/cache', () => ({
  revalidatePath: vi.fn(),
}))

// We mock global fetch
const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  vi.clearAllMocks()
})

describe('approveSenderId', () => {
  it('POSTs to /api/v1/internal/sender-ids/{id}/approve with Bearer token', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })

    const { approveSenderId } = await import('./actions')
    const result = await approveSenderId('sender-123')

    expect(mockFetch).toHaveBeenCalledOnce()
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toContain('/api/v1/internal/sender-ids/sender-123/approve')
    expect(opts.method).toBe('POST')
    expect(opts.headers?.Authorization).toContain('Bearer')
    expect(result).toEqual({ success: true })
  })

  it('returns error object when backend returns non-2xx', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404, json: async () => ({}) })

    const { approveSenderId } = await import('./actions')
    const result = await approveSenderId('sender-999')

    expect(result).toHaveProperty('error')
  })

  it('calls revalidatePath("/sender-ids") on success', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })
    const { revalidatePath } = await import('next/cache')

    const { approveSenderId } = await import('./actions')
    await approveSenderId('sender-123')

    expect(revalidatePath).toHaveBeenCalledWith('/sender-ids')
  })
})

describe('rejectSenderId', () => {
  it('POSTs to /api/v1/internal/sender-ids/{id}/reject with reason in body', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })

    const { rejectSenderId } = await import('./actions')
    const result = await rejectSenderId('sender-456', 'Inappropriate name')

    expect(mockFetch).toHaveBeenCalledOnce()
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toContain('/api/v1/internal/sender-ids/sender-456/reject')
    expect(opts.method).toBe('POST')
    const body = JSON.parse(opts.body as string)
    expect(body.reason).toBe('Inappropriate name')
    expect(result).toEqual({ success: true })
  })

  it('returns error when reason is empty', async () => {
    const { rejectSenderId } = await import('./actions')
    const result = await rejectSenderId('sender-456', '')

    expect(result).toHaveProperty('error')
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('returns error when backend returns non-2xx', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 400, json: async () => ({}) })

    const { rejectSenderId } = await import('./actions')
    const result = await rejectSenderId('sender-456', 'Bad reason')

    expect(result).toHaveProperty('error')
  })

  it('calls revalidatePath("/sender-ids") on success', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })
    const { revalidatePath } = await import('next/cache')

    const { rejectSenderId } = await import('./actions')
    await rejectSenderId('sender-456', 'Bad name')

    expect(revalidatePath).toHaveBeenCalledWith('/sender-ids')
  })
})
