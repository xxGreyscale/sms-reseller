'use client'

import { useState, useEffect, useTransition } from 'react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/src/components/ui/dialog'
import { Button } from '@/src/components/ui/button'
import { Input } from '@/src/components/ui/input'
import { Label } from '@/src/components/ui/label'
import { createBundle, updateBundle, type BundleInput } from './actions'

export interface Bundle {
  id: string
  name: string
  smsCount: number
  priceTzs: number
  active: boolean
}

interface BundleDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  bundle?: Bundle | null // null = create mode
}

export function BundleDialog({ open, onOpenChange, bundle }: BundleDialogProps) {
  const [name, setName] = useState('')
  const [smsCount, setSmsCount] = useState('')
  const [priceTzs, setPriceTzs] = useState('')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  useEffect(() => {
    if (bundle) {
      setName(bundle.name)
      setSmsCount(String(bundle.smsCount))
      setPriceTzs(String(bundle.priceTzs))
      setActive(bundle.active)
    } else {
      setName('')
      setSmsCount('')
      setPriceTzs('')
      setActive(true)
    }
    setError(null)
  }, [bundle, open])

  function handleClose() {
    onOpenChange(false)
  }

  function handleSubmit() {
    const smsCountNum = parseInt(smsCount, 10)
    const priceTzsNum = parseInt(priceTzs, 10)

    const input: BundleInput = {
      name,
      smsCount: isNaN(smsCountNum) ? 0 : smsCountNum,
      priceTzs: isNaN(priceTzsNum) ? 0 : priceTzsNum,
      active,
    }

    startTransition(async () => {
      const result = bundle
        ? await updateBundle(bundle.id, input)
        : await createBundle(input)

      if (result.error) {
        setError(result.error)
      } else {
        toast.success(bundle ? 'Bundle updated.' : 'Bundle created.')
        handleClose()
      }
    })
  }

  const isEdit = !!bundle

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit Bundle' : 'Add Bundle'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="bundle-name" className="text-[12px]">Name</Label>
            <Input
              id="bundle-name"
              value={name}
              onChange={(e) => { setName(e.target.value); setError(null) }}
              placeholder="e.g. Basic Pack"
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="bundle-sms-count" className="text-[12px]">SMS Count</Label>
            <Input
              id="bundle-sms-count"
              type="number"
              min={1}
              value={smsCount}
              onChange={(e) => { setSmsCount(e.target.value); setError(null) }}
              placeholder="100"
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="bundle-price" className="text-[12px]">Price (TZS)</Label>
            <Input
              id="bundle-price"
              type="number"
              min={1}
              value={priceTzs}
              onChange={(e) => { setPriceTzs(e.target.value); setError(null) }}
              placeholder="5000"
            />
          </div>

          <div className="flex items-center gap-2">
            <input
              id="bundle-active"
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              className="h-4 w-4 rounded border-zinc-300"
            />
            <Label htmlFor="bundle-active" className="text-[14px] cursor-pointer">Active</Label>
          </div>

          {error && (
            <p className="text-[14px] text-red-600" role="alert">
              {error}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isPending}>
            Cancel
          </Button>
          <Button
            className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700"
            onClick={handleSubmit}
            disabled={isPending}
          >
            {isPending ? 'Saving…' : 'Save Bundle'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
