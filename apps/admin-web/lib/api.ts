/**
 * Typed fetch wrapper for backend REST calls.
 *
 * All backend calls from Server Components and Server Actions go through this module.
 * - Reads BACKEND_URL from env
 * - Attaches Bearer token from the admin_token cookie (server-side only)
 * - Sets cache: 'no-store' for all requests (admin data must be fresh)
 * - Throws typed ApiError on non-2xx responses
 */
import { getToken } from './auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Internal fetch wrapper. Attaches Bearer token automatically.
 */
async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> ?? {}),
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...options,
    headers,
    cache: 'no-store',
  })

  if (!res.ok) {
    throw new ApiError(res.status, `API error ${res.status} on ${path}`)
  }

  return res.json() as Promise<T>
}

// ─── Admin User Search (ADMN-02) ──────────────────────────────────────────────

export interface UserSummaryDto {
  id: string
  fullName: string
  email: string
  phone: string | null
  verificationStatus: string
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export async function searchUsers(
  q: string,
  page = 0,
  size = 20
): Promise<Page<UserSummaryDto>> {
  const params = new URLSearchParams({ q, page: String(page), size: String(size) })
  return apiFetch<Page<UserSummaryDto>>(`/api/v1/admin/users?${params}`)
}

// ─── Admin Ledger (ADMN-03) ───────────────────────────────────────────────────

export interface LedgerEntryDto {
  id: string
  date: string
  txnType: string
  description: string
  delta: number
  referenceId: string | null
}

export async function getLedger(
  userId: string,
  page = 0,
  size = 50
): Promise<Page<LedgerEntryDto>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<Page<LedgerEntryDto>>(`/api/v1/admin/ledger/${userId}?${params}`)
}
