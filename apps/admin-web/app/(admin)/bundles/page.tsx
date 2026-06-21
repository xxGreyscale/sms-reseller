import { Suspense } from 'react'
import { Skeleton } from '@/src/components/ui/skeleton'
import { BundleTable } from './BundleTable'
import { getToken } from '@/lib/auth'
import type { Bundle } from './BundleDialog'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

async function fetchBundles(): Promise<Bundle[]> {
  const token = getToken()
  const res = await fetch(`${BACKEND_URL}/api/v1/admin/bundles`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`API error ${res.status}`)
  return res.json()
}

async function BundlesContent() {
  let bundles: Bundle[]
  try {
    bundles = await fetchBundles()
  } catch {
    return (
      <div className="w-full border border-amber-600 rounded-md p-4 text-[14px] text-zinc-900">
        Failed to load data. Refresh the page or contact engineering if the problem persists.
      </div>
    )
  }
  return <BundleTable bundles={bundles} />
}

export default function BundlesPage() {
  return (
    <div>
      <h1 className="text-[18px] font-semibold text-zinc-900 mb-6">Bundle Catalog</h1>
      <Suspense
        fallback={
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-11 w-full rounded" />
            ))}
          </div>
        }
      >
        <BundlesContent />
      </Suspense>
    </div>
  )
}
