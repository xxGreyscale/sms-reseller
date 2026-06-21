'use server'

import { getToken } from '@/lib/auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

interface RefundInput {
  userIdentifier: string // email or user ID
  amount: number
  reason: string
}

interface RefundResult {
  success?: true
  error?: string
}

export async function issueRefund(input: RefundInput): Promise<RefundResult> {
  if (!input.userIdentifier.trim()) {
    return { error: 'User email or ID is required.' }
  }
  if (!input.amount || input.amount < 1) {
    return { error: 'Amount must be at least 1 TZS.' }
  }
  if (!input.reason || input.reason.trim().length < 10) {
    return { error: 'Reason must be at least 10 characters.' }
  }

  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/wallet/refunds`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        userIdentifier: input.userIdentifier.trim(),
        amount: input.amount,
        reason: input.reason.trim(),
      }),
      cache: 'no-store',
    })

    if (!res.ok) {
      if (res.status === 404) {
        return { error: 'No user found with that email or ID. Check the input and try again.' }
      }
      if (res.status === 422) {
        return { error: 'Refund amount exceeds the allowed policy limit. Adjust the amount or contact a senior admin.' }
      }
      return { error: `Refund failed (status ${res.status}). Please try again.` }
    }

    return { success: true }
  } catch {
    return { error: 'Network error issuing refund. Please try again.' }
  }
}
