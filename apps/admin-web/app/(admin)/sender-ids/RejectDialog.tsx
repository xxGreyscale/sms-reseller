'use client'

import { useState, useTransition } from 'react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/src/components/ui/dialog'
import { Button } from '@/src/components/ui/button'
import { Textarea } from '@/src/components/ui/textarea'
import { Label } from '@/src/components/ui/label'
import { rejectSenderId } from './actions'

interface RejectDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  senderId: string
  senderIdValue: string
}

export function RejectDialog({ open, onOpenChange, senderId, senderIdValue }: RejectDialogProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  function handleClose() {
    setReason('')
    setError(null)
    onOpenChange(false)
  }

  function handleSubmit() {
    if (!reason.trim()) {
      setError('Rejection reason is required.')
      return
    }
    startTransition(async () => {
      const result = await rejectSenderId(senderId, reason)
      if (result.error) {
        setError(result.error)
      } else {
        toast.success('Sender ID rejected.')
        handleClose()
      }
    })
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject Sender ID</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <p className="text-[14px] text-zinc-600">
            Reject <span className="font-mono font-semibold">{senderIdValue}</span>? Enter a reason to send to the user.
          </p>
          <div className="space-y-1">
            <Label htmlFor="reject-reason" className="text-[12px]">
              Reason for rejection (required)
            </Label>
            <Textarea
              id="reject-reason"
              value={reason}
              onChange={(e) => {
                setReason(e.target.value.slice(0, 500))
                setError(null)
              }}
              placeholder="Explain why this sender ID is being rejected…"
              rows={4}
              maxLength={500}
              aria-describedby={error ? 'reject-error' : undefined}
            />
            <p className="text-[12px] text-zinc-400 text-right">{reason.length}/500</p>
            {error && (
              <p id="reject-error" className="text-[14px] text-red-600" role="alert">
                {error}
              </p>
            )}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isPending}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={handleSubmit}
            disabled={isPending}
            aria-label="Confirm rejection of this sender ID"
          >
            {isPending ? 'Rejecting…' : 'Confirm Rejection'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
