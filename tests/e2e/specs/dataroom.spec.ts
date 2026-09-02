import { test, expect } from '@playwright/test';
import { loginAsKarim } from '../helpers/auth';

test.describe('Data Room V2', () => {
  test('Page Data Room accessible', async ({ page }) => {
    await loginAsKarim(page);
    await page.goto('/data-rooms');
    await expect(page.getByRole('heading', { name: /Data Room/i }).first()).toBeVisible({ timeout: 10000 });
  });

  test('Archive supprimee (V2 spec)', async ({ page }) => {
    await loginAsKarim(page);
    await page.goto('/data-rooms');
    // L'onglet "Archive" ne doit pas exister dans la nouvelle UI
    await expect(page.getByRole('tab', { name: /^Archive$/i })).toHaveCount(0);
  });
});
