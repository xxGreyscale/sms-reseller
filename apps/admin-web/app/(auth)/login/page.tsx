'use client'

/**
 * Login page — /login
 *
 * Centered 400px Card with Email + Password fields and "Sign in" Button.
 * Uses the adminLogin Server Action. Middleware bounces authenticated users away.
 *
 * UI-SPEC §Login:
 *   - 400px card, vertically centered
 *   - Email (type=email, autocomplete=email)
 *   - Password (type=password, autocomplete=current-password)
 *   - "Sign in" button (full-width, accent zinc-900)
 *   - Inline error: red-600, 14px
 */
import { useState, useTransition } from 'react'
import { adminLogin } from './actions'
import { Button } from '@/src/components/ui/button'
import { Input } from '@/src/components/ui/input'
import { Label } from '@/src/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card'

export default function LoginPage() {
  const [error, setError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const formData = new FormData(e.currentTarget)
    startTransition(async () => {
      const result = await adminLogin(formData)
      if (result?.error) {
        setError(result.error)
      }
    })
  }

  return (
    <div className="min-h-screen bg-zinc-50 flex items-center justify-center">
      <Card className="w-[400px] shadow-sm border-zinc-200">
        <CardHeader>
          <CardTitle className="text-[18px] font-semibold text-zinc-900">
            Open Desk Admin
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="email" className="text-[12px] text-zinc-700">
                Email
              </Label>
              <Input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="admin@opendesk.co.tz"
                required
                disabled={isPending}
                className="text-[14px]"
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="password" className="text-[12px] text-zinc-700">
                Password
              </Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                disabled={isPending}
                className="text-[14px]"
              />
            </div>

            {error && (
              <p
                className="text-[14px] text-red-600"
                role="alert"
                aria-live="polite"
              >
                {error}
              </p>
            )}

            <Button
              type="submit"
              disabled={isPending}
              className="w-full bg-zinc-900 hover:bg-zinc-800 text-zinc-50 text-[14px] h-11"
            >
              {isPending ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
