'use server'

import { revalidatePath } from 'next/cache'
import { getToken } from '@/lib/auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

export interface BundleInput {
  name: string
  smsCount: number
  priceTzs: number
  active: boolean
}

interface ActionResult {
  success?: true
  error?: string
}

function validateBundle(input: BundleInput): string | null {
  if (!input.name || input.name.trim().length === 0) return 'Bundle name is required.'
  if (!input.smsCount || input.smsCount < 1) return 'Please fill all required fields. Price and SMS count must be greater than zero.'
  if (!input.priceTzs || input.priceTzs < 1) return 'Please fill all required fields. Price and SMS count must be greater than zero.'
  return null
}

export async function createBundle(input: BundleInput): Promise<ActionResult> {
  const validationError = validateBundle(input)
  if (validationError) return { error: validationError }

  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/admin/bundles`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        name: input.name.trim(),
        smsCount: input.smsCount,
        priceTzs: input.priceTzs,
        active: input.active,
      }),
      cache: 'no-store',
    })

    if (!res.ok) {
      return { error: `Failed to create bundle (status ${res.status})` }
    }

    revalidatePath('/bundles')
    return { success: true }
  } catch {
    return { error: 'Network error creating bundle. Please try again.' }
  }
}

export async function updateBundle(id: string, input: BundleInput): Promise<ActionResult> {
  const validationError = validateBundle(input)
  if (validationError) return { error: validationError }

  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/admin/bundles/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        name: input.name.trim(),
        smsCount: input.smsCount,
        priceTzs: input.priceTzs,
        active: input.active,
      }),
      cache: 'no-store',
    })

    if (!res.ok) {
      return { error: `Failed to update bundle (status ${res.status})` }
    }

    revalidatePath('/bundles')
    return { success: true }
  } catch {
    return { error: 'Network error updating bundle. Please try again.' }
  }
}

export async function deleteBundle(id: string): Promise<ActionResult> {
  const token = getToken()

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/admin/bundles/${id}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      cache: 'no-store',
    })

    if (!res.ok) {
      return { error: `Failed to delete bundle (status ${res.status})` }
    }

    revalidatePath('/bundles')
    return { success: true }
  } catch {
    return { error: 'Network error deleting bundle. Please try again.' }
  }
}
