import { Suspense } from 'react'
import { Card, CardContent } from '@/src/components/ui/card'
import { Skeleton } from '@/src/components/ui/skeleton'
import { SenderIdTable, type SenderIdRequest } from './SenderIdTable'
import { SenderIdFilter } from './SenderIdFilter'
import { getToken } from '@/lib/auth'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

async function fetchSenderIds(status: string): Promise<{ content: SenderIdRequest[]; totalElements: number }> {
  const token = getToken()
  const params = new URLSearchParams()
  if (status && status !== 'ALL') params.set('status', status)
  params.set('page', '0')
  params.set('size', '50')

  const res = await fetch(`${BACKEND_URL}/api/v1/internal/sender-ids?${params}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: 'no-store',
  })

  if (!res.ok) {
    throw new Error(`API error ${res.status}`)
  }

  return res.json()
}

interface PageProps {
  searchParams: { status?: string }
}

async function SenderIdContent({ status }: { status: string }) {
  let data: { content: SenderIdRequest[]; totalElements: number }
  try {
    data = await fetchSenderIds(status)
  } catch {
    return (
      <div className="w-full border border-amber-600 rounded-md p-4 text-[14px] text-zinc-900">
        Failed to load data. Refresh the page or contact engineering if the problem persists.
      </div>
    )
  }

  const pendingCount = data.content.filter((r) => r.status === 'REQUESTED').length

  return (
    <>
      <div className="mb-4">
        <Card className="inline-block">
          <CardContent className="px-6 py-3">
            <span className="text-[24px] font-semibold text-zinc-900">
              Pending Review: {status === 'REQUESTED' || status === 'ALL' ? pendingCount : data.content.filter(r => r.status === 'REQUESTED').length}
            </span>
          </CardContent>
        </Card>
      </div>
      <SenderIdTable requests={data.content} statusFilter={status} />
    </>
  )
}

export default async function SenderIdsPage({ searchParams }: PageProps) {
  const status = searchParams.status ?? 'REQUESTED'

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-[18px] font-semibold text-zinc-900">Sender IDs</h1>
        <Suspense>
          <SenderIdFilter defaultValue={status} />
        </Suspense>
      </div>
      <Suspense
        fallback={
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-11 w-full rounded" />
            ))}
          </div>
        }
      >
        <SenderIdContent status={status} />
      </Suspense>
    </div>
  )
}
