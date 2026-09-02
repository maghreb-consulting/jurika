import { test, expect } from '@playwright/test';

/**
 * Smoke test minimal — Sprint 14 ter TASK E1.
 * Verifie que l'application repond et que le bundle React monte (titre + root element non vide).
 */
test.describe('JURIKA Smoke', () => {
  test('homepage loads with JURIKA title', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/JURIKA/i);
  });

  test('react root mounts', async ({ page }) => {
    await page.goto('/');
    const root = page.locator('#root');
    await expect(root).not.toBeEmpty();
  });
});
