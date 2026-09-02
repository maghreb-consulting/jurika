import { expect, test } from './fixtures';

/**
 * Sprint 12 — E2E : annulation subscription (RG-BL07).
 *
 * Cancel cote Stripe Customer Portal -> webhook customer.subscription.deleted
 * -> workspace status='cancelled' avec acces preserve jusqu'a current_period_end.
 * Le bandeau orange doit apparaitre dans /billing.
 */
test.describe('Billing — annulation subscription', () => {
  test.skip(process.env.E2E_BILLING_STRIPE_READY !== 'true',
    'Necessite Stripe TEST keys + workspace active + simulate cancel via stripe trigger');

  test('cancel -> banner orange + acces conserve jusqu\'a fin periode', async ({ page, authenticatedUser }) => {
    // Pre-requis : appeler une fois `stripe trigger customer.subscription.deleted`
    // (ou utiliser un workspace deja seede en status cancelled via /api/v1/test/seed).

    await page.goto('/app/billing');
    await expect(page.getByTestId('billing-overview')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('billing-cancelled-banner')).toBeVisible({ timeout: 10_000 });

    // Acces dataroom/dashboard/tickets toujours OK pendant la periode payee
    await page.goto('/app/dashboard');
    await expect(page).not.toHaveURL(/\/forbidden/);
    await expect(page).not.toHaveURL(/\/login/);
  });
});
