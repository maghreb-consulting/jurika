import type { Page } from '@playwright/test';

export const DEMO = {
  workspace: 'JUR-DEMO1',
  email: 'karim@jurika.ma',
  password: 'Admin@2026',
};

export async function loginAsKarim(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('JUR-XXXXX').fill(DEMO.workspace);
  await page.getByRole('button', { name: /Continuer/i }).click();
  await page.getByPlaceholder(/nom@cabinet/i).fill(DEMO.email);
  await page.getByPlaceholder('••••••••').fill(DEMO.password);
  await page.getByRole('button', { name: /Se connecter/i }).click();
  await page.waitForURL('**/dashboard', { timeout: 15000 });
}
