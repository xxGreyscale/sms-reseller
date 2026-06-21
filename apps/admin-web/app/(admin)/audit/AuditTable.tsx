'use client'

import { useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/src/components/ui/table'

export interface AuditEvent {
  id: string
  timestamp: string
  actor: string
  action: string
  target: string | null
  details: Record<string, unknown> | null
}

interface AuditTableProps {
  events: AuditEvent[]
}

export function AuditTable({ events }: AuditTableProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null)

  if (events.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <h2 className="text-[18px] font-semibold text-zinc-900">No audit events</h2>
        <p className="text-[14px] text-zinc-600 mt-1">
          No events match the selected filters. Widen the date range or clear filters.
        </p>
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead scope="col">Timestamp</TableHead>
          <TableHead scope="col">Actor</TableHead>
          <TableHead scope="col">Action</TableHead>
          <TableHead scope="col">Target</TableHead>
          <TableHead scope="col">Details</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {events.map((event) => (
          <>
            <TableRow key={event.id} className="min-h-[44px]">
              <TableCell className="text-[14px] text-zinc-600 whitespace-nowrap">
                {event.timestamp}
              </TableCell>
              <TableCell className="text-[14px]">{event.actor}</TableCell>
              <TableCell>
                <code className="font-mono text-[13px] bg-zinc-100 px-1 py-0.5 rounded">
                  {event.action}
                </code>
              </TableCell>
              <TableCell className="text-[14px] text-zinc-600">{event.target ?? '—'}</TableCell>
              <TableCell>
                {event.details ? (
                  <button
                    className="text-[12px] text-zinc-500 underline hover:text-zinc-900"
                    onClick={() => setExpandedId(expandedId === event.id ? null : event.id)}
                    aria-label={expandedId === event.id ? 'Collapse details' : 'Expand details'}
                  >
                    {expandedId === event.id ? 'Collapse' : 'Expand'}
                  </button>
                ) : (
                  <span className="text-[12px] text-zinc-400">—</span>
                )}
              </TableCell>
            </TableRow>
            {expandedId === event.id && event.details && (
              <TableRow key={`${event.id}-details`}>
                <TableCell colSpan={5} className="p-0">
                  <pre className="text-[12px] font-mono bg-zinc-100 p-4 overflow-x-auto">
                    {JSON.stringify(event.details, null, 2)}
                  </pre>
                </TableCell>
              </TableRow>
            )}
          </>
        ))}
      </TableBody>
    </Table>
  )
}
