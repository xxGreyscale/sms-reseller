import { Suspense } from 'react'
import { Skeleton } from '@/src/components/ui/skeleton'
import { ScrollArea } from '@/src/components/ui/scroll-area'
import { AuditFilters } from './AuditFilters'
import { AuditTable, type AuditEvent } from './AuditTable'
import { getToken } from '@/lib/auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

interface PageProps {
  searchParams: { from?: string; to?: string; actor?: string; page?: string }
}

async function fetchAuditLog(params: URLSearchParams): Promise<{ content: AuditEvent[]; totalPages: number; number: number }> {
  const token = getToken()
  const res = await fetch(`${BACKEND_URL}/api/v1/admin/audit?${params}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`API error ${res.status}`)
  return res.json()
}

async function AuditContent({ searchParams }: PageProps) {
  const params = new URLSearchParams()
  if (searchParams.from) params.set('from', searchParams.from)
  if (searchParams.to) params.set('to', searchParams.to)
  if (searchParams.actor && searchParams.actor !== 'ALL') params.set('actor', searchParams.actor)
  params.set('page', searchParams.page ?? '0')
  params.set('size', '50')

  let data: { content: AuditEvent[]; totalPages: number; number: number }
  try {
    data = await fetchAuditLog(params)
  } catch {
    return (
      <div className="w-full border border-amber-600 rounded-md p-4 text-[14px] text-zinc-900">
        Failed to load data. Refresh the page or contact engineering if the problem persists.
      </div>
    )
  }

  return (
    <>
      <ScrollArea className="h-[calc(100vh-280px)]">
        <AuditTable events={data.content} />
      </ScrollArea>
      {data.totalPages > 1 && (
        <div className="flex justify-between items-center mt-4 text-[14px] text-zinc-600">
          <span>Page {data.number + 1} of {data.totalPages}</span>
          <div className="flex gap-2">
            {data.number > 0 && (
              <a
                href={`/audit?${new URLSearchParams({ ...Object.fromEntries(params), page: String(data.number - 1) })}`}
                className="px-3 py-1 border border-zinc-200 rounded hover:bg-zinc-100"
              >
                Previous
              </a>
            )}
            {data.number < data.totalPages - 1 && (
              <a
                href={`/audit?${new URLSearchParams({ ...Object.fromEntries(params), page: String(data.number + 1) })}`}
                className="px-3 py-1 border border-zinc-200 rounded hover:bg-zinc-100"
              >
                Next
              </a>
            )}
          </div>
        </div>
      )}
    </>
  )
}

export default function AuditPage({ searchParams }: PageProps) {
  return (
    <div>
      <h1 className="text-[18px] font-semibold text-zinc-900 mb-4">Audit Log</h1>
      <Suspense>
        <AuditFilters />
      </Suspense>
      <Suspense
        fallback={
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-11 w-full rounded" />
            ))}
          </div>
        }
      >
        <AuditContent searchParams={searchParams} />
      </Suspense>
    </div>
  )
}
