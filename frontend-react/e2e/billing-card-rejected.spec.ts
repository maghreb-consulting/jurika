import { expect, test } from './fixtures';

/**
 * Sprint 12 — E2E : carte refusee 4000 0000 0000 0002 -> workspace reste trial.
 */
test.describe('Billing — carte refusee', () => {
  test.skip(process.env.E2E_BILLING_STRIPE_READY !== 'true',
    'Necessite Stripe TEST keys + Stripe CLI');

  test('card 4000-0000-0000-0002 -> trial preserve + erreur lisible', async ({ page, authenticatedUser }) => {
    await page.goto('/app/billing');
    await page.getByTestId('plan-business-cta').click();

    await page.waitForURL(/checkout\.stripe\.com/, { timeout: 20_000 });
    await page.frameLocator('iframe[name*="cardNumber"]')
      .locator('input[name="cardnumber"]')
      .fill('4000 0000 0000 0002');
    await page.frameLocator('iframe[name*="exp-date"]')
      .locator('input[name="exp-date"]').fill('12 / 34');
    await page.frameLocator('iframe[name*="cvc"]')
      .locator('input[name="cvc"]').fill('123');

    await page.getByRole('button', { name: /Souscrire|Subscribe|S'abonner/i }).click();

    await expect(page.getByText(/(declined|refusee|refused)/i)).toBeVisible({ timeout: 15_000 });

    // Retour app : trial doit etre intact (pas de subscription cree)
    await page.goto('/app/billing');
    await expect(page.getByTestId('billing-no-subscription')).toBeVisible();
  });
});
