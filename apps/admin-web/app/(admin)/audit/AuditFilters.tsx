'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useState } from 'react'
import { Button } from '@/src/components/ui/button'
import { Input } from '@/src/components/ui/input'
import { Label } from '@/src/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/src/components/ui/select'

export function AuditFilters() {
  const router = useRouter()
  const searchParams = useSearchParams()

  const [from, setFrom] = useState(searchParams.get('from') ?? '')
  const [to, setTo] = useState(searchParams.get('to') ?? '')
  const [actor, setActor] = useState(searchParams.get('actor') ?? 'ALL')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    if (actor && actor !== 'ALL') params.set('actor', actor)
    params.set('page', '0')
    router.push(`/audit?${params.toString()}`)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap gap-4 items-end mb-6">
      <div className="space-y-1">
        <Label htmlFor="audit-from" className="text-[12px]">From</Label>
        <Input
          id="audit-from"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          className="w-[160px]"
          aria-label="Filter from date"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="audit-to" className="text-[12px]">To</Label>
        <Input
          id="audit-to"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          className="w-[160px]"
          aria-label="Filter to date"
        />
      </div>
      <div className="space-y-1">
        <Label className="text-[12px]">Actor</Label>
        <Select value={actor} onValueChange={setActor}>
          <SelectTrigger className="w-[180px]" aria-label="Filter by actor type">
            <SelectValue placeholder="All" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All</SelectItem>
            <SelectItem value="ADMIN">Admin mutations</SelectItem>
            <SelectItem value="SYSTEM">System events</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Button type="submit" className="bg-zinc-900 text-zinc-50 hover:bg-zinc-700">
        Apply Filters
      </Button>
    </form>
  )
}
