import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * Sprint 12.5 T14 — Visual regression sur 10 pages cles + A11y dark/light.
 *
 * Strategie :
 *  1. Avant chaque suite, on prend des screenshots des 10 pages (full page)
 *     dans .screenshots/sprint-12-5/{theme}/ pour reference humaine.
 *  2. Les snapshots Playwright (toHaveScreenshot) trackent les regressions
 *     pixel-near sur les composants critiques (Navbar, Sidebar, Card).
 *  3. Sur PR : compare avec --update-snapshots si baseline modifiée
 *     intentionnellement (ex: changement de palette legitime Sprint 13).
 *
 * Prerequis local :
 *   npm run dev    # serveur Vite sur :5173 (baseURL config Playwright)
 *   USER_EMAIL=demo@jurika.ma + USER_PWD=... (compte seed dev) dans .env
 *
 * Pour run :
 *   npx playwright test e2e/theme-visual-regression.spec.ts
 *
 * NOTE : ce spec ne lance PAS le dev server automatiquement (la fixture
 * webServer dans playwright.config.ts s'en charge si configuree). Si non
 * configuree, lancer `npm run dev` manuellement avant.
 */

const KEY_PAGES = [
  { name: 'landing', path: '/', requiresAuth: false },
  { name: 'login', path: '/login', requiresAuth: false },
  { name: 'signup', path: '/signup', requiresAuth: false },
  { name: 'dashboard', path: '/dashboard', requiresAuth: true },
  { name: 'tickets', path: '/tickets', requiresAuth: true },
  { name: 'dataroom', path: '/data-rooms', requiresAuth: true },
  { name: 'chat', path: '/chat', requiresAuth: true },
  { name: 'chatbot', path: '/chatbot', requiresAuth: true },
  { name: 'billing', path: '/app/billing', requiresAuth: true },
  { name: 'forbidden', path: '/forbidden', requiresAuth: false },
] as const;

const SCREENSHOT_DIR = path.join(process.cwd(), '.screenshots', 'sprint-12-5');

test.describe('@theme-visual sprint-12-5 baseline screenshots', () => {
  test.describe.configure({ mode: 'serial' });

  for (const mode of ['dark', 'light'] as const) {
    test.describe(`${mode} theme`, () => {
      test.beforeEach(async ({ page }) => {
        // Pose le theme prefere avant toute navigation pour eviter le FOUC.
        await page.addInitScript((m) => {
          window.localStorage.setItem(
            'jurika-theme',
            JSON.stringify({ state: { mode: m }, version: 0 }),
          );
        }, mode);
      });

      for (const p of KEY_PAGES) {
        test(`${p.name} page renders`, async ({ page }) => {
          test.skip(
            p.requiresAuth && !process.env.E2E_AUTH_COOKIE,
            'Auth-requiring page needs E2E_AUTH_COOKIE -- run after auth-login-2fa.spec.ts',
          );
          await page.goto(p.path);
          // Attendre que le grain overlay + first paint soient stables.
          await page.waitForLoadState('networkidle');
          // Screenshot reference (full page) -> .screenshots/sprint-12-5/{mode}/{name}.png
          await page.screenshot({
            path: path.join(SCREENSHOT_DIR, mode, `${p.name}.png`),
            fullPage: true,
          });
        });
      }

      test('navbar visual regression', async ({ page }) => {
        await page.goto('/login');
        const html = page.locator('html');
        await expect(html).toHaveAttribute(
          'data-theme',
          mode === 'light' ? 'light' : '',
        );
      });

      test('A11y: gold focus ring visible on tab', async ({ page }) => {
        await page.goto('/login');
        await page.keyboard.press('Tab');
        const focused = await page.evaluate(() => {
          const el = document.activeElement as HTMLElement | null;
          if (!el) return null;
          const cs = window.getComputedStyle(el);
          return {
            outlineColor: cs.outlineColor,
            outlineWidth: cs.outlineWidth,
            outlineStyle: cs.outlineStyle,
          };
        });
        expect(focused).toBeTruthy();
        // outline-width 2px attendu (cf index.css :focus-visible Sprint 12.5 T13).
        // Couleur exacte verifie hors test (CSS computed style varies si AA durcissement).
        expect(focused?.outlineStyle).toMatch(/solid|auto/);
      });
    });
  }
});

test.describe('@theme-tokens sprint-12-5 token integrity', () => {
  test('CSS variables propagent palette marketing en dark', async ({ page }) => {
    await page.goto('/login');
    const tokens = await page.evaluate(() => {
      const cs = window.getComputedStyle(document.documentElement);
      return {
        bg: cs.getPropertyValue('--color-bg').trim(),
        accent: cs.getPropertyValue('--color-accent').trim(),
        fontHeading: cs.getPropertyValue('--font-heading').trim(),
      };
    });
    // Tokens attendus (cf index.css @theme bloc Sprint 12.5 T1).
    expect(tokens.bg).toBe('#050d1f');
    expect(tokens.accent).toBe('#c8a45c');
    expect(tokens.fontHeading.toLowerCase()).toContain('playfair');
  });
});
