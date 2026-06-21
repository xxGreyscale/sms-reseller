/**
 * Formatting utilities for the admin-web panel.
 * All amounts in TZS (integer, stored as BIGINT in PostgreSQL).
 */

/**
 * Format a TZS integer amount with comma grouping and "TZS" suffix.
 * Examples:
 *   formatTzs(12000)   → "12,000 TZS"
 *   formatTzs(-5000)   → "-5,000 TZS"
 *   formatTzs(0)       → "0 TZS"
 */
export function formatTzs(amount: number): string {
  const formatted = Math.abs(amount).toLocaleString('en-US')
  const sign = amount < 0 ? '-' : ''
  return `${sign}${formatted} TZS`
}

/**
 * Format an ISO date string as a relative human-readable string.
 * Examples:
 *   "2 hours ago"
 *   "3 days ago"
 *   "Jan 15, 2026"
 */
export function formatRelativeDate(isoDate: string): string {
  const date = new Date(isoDate)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSeconds < 60) return 'just now'
  if (diffMinutes < 60) return `${diffMinutes} minute${diffMinutes === 1 ? '' : 's'} ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours === 1 ? '' : 's'} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays === 1 ? '' : 's'} ago`

  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

/**
 * Format an ISO date string for display in the ledger/audit tables.
 * Returns a locale date + time string.
 */
export function formatDateTime(isoDate: string): string {
  const date = new Date(isoDate)
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
