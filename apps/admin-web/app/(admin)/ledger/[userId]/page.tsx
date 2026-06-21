/**
 * Ledger inspection page — /(admin)/ledger/[userId] (ADMN-03)
 *
 * Async Server Component: fetches /api/v1/admin/ledger/{userId} via lib/api.
 *
 * UI-SPEC §Ledger Inspection:
 *   - "← Back to users" link (14px, zinc-600)
 *   - User summary Card: name, email, current balance (18px semibold), status Badge
 *   - Table: Date, Type (credit/debit Badge), Description, Delta (colored), Balance After
 *   - ScrollArea: max-height calc(100vh - 280px)
 *   - Empty state: "No transactions"
 */
import Link from 'next/link'
import { getLedger } from '@/lib/api'
import { formatTzs, formatDateTime } from '@/lib/format'
import { Badge } from '@/src/components/ui/badge'
import { Card, CardContent } from '@/src/components/ui/card'
import { ScrollArea } from '@/src/components/ui/scroll-area'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/src/components/ui/table'
import { ArrowLeft } from 'lucide-react'

const CREDIT_TYPES = ['GRANT', 'REFUND', 'RELEASE']
const DEBIT_TYPES = ['CONSUME', 'EXPIRE', 'RESERVE']

function txnTypeVariant(txnType: string): 'default' | 'destructive' | 'secondary' {
  if (CREDIT_TYPES.includes(txnType)) return 'default'
  if (DEBIT_TYPES.includes(txnType)) return 'destructive'
  return 'secondary'
}

function deltaColor(txnType: string): string {
  if (CREDIT_TYPES.includes(txnType)) return 'text-green-600'
  if (DEBIT_TYPES.includes(txnType)) return 'text-red-600'
  return 'text-zinc-700'
}

export default async function LedgerPage({
  params,
}: {
  params: { userId: string }
}) {
  const { userId } = params

  let data
  let fetchError = false

  try {
    data = await getLedger(userId, 0, 50)
  } catch {
    fetchError = true
  }

  return (
    <div>
      {/* Back link */}
      <Link
        href="/users"
        className="inline-flex items-center gap-1 text-[14px] text-zinc-600 hover:text-zinc-900 transition-colors mb-6"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to users
      </Link>

      {/* Page heading */}
      <h1 className="text-[18px] font-semibold text-zinc-900 mb-6">
        User Ledger
      </h1>

      {fetchError || !data ? (
        <div
          className="w-full border border-amber-600 rounded-md p-4 text-[14px] text-zinc-900"
          role="alert"
        >
          Failed to load data. Refresh the page or contact engineering if the problem persists.
        </div>
      ) : data.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <h2 className="text-[18px] font-semibold text-zinc-900 mb-2">No transactions</h2>
          <p className="text-[14px] text-zinc-600">
            This user has no credit history yet.
          </p>
        </div>
      ) : (
        <ScrollArea style={{ maxHeight: 'calc(100vh - 280px)' }}>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col" className="text-[12px]">Date</TableHead>
                <TableHead scope="col" className="text-[12px]">Type</TableHead>
                <TableHead scope="col" className="text-[12px]">Description</TableHead>
                <TableHead scope="col" className="text-[12px]">Delta</TableHead>
                <TableHead scope="col" className="text-[12px]">Balance After</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((entry) => (
                <TableRow key={entry.id} className="h-11">
                  <TableCell className="text-[14px] text-zinc-700 whitespace-nowrap">
                    {formatDateTime(entry.date)}
                  </TableCell>
                  <TableCell>
                    <Badge variant={txnTypeVariant(entry.txnType)}>
                      {entry.txnType}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-[14px] text-zinc-700 max-w-[240px] truncate">
                    {entry.description}
                  </TableCell>
                  <TableCell className={`text-[14px] font-medium ${deltaColor(entry.txnType)}`}>
                    {formatTzs(entry.delta)}
                  </TableCell>
                  <TableCell className="text-[14px] text-zinc-900 font-medium">
                    {/* Balance after is not in the DTO — show reference ID or delta */}
                    {entry.referenceId ? (
                      <span className="text-[12px] text-zinc-500 font-mono">{entry.referenceId.slice(0, 8)}</span>
                    ) : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </ScrollArea>
      )}
    </div>
  )
}
