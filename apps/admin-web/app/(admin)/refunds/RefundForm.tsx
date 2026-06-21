'use client'

import { useState, useTransition } from 'react'
import { toast } from 'sonner'
import { Button } from '@/src/components/ui/button'
import { Input } from '@/src/components/ui/input'
import { Label } from '@/src/components/ui/label'
import { Textarea } from '@/src/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/src/components/ui/dialog'
import { formatTzs } from '@/lib/format'
import { issueRefund } from './actions'

export function RefundForm() {
  const [userIdentifier, setUserIdentifier] = useState('')
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [inlineError, setInlineError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [isPending, startTransition] = useTransition()

  function handleSubmitClick(e: React.FormEvent) {
    e.preventDefault()
    setInlineError(null)

    if (!userIdentifier.trim()) { setInlineError('User email or ID is required.'); return }
    const amountNum = parseInt(amount, 10)
    if (!amount || isNaN(amountNum) || amountNum < 1) { setInlineError('Amount must be at least 1 TZS.'); return }
    if (!reason.trim() || reason.trim().length < 10) { setInlineError('Reason must be at least 10 characters.'); return }

    setConfirmOpen(true)
  }

  function handleConfirmRefund() {
    const amountNum = parseInt(amount, 10)
    startTransition(async () => {
      const result = await issueRefund({ userIdentifier, amount: amountNum, reason })
      if (result.error) {
        setInlineError(result.error)
        setConfirmOpen(false)
      } else {
        toast.success(`Refund issued successfully. ${formatTzs(amountNum)} credited to ${userIdentifier}.`)
        setUserIdentifier('')
        setAmount('')
        setReason('')
        setConfirmOpen(false)
      }
    })
  }

  const amountNum = parseInt(amount, 10)
  const displayAmount = !isNaN(amountNum) && amountNum > 0 ? formatTzs(amountNum) : `${amount} TZS`

  return (
    <>
      <form onSubmit={handleSubmitClick} className="space-y-4 max-w-lg">
        <div className="space-y-1">
          <Label htmlFor="user-identifier" className="text-[12px]">User Email or ID</Label>
          <Input
            id="user-identifier"
            type="text"
            value={userIdentifier}
            onChange={(e) => { setUserIdentifier(e.target.value); setInlineError(null) }}
            placeholder="user@example.co.tz or user-uuid"
            autoComplete="off"
            aria-describedby={inlineError ? 'refund-error' : undefined}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="amount" className="text-[12px]">Amount (TZS)</Label>
          <Input
            id="amount"
            type="number"
            min={1}
            value={amount}
            onChange={(e) => { setAmount(e.target.value); setInlineError(null) }}
            placeholder="5000"
            aria-describedby={inlineError ? 'refund-error' : undefined}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="refund-reason" className="text-[12px]">Reason</Label>
          <Textarea
            id="refund-reason"
            value={reason}
            onChange={(e) => { setReason(e.target.value.slice(0, 500)); setInlineError(null) }}
            placeholder="Explain the reason for this refund (10–500 characters)…"
            rows={4}
            maxLength={500}
            aria-describedby={inlineError ? 'refund-error' : undefined}
          />
          <p className="text-[12px] text-zinc-400 text-right">{reason.length}/500</p>
        </div>

        {inlineError && (
          <p id="refund-error" className="text-[14px] text-red-600" role="alert">
            {inlineError}
          </p>
        )}

        <Button
          type="submit"
          className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700"
          disabled={isPending}
        >
          Issue Refund
        </Button>
      </form>

      <Dialog open={confirmOpen} onOpenChange={(open) => { if (!open) setConfirmOpen(false) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm Refund</DialogTitle>
          </DialogHeader>
          <p className="text-[14px] text-zinc-700">
            Refund {displayAmount} to {userIdentifier}? This cannot be undone.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={isPending}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleConfirmRefund}
              disabled={isPending}
            >
              {isPending ? 'Processing…' : 'Confirm Refund'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
