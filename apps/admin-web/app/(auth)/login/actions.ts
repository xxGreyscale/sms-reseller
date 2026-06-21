'use server'

/**
 * Server Action: adminLogin
 *
 * Posts credentials to the identity-service admin login endpoint.
 * On success: sets an httpOnly admin_token cookie and redirects to /sender-ids.
 * On failure: returns an error object with the UI-SPEC copy string.
 *
 * Security: httpOnly prevents JS access (T-05-21).
 * Cookie mutation only in Server Action per Pitfall 2 (RESEARCH.md).
 */
import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

export async function adminLogin(
  formData: FormData
): Promise<{ error: string } | undefined> {
  const email = formData.get('email') as string
  const password = formData.get('password') as string

  let res: Response
  try {
    res = await fetch(`${BACKEND_URL}/api/v1/auth/admin/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
  } catch {
    return { error: 'Login failed due to a server error. Please try again in a moment.' }
  }

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      return { error: 'Invalid email or password. Check your credentials and try again.' }
    }
    return { error: 'Login failed due to a server error. Please try again in a moment.' }
  }

  const { accessToken } = await res.json()

  cookies().set('admin_token', accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 60 * 60, // 60 minutes — matches backend JWT TTL
    path: '/',
  })

  redirect('/sender-ids')
}
