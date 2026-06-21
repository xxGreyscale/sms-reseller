/**
 * Playwright E2E: Admin authentication flow (ADMN-01)
 *
 * Uses Playwright route interception to mock the backend — no real backend required.
 *
 * Flow: visit /login → fill credentials → submit → redirected to /sender-ids
 * (middleware bounces unauthenticated requests → /login)
 */
import { test, expect } from '@playwright/test'

test.describe('Admin authentication', () => {
  test('login page is accessible at /login', async ({ page }) => {
    await page.route('**/api/v1/auth/admin/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'mock-jwt-token-for-e2e' }),
      })
    })

    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'Open Desk Admin' })).toBeVisible()
    await expect(page.getByLabel('Email')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  })

  test('unauthenticated request to /users redirects to /login', async ({ page }) => {
    await page.goto('/users')
    // middleware should redirect to /login
    await expect(page).toHaveURL(/\/login/)
  })

  test('successful login redirects to /sender-ids', async ({ page }) => {
    await page.route('**/api/v1/auth/admin/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'mock-jwt-token-for-e2e' }),
      })
    })

    // Mock /sender-ids page to avoid needing backend for user list
    await page.route('**/api/v1/admin/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      })
    })

    await page.goto('/login')
    await page.getByLabel('Email').fill('admin@opendesk.co.tz')
    await page.getByLabel('Password').fill('password123')
    await page.getByRole('button', { name: 'Sign in' }).click()

    // After successful login, should be at /sender-ids
    await expect(page).toHaveURL(/\/sender-ids/, { timeout: 5000 })
  })

  test('invalid credentials shows error message', async ({ page }) => {
    await page.route('**/api/v1/auth/admin/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Unauthorized' }),
      })
    })

    await page.goto('/login')
    await page.getByLabel('Email').fill('admin@opendesk.co.tz')
    await page.getByLabel('Password').fill('wrongpassword')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert')).toContainText('Invalid email or password')
  })
})
