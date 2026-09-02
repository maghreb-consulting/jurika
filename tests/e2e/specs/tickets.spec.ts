import { test, expect } from '@playwright/test';
import { loginAsKarim } from '../helpers/auth';

test.describe('Tickets', () => {
  test('Liste des tickets accessible', async ({ page }) => {
    await loginAsKarim(page);
    await page.goto('/tickets');
    await expect(page.getByRole('heading', { name: /Tickets/i }).first()).toBeVisible({ timeout: 10000 });
  });

  test('Bouton Nouveau ticket visible pour EMPLOYE', async ({ page }) => {
    await loginAsKarim(page);
    await page.goto('/tickets');
    await expect(page.getByRole('button', { name: /Nouveau ticket/i })).toBeVisible({ timeout: 10000 });
  });

  test('Ouverture drawer Nouveau ticket', async ({ page }) => {
    await loginAsKarim(page);
    await page.goto('/tickets');
    await page.getByRole('button', { name: /Nouveau ticket/i }).click();
    await expect(page.getByPlaceholder(/Constitution/i).first()).toBeVisible({ timeout: 10000 });
  });
});
