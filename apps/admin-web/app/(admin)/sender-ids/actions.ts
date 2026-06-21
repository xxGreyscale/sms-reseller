'use server'

import { revalidatePath } from 'next/cache'
import { getToken } from '@/lib/auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

interface ActionResult {
  success?: true
  error?: string
}

export async function approveSenderId(id: string): Promise<ActionResult> {
  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/internal/sender-ids/${id}/approve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      cache: 'no-store',
    })

    if (!res.ok) {
      return { error: `Failed to approve sender ID (status ${res.status})` }
    }

    revalidatePath('/sender-ids')
    return { success: true }
  } catch {
    return { error: 'Network error approving sender ID. Please try again.' }
  }
}

export async function rejectSenderId(id: string, reason: string): Promise<ActionResult> {
  if (!reason || reason.trim().length === 0) {
    return { error: 'Rejection reason is required.' }
  }

  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/internal/sender-ids/${id}/reject`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ reason: reason.trim() }),
      cache: 'no-store',
    })

    if (!res.ok) {
      return { error: `Failed to reject sender ID (status ${res.status})` }
    }

    revalidatePath('/sender-ids')
    return { success: true }
  } catch {
    return { error: 'Network error rejecting sender ID. Please try again.' }
  }
}
