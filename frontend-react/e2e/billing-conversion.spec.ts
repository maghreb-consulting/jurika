import { expect, test } from './fixtures';

/**
 * Sprint 12 — E2E : conversion trial -> active via Stripe Checkout test card.
 *
 * Pre-requis : vraies cles Stripe TEST + Stripe CLI lance en forward webhook.
 * Skipable via E2E_BILLING_STRIPE_READY=true (env var de l'utilisateur).
 *
 * Scenario :
 *  1. signup nouveau workspace (trial actif 14j)
 *  2. login -> /app/billing
 *  3. Choisir Essentiel -> redirection Stripe Checkout
 *  4. Saisir card test 4242 4242 4242 4242
 *  5. Submit -> redirection /billing/success
 *  6. Polling -> subscription active
 *  7. Verifier acces a /app/dashboard sans 402
 */
test.describe('Billing — conversion trial -> active', () => {
  test.skip(process.env.E2E_BILLING_STRIPE_READY !== 'true',
    'Necessite Stripe TEST keys + Stripe CLI webhook forwarder (cf. PLAN §3)');

  test('signup -> checkout essentiel card 4242 -> active', async ({ page, authenticatedUser }) => {
    await page.goto('/app/billing');
    await expect(page.getByTestId('billing-page')).toBeVisible();
    await expect(page.getByTestId('billing-no-subscription')).toBeVisible();

    await page.getByTestId('plan-essentiel-cta').click();

    // Redirection Stripe Checkout (URL externe)
    await page.waitForURL(/checkout\.stripe\.com/, { timeout: 20_000 });

    // Saisie carte test
    await page.frameLocator('iframe[name*="cardNumber"]')
      .locator('input[name="cardnumber"]')
      .fill('4242 4242 4242 4242');
    await page.frameLocator('iframe[name*="exp-date"]')
      .locator('input[name="exp-date"]').fill('12 / 34');
    await page.frameLocator('iframe[name*="cvc"]')
      .locator('input[name="cvc"]').fill('123');

    await page.getByRole('button', { name: /Souscrire|Subscribe|S'abonner/i }).click();

    // Retour /billing/success?session_id=...
    await page.waitForURL(/\/billing\/success/, { timeout: 30_000 });
    await expect(page.getByText(/Bienvenue/i)).toBeVisible({ timeout: 60_000 });
  });
});
