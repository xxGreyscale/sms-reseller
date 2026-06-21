'use client'

import { useState, useTransition } from 'react'
import { toast } from 'sonner'
import { Pencil, Trash2 } from 'lucide-react'
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
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/src/components/ui/dialog'
import { BundleDialog, type Bundle } from './BundleDialog'
import { deleteBundle } from './actions'
import { formatTzs } from '@/lib/format'

interface BundleTableProps {
  bundles: Bundle[]
}

export function BundleTable({ bundles }: BundleTableProps) {
  const [editTarget, setEditTarget] = useState<Bundle | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Bundle | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [isPending, startTransition] = useTransition()

  function handleDelete() {
    if (!deleteTarget) return
    startTransition(async () => {
      const result = await deleteBundle(deleteTarget.id)
      if (result.error) {
        toast.error(result.error)
      } else {
        toast.success('Bundle deleted.')
      }
      setDeleteTarget(null)
    })
  }

  if (bundles.length === 0 && !addOpen) {
    return (
      <>
        <div className="flex justify-end mb-4">
          <Button
            className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700"
            onClick={() => setAddOpen(true)}
          >
            Add Bundle
          </Button>
        </div>
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <h2 className="text-[18px] font-semibold text-zinc-900">No bundles configured</h2>
          <p className="text-[14px] text-zinc-600 mt-1">Add the first SMS bundle to make it available for purchase.</p>
        </div>
        <BundleDialog open={addOpen} onOpenChange={setAddOpen} bundle={null} />
      </>
    )
  }

  return (
    <>
      <div className="flex justify-end mb-4">
        <Button
          className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700"
          onClick={() => setAddOpen(true)}
        >
          Add Bundle
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead scope="col">Bundle Name</TableHead>
            <TableHead scope="col">SMS Count</TableHead>
            <TableHead scope="col">Price TZS</TableHead>
            <TableHead scope="col">Active</TableHead>
            <TableHead scope="col">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {bundles.map((bundle) => (
            <TableRow key={bundle.id} className="min-h-[44px]">
              <TableCell className="text-[14px] font-medium">{bundle.name}</TableCell>
              <TableCell className="text-[14px]">{bundle.smsCount.toLocaleString()}</TableCell>
              <TableCell className="text-[14px]">{formatTzs(bundle.priceTzs)}</TableCell>
              <TableCell>
                <Badge
                  variant="outline"
                  className={bundle.active
                    ? 'bg-green-100 text-green-700 border-green-200'
                    : 'bg-zinc-100 text-zinc-500 border-zinc-200'}
                >
                  {bundle.active ? 'Active' : 'Inactive'}
                </Badge>
              </TableCell>
              <TableCell>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setEditTarget(bundle)}
                    aria-label={`Edit bundle ${bundle.name}`}
                  >
                    <Pencil className="h-4 w-4 mr-1" />
                    Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="border-red-200 text-red-600 hover:bg-red-50"
                    onClick={() => setDeleteTarget(bundle)}
                    disabled={isPending}
                    aria-label={`Delete bundle ${bundle.name}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <BundleDialog open={addOpen} onOpenChange={setAddOpen} bundle={null} />
      <BundleDialog open={editTarget !== null} onOpenChange={(open) => { if (!open) setEditTarget(null) }} bundle={editTarget} />

      {/* Delete confirmation */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Bundle</DialogTitle>
          </DialogHeader>
          <p className="text-[14px] text-zinc-700">
            Delete {deleteTarget?.name}? Users will no longer be able to purchase this bundle.
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)} disabled={isPending}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={isPending}
            >
              {isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
