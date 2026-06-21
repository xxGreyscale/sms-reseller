'use client'

/**
 * UserSearch — client component for the search input + button.
 *
 * Drives a URL-based search by updating the `q` query param, which causes
 * the Server Component (users/page.tsx) to re-fetch with the new query.
 */
import { useRouter, useSearchParams } from 'next/navigation'
import { useState } from 'react'
import { Input } from '@/src/components/ui/input'
import { Button } from '@/src/components/ui/button'
import { Search } from 'lucide-react'

export default function UserSearch({ defaultValue = '' }: { defaultValue?: string }) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [value, setValue] = useState(defaultValue)

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const params = new URLSearchParams(searchParams.toString())
    params.set('q', value)
    params.set('page', '0')
    router.push(`/users?${params.toString()}`)
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <Input
        type="text"
        placeholder="Search by email or phone…"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className="w-80 text-[14px]"
      />
      <Button
        type="submit"
        className="bg-zinc-900 hover:bg-zinc-800 text-zinc-50 text-[14px] h-10"
      >
        <Search className="h-4 w-4 mr-2" />
        Search Users
      </Button>
    </form>
  )
}
