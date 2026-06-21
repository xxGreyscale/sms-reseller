import { describe, it, expect } from 'vitest'
import { formatTzs, formatRelativeDate } from './format'

describe('formatTzs', () => {
  it('formats zero', () => {
    expect(formatTzs(0)).toBe('0 TZS')
  })

  it('formats small amount', () => {
    expect(formatTzs(500)).toBe('500 TZS')
  })

  it('formats 12000 as 12,000 TZS', () => {
    expect(formatTzs(12000)).toBe('12,000 TZS')
  })

  it('formats large amount with commas', () => {
    expect(formatTzs(1500000)).toBe('1,500,000 TZS')
  })

  it('formats negative amount (debit)', () => {
    expect(formatTzs(-5000)).toBe('-5,000 TZS')
  })
})

describe('formatRelativeDate', () => {
  it('returns a non-empty string for a valid ISO date', () => {
    const result = formatRelativeDate('2026-01-01T00:00:00Z')
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
  })

  it('returns a non-empty string for a recent date', () => {
    const now = new Date().toISOString()
    const result = formatRelativeDate(now)
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
  })
})
