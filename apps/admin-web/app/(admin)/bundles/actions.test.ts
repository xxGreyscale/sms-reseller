/**
 * Vitest unit tests for bundle catalog Server Actions (ADMN-07)
 * RED phase — tests fail until actions.ts is implemented.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('next/headers', () => ({
  cookies: () => ({
    get: (name: string) => (name === 'admin_token' ? { value: 'mock-token' } : undefined),
  }),
}))

vi.mock('next/cache', () => ({
  revalidatePath: vi.fn(),
}))

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  vi.clearAllMocks()
})

describe('createBundle', () => {
  it('POSTs to /api/v1/admin/bundles with bundle data', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'b1' }) })

    const { createBundle } = await import('./actions')
    const result = await createBundle({ name: 'Basic', smsCount: 100, priceTzs: 5000, active: true })

    expect(mockFetch).toHaveBeenCalledOnce()
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toContain('/api/v1/admin/bundles')
    expect(opts.method).toBe('POST')
    const body = JSON.parse(opts.body as string)
    expect(body.name).toBe('Basic')
    expect(body.smsCount).toBe(100)
    expect(result).toEqual({ success: true })
  })

  it('rejects when smsCount is 0', async () => {
    const { createBundle } = await import('./actions')
    const result = await createBundle({ name: 'Bad', smsCount: 0, priceTzs: 5000, active: true })

    expect(result).toHaveProperty('error')
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('rejects when priceTzs is negative', async () => {
    const { createBundle } = await import('./actions')
    const result = await createBundle({ name: 'Bad', smsCount: 100, priceTzs: -1, active: true })

    expect(result).toHaveProperty('error')
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('calls revalidatePath("/bundles") on success', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'b1' }) })
    const { revalidatePath } = await import('next/cache')

    const { createBundle } = await import('./actions')
    await createBundle({ name: 'Basic', smsCount: 100, priceTzs: 5000, active: true })

    expect(revalidatePath).toHaveBeenCalledWith('/bundles')
  })
})

describe('updateBundle', () => {
  it('PUTs to /api/v1/admin/bundles/{id} with updated data', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })

    const { updateBundle } = await import('./actions')
    const result = await updateBundle('b1', { name: 'Pro', smsCount: 500, priceTzs: 20000, active: true })

    expect(mockFetch).toHaveBeenCalledOnce()
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toContain('/api/v1/admin/bundles/b1')
    expect(opts.method).toBe('PUT')
    expect(result).toEqual({ success: true })
  })

  it('rejects non-positive smsCount on update', async () => {
    const { updateBundle } = await import('./actions')
    const result = await updateBundle('b1', { name: 'Bad', smsCount: 0, priceTzs: 1000, active: true })

    expect(result).toHaveProperty('error')
    expect(mockFetch).not.toHaveBeenCalled()
  })
})

describe('deleteBundle', () => {
  it('DELETEs /api/v1/admin/bundles/{id}', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })

    const { deleteBundle } = await import('./actions')
    const result = await deleteBundle('b1')

    expect(mockFetch).toHaveBeenCalledOnce()
    const [url, opts] = mockFetch.mock.calls[0]
    expect(url).toContain('/api/v1/admin/bundles/b1')
    expect(opts.method).toBe('DELETE')
    expect(result).toEqual({ success: true })
  })

  it('returns error on backend failure', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404, json: async () => ({}) })

    const { deleteBundle } = await import('./actions')
    const result = await deleteBundle('b999')

    expect(result).toHaveProperty('error')
  })
})
