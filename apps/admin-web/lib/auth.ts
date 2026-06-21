/**
 * Auth helper — reads the admin JWT from the httpOnly cookie.
 * Only callable in Server Components, Server Actions, and Route Handlers.
 */
import { cookies } from 'next/headers'

/**
 * Get the admin JWT token from the httpOnly cookie.
 * Returns the token string or undefined if not set.
 */
export function getToken(): string | undefined {
  return cookies().get('admin_token')?.value
}
