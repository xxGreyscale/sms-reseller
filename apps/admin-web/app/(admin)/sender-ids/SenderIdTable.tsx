'use client'

import { useState, useTransition } from 'react'
import { toast } from 'sonner'
import { CheckCircle } from 'lucide-react'
import { Button } from '@/src/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/src/components/ui/table'
import { Badge } from '@/src/components/ui/badge'
import { RejectDialog } from './RejectDialog'
import { approveSenderId } from './actions'
import { formatRelativeDate } from '@/lib/format'

export interface SenderIdRequest {
  id: string
  requestedByEmail: string
  senderIdValue: string
  submittedAt: string
  status: string
}

interface SenderIdTableProps {
  requests: SenderIdRequest[]
  statusFilter: string
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; className: string }> = {
    PENDING: { label: 'Pending', className: 'bg-amber-100 text-amber-700 border-amber-200' },
    APPROVED: { label: 'Approved', className: 'bg-green-100 text-green-700 border-green-200' },
    REJECTED: { label: 'Rejected', className: 'bg-red-100 text-red-700 border-red-200' },
  }
  const entry = map[status] ?? { label: status, className: '' }
  return (
    <Badge variant="outline" className={entry.className}>
      {entry.label}
    </Badge>
  )
}

export function SenderIdTable({ requests, statusFilter }: SenderIdTableProps) {
  const [rejectTarget, setRejectTarget] = useState<SenderIdRequest | null>(null)
  const [approvingId, setApprovingId] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const pending = requests.filter((r) => statusFilter === 'ALL' || r.status === statusFilter)

  if (pending.length === 0 && statusFilter === 'PENDING') {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <CheckCircle className="h-10 w-10 text-green-600 mb-3" />
        <h2 className="text-[18px] font-semibold text-zinc-900">Queue is clear</h2>
        <p className="text-[14px] text-zinc-600 mt-1">No sender ID requests are pending review.</p>
      </div>
    )
  }

  if (pending.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <h2 className="text-[18px] font-semibold text-zinc-900">No results</h2>
        <p className="text-[14px] text-zinc-600 mt-1">No sender ID requests match the selected filter.</p>
      </div>
    )
  }

  function handleApprove(req: SenderIdRequest) {
    setApprovingId(req.id)
    startTransition(async () => {
      const result = await approveSenderId(req.id)
      if (result.error) {
        toast.error(result.error)
      } else {
        toast.success('Sender ID approved.')
      }
      setApprovingId(null)
    })
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Requested By</TableHead>
            <TableHead scope="col">Sender ID</TableHead>
            <TableHead scope="col">Submitted</TableHead>
            <TableHead scope="col">Status</TableHead>
            <TableHead scope="col">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {pending.map((req) => (
            <TableRow key={req.id} className="min-h-[44px]">
              <TableCell className="text-[14px]">{req.requestedByEmail}</TableCell>
              <TableCell className="font-mono text-[14px]">{req.senderIdValue}</TableCell>
              <TableCell className="text-[14px] text-zinc-600">
                {formatRelativeDate(req.submittedAt)}
              </TableCell>
              <TableCell>
                <StatusBadge status={req.status} />
              </TableCell>
              <TableCell>
                {req.status === 'PENDING' && (
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700"
                      onClick={() => handleApprove(req)}
                      disabled={approvingId === req.id || isPending}
                      aria-label={`Approve sender ID ${req.senderIdValue}`}
                    >
                      {approvingId === req.id ? 'Approving…' : 'Approve'}
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      className="border border-red-600 bg-transparent text-red-600 hover:bg-red-50"
                      onClick={() => setRejectTarget(req)}
                      disabled={isPending}
                      aria-label={`Reject sender ID ${req.senderIdValue}`}
                    >
                      Reject
                    </Button>
                  </div>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <RejectDialog
        open={rejectTarget !== null}
        onOpenChange={(open) => { if (!open) setRejectTarget(null) }}
        senderId={rejectTarget?.id ?? ''}
        senderIdValue={rejectTarget?.senderIdValue ?? ''}
      />
    </>
  )
}
