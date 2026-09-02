import { expect, test } from './fixtures';

/**
 * Sprint 12 — E2E : ouverture Customer Portal Stripe (RG-BL09).
 *
 * Pre-requis : workspace deja en subscription active (utilise fixture).
 */
test.describe('Billing — Customer Portal', () => {
  test.skip(process.env.E2E_BILLING_STRIPE_READY !== 'true',
    'Necessite Stripe TEST keys + workspace deja active');

  test('clic "Gerer mon abonnement" -> redirection Stripe Customer Portal', async ({ page, authenticatedUser }) => {
    await page.goto('/app/billing');
    await expect(page.getByTestId('billing-overview')).toBeVisible({ timeout: 10_000 });

    const portalCta = page.getByTestId('billing-portal-cta');
    await expect(portalCta).toBeEnabled();
    await portalCta.click();

    await page.waitForURL(/billing\.stripe\.com/, { timeout: 20_000 });
    await expect(page.url()).toMatch(/billing\.stripe\.com/);
  });
});
