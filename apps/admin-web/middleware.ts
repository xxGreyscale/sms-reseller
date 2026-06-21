import { NextRequest, NextResponse } from 'next/server'

/**
 * Admin route protection middleware.
 *
 * Reads the admin_token httpOnly cookie set by the login Server Action.
 * If missing, redirects unauthenticated requests to /login.
 *
 * For MVP: cookie presence check only. Full JWT decode at edge is optional
 * — backend returns 401 on expired tokens, which admin-web can handle with
 * a redirect to /login in the Server Component error boundary.
 *
 * Made GREEN by plan 05-08 (middleware.test.ts RED placeholder tested here).
 */
export function middleware(request: NextRequest) {
  const token = request.cookies.get('admin_token')?.value

  // No cookie → redirect to login
  if (!token) {
    return NextResponse.redirect(new URL('/login', request.url))
  }

  return NextResponse.next()
}

// Protect all routes under /(admin) group and /admin/* paths
export const config = {
  matcher: ['/(admin)/:path*', '/users/:path*', '/ledger/:path*', '/bundles/:path*', '/audit/:path*', '/sender-ids/:path*', '/refunds/:path*'],
}
