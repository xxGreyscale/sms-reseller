/**
 * Playwright E2E: User search screen (ADMN-02)
 *
 * Uses cookie injection to simulate authenticated state and
 * route interception to mock backend responses.
 */
import { test, expect } from '@playwright/test'

const MOCK_USERS = [
  {
    id: 'uuid-001',
    fullName: 'Amina Hassan',
    email: 'amina@example.co.tz',
    phone: '+255712345678',
    verificationStatus: 'VERIFIED',
    createdAt: '2026-01-15T10:00:00Z',
  },
]

test.describe('User search', () => {
  test.beforeEach(async ({ context }) => {
    // Inject admin_token cookie to bypass middleware
    await context.addCookies([
      {
        name: 'admin_token',
        value: 'mock-jwt-token-for-e2e',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
        secure: false,
      },
    ])
  })

  test('users page shows empty state when no search entered', async ({ page }) => {
    await page.route('**/api/v1/admin/users**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      })
    })

    await page.goto('/users')
    await expect(page.getByText('Search for a user')).toBeVisible()
  })

  test('user search finds a user by email', async ({ page }) => {
    await page.route('**/api/v1/admin/users**', async (route) => {
      const url = new URL(route.request().url())
      const q = url.searchParams.get('q') ?? ''

      if (q.includes('amina')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: MOCK_USERS,
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
          }),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
        })
      }
    })

    await page.goto('/users')
    await page.getByPlaceholder('Search by email or phone…').fill('amina@example.co.tz')
    await page.getByRole('button', { name: 'Search Users' }).click()

    await expect(page.getByText('Amina Hassan')).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('amina@example.co.tz')).toBeVisible()
  })

  test('sidebar shows all 6 nav items', async ({ page }) => {
    await page.route('**/api/v1/admin/users**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      })
    })

    await page.goto('/users')

    await expect(page.getByRole('link', { name: 'Users' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Ledger' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sender IDs' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Refunds' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Bundle Catalog' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Audit Log' })).toBeVisible()
  })
})
