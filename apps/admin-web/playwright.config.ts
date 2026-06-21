import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright E2E configuration for admin-web.
 *
 * Runs against the Next.js dev server (or standalone output) for full stack E2E.
 * Chromium project only at MVP (D-11 — locked stack, 1 browser target).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
