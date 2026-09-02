import { defineConfig, devices } from '@playwright/test';

/**
 * JURIKA e2e configuration — Sprint 14 ter TASK E1.
 *
 * Multi-browser (chromium + firefox + webkit) avec strategie :
 *   - PR : uniquement chromium (rapide < 5 min)
 *   - merge main : 3 projects (full < 10 min)
 *
 * Pre-requis local :
 *   npx playwright install --with-deps chromium firefox webkit
 *
 * Lancer :
 *   npm run e2e                # tous les browsers
 *   npm run e2e:chromium       # uniquement chromium (PR-like)
 *   npm run e2e:ui             # UI interactive
 *   npm run e2e:headed         # headed mode pour debug visuel
 *   npm run e2e:report         # rouvre le HTML report
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['html'], ['github']] : [['html'], ['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  // Sprint 11 TASK 7 -- 2 webServers : SPA (5173) + marketing-site (5174)
  // pour les e2e cross-domain signup-funnel.spec.ts.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : [
        {
          command: 'npm run dev',
          port: 5173,
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
        },
        {
          command: 'cd ../marketing-site && npm run dev -- --port 5174',
          port: 5174,
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
        },
      ],
});
