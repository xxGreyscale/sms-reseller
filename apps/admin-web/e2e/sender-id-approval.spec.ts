/**
 * Playwright E2E: Sender-ID approval queue (ADMN-04)
 *
 * Uses route interception to mock backend — no real backend required.
 */
import { test, expect } from '@playwright/test'

const PENDING_ITEM = {
  id: 'sid-001',
  requestedByEmail: 'org@example.co.tz',
  senderIdValue: 'MYORG',
  submittedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  status: 'PENDING',
}

test.describe('Sender-ID approval queue', () => {
  test.beforeEach(async ({ page }) => {
    // Inject auth cookie so middleware passes
    await page.context().addCookies([
      {
        name: 'admin_token',
        value: 'mock-admin-jwt',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
        sameSite: 'Lax',
      },
    ])
  })

  test('shows pending sender-ID and stat card count', async ({ page }) => {
    await page.route('**/api/v1/internal/sender-ids**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [PENDING_ITEM], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
      })
    })

    await page.goto('/sender-ids')

    await expect(page.getByText('Pending Review:')).toBeVisible()
    await expect(page.getByText('MYORG')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Approve' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Reject' })).toBeVisible()
  })

  test('approving a pending request calls approve endpoint', async ({ page }) => {
    let approveCallMade = false

    await page.route('**/api/v1/internal/sender-ids**', async (route) => {
      const url = route.request().url()
      if (url.includes('/approve')) {
        approveCallMade = true
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [PENDING_ITEM], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
        })
      }
    })

    await page.goto('/sender-ids')
    await page.getByRole('button', { name: 'Approve' }).first().click()

    // Wait for any state update
    await page.waitForTimeout(500)
    expect(approveCallMade).toBe(true)
  })

  test('shows empty state when no pending sender IDs', async ({ page }) => {
    await page.route('**/api/v1/internal/sender-ids**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      })
    })

    await page.goto('/sender-ids')

    await expect(page.getByText('Queue is clear')).toBeVisible()
  })
})
