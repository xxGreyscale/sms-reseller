/**
 * User search page — /(admin)/users (ADMN-02)
 *
 * Async Server Component: reads `q` from searchParams, fetches
 * /api/v1/admin/users via lib/api, renders Table + Pagination + empty states.
 * Lists all users on load (blank `q` returns all); the search box filters.
 *
 * UI-SPEC §User Search:
 *   - Search Input + "Search Users" Button (filters the list)
 *   - Table: Full Name, Email, Phone, Status (Badge), Registered, Actions
 *   - Lists all users by default; search filters by email/phone
 *   - Empty state (no results): "No users found"
 *   - Pagination: 20 rows/page
 */
import Link from 'next/link'
import { searchUsers } from '@/lib/api'
import { formatRelativeDate } from '@/lib/format'
import { Badge } from '@/src/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/src/components/ui/table'
import { Skeleton } from '@/src/components/ui/skeleton'
import { Button } from '@/src/components/ui/button'
import UserSearch from './UserSearch'

function statusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status) {
    case 'VERIFIED': return 'default'
    case 'SUSPENDED': return 'destructive'
    default: return 'secondary'
  }
}

export default async function UsersPage({
  searchParams,
}: {
  searchParams: { q?: string; page?: string }
}) {
  const q = searchParams.q ?? ''
  const page = parseInt(searchParams.page ?? '0', 10)

  // Always fetch: a blank query lists all users (backend returns all for blank q);
  // a non-blank query filters by email/phone.
  let data
  let fetchError = false

  try {
    data = await searchUsers(q, page, 20)
  } catch {
    fetchError = true
  }

  if (fetchError || !data) {
    return (
      <div>
        <div className="mb-6">
          <h1 className="text-[18px] font-semibold text-zinc-900 mb-4">Users</h1>
          <UserSearch defaultValue={q} />
        </div>
        <div
          className="w-full border border-amber-600 rounded-md p-4 text-[14px] text-zinc-900"
          role="alert"
        >
          Failed to load data. Refresh the page or contact engineering if the problem persists.
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-[18px] font-semibold text-zinc-900 mb-4">Users</h1>
        <UserSearch defaultValue={q} />
      </div>

      {data.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <h2 className="text-[18px] font-semibold text-zinc-900 mb-2">No users found</h2>
          <p className="text-[14px] text-zinc-600">
            {q.trim()
              ? 'No accounts match that query. Check the spelling or try a phone number.'
              : 'No users have registered yet.'}
          </p>
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col" className="text-[12px]">Full Name</TableHead>
                <TableHead scope="col" className="text-[12px]">Email</TableHead>
                <TableHead scope="col" className="text-[12px]">Phone</TableHead>
                <TableHead scope="col" className="text-[12px]">Status</TableHead>
                <TableHead scope="col" className="text-[12px]">Registered</TableHead>
                <TableHead scope="col" className="text-[12px]">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((user) => (
                <TableRow key={user.id} className="h-11">
                  <TableCell className="text-[14px] font-medium text-zinc-900">
                    {user.fullName}
                  </TableCell>
                  <TableCell className="text-[14px] text-zinc-700">{user.email}</TableCell>
                  <TableCell className="text-[14px] text-zinc-700">
                    {user.phone ?? '—'}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(user.verificationStatus)}>
                      {user.verificationStatus}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-[14px] text-zinc-500">
                    {formatRelativeDate(user.createdAt)}
                  </TableCell>
                  <TableCell>
                    <Button variant="outline" size="sm" asChild>
                      <Link href={`/ledger/${user.id}`} className="text-[14px]">
                        View Ledger
                      </Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {/* Pagination */}
          {data.totalPages > 1 && (
            <div className="flex justify-center mt-6 gap-2">
              {page > 0 && (
                <Button variant="outline" size="sm" asChild>
                  <Link href={`/users?q=${encodeURIComponent(q)}&page=${page - 1}`}>
                    Previous
                  </Link>
                </Button>
              )}
              <span className="text-[14px] text-zinc-600 self-center">
                Page {page + 1} of {data.totalPages}
              </span>
              {page < data.totalPages - 1 && (
                <Button variant="outline" size="sm" asChild>
                  <Link href={`/users?q=${encodeURIComponent(q)}&page=${page + 1}`}>
                    Next
                  </Link>
                </Button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
