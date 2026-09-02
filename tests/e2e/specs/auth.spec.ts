import { test, expect } from '@playwright/test';
import { DEMO, loginAsKarim } from '../helpers/auth';

test.describe('Authentification 3 etapes', () => {
  test('Login workspace -> credentials -> dashboard', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /espace/i }).first()).toBeVisible();

    await page.getByPlaceholder('JUR-XXXXX').fill(DEMO.workspace);
    await page.getByRole('button', { name: /Continuer/i }).click();

    await expect(page.getByText(/Bienvenue/i).first()).toBeVisible({ timeout: 10000 });
    await page.getByPlaceholder(/nom@cabinet/i).fill(DEMO.email);
    await page.getByPlaceholder('••••••••').fill(DEMO.password);
    await page.getByRole('button', { name: /Se connecter/i }).click();

    await page.waitForURL('**/dashboard', { timeout: 15000 });
    await expect(
      page.getByText(/Bonjour|Tableau de bord|Dashboard|tickets/i).first(),
    ).toBeVisible();
  });

  test('Login workspace inconnu echoue', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('JUR-XXXXX').fill('JUR-XXXXX');
    await page.getByRole('button', { name: /Continuer/i }).click();
    await expect(
      page.getByText(/inconnu|invalide|introuvable|Code/i).first(),
    ).toBeVisible({ timeout: 10000 });
  });

  test('Logout retourne sur login', async ({ page }) => {
    await loginAsKarim(page);
    await page
      .locator('header button')
      .filter({ has: page.locator('div').filter({ hasText: /^K$/ }) })
      .first()
      .click();
    await page.getByText(/Deconnexion/i).first().click();
    await page.waitForURL(/\/login$/, { timeout: 15000 });
  });
});
