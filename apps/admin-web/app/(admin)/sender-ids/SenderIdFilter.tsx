'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/src/components/ui/select'

export function SenderIdFilter({ defaultValue }: { defaultValue: string }) {
  const router = useRouter()
  const searchParams = useSearchParams()

  function handleChange(value: string) {
    const params = new URLSearchParams(searchParams.toString())
    params.set('status', value)
    router.push(`/sender-ids?${params.toString()}`)
  }

  return (
    <Select defaultValue={defaultValue} onValueChange={handleChange}>
      <SelectTrigger className="w-[160px]" aria-label="Filter by status">
        <SelectValue placeholder="Filter by status" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="PENDING">Pending</SelectItem>
        <SelectItem value="APPROVED">Approved</SelectItem>
        <SelectItem value="REJECTED">Rejected</SelectItem>
        <SelectItem value="ALL">All</SelectItem>
      </SelectContent>
    </Select>
  )
}
