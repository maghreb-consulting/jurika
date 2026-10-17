import { expect, type Page } from '@playwright/test';
import { allerA } from './sessions';

/**
 * Lot L6b (E16) : souscription d'un forfait par carte, avec le SIMULATEUR du CMI (actif sur le
 * Z440 : CMI_SIMULATEUR_ACTIF=true ; aucun paiement reel). Parcours du superviseur :
 * /app/billing -> Souscrire -> forfait -> carte -> page du simulateur (formulaire signe) ->
 * "Accepter le paiement" -> retour serveur signe vers JURIKA -> retour du navigateur sur
 * /app/billing/paiement/retour, qui interroge l'etat du paiement jusqu'a "acquis".
 */
export async function souscrireParCarte(page: Page, forfait: 'essentiel' | 'professionnel' | 'max'): Promise<{ texteAcquis: string }> {
  await allerA(page, '/app/billing');
  await page.getByTestId('billing-souscrire').click();
  await expect(page).toHaveURL(/\/app\/billing\/payment/);
  await expect(page.getByTestId('payment-page')).toBeVisible();
  await page.getByTestId(`payment-plan-choisir-${forfait}`).click();
  await expect(page.getByTestId(`payment-plan-choisir-${forfait}`)).toHaveText('Forfait choisi');
  const carte = page.locator('input[name="mode-paiement"][value="CARD"]');
  await expect(carte, 'carte disponible (CMI configure)').toBeEnabled();
  await carte.check();
  await expect(page.getByTestId('payment-submit')).toHaveText('Payer par carte');
  await page.getByTestId('payment-submit').click();

  // Page du simulateur (servie par billing, derriere la passerelle). Le formulaire signe part vers
  // CMI_GATEWAY_URL : une adresse servie par le front (nginx sans /api) repond 405.
  await page.waitForURL((u) => !u.pathname.startsWith('/app/billing/payment'), { timeout: 30_000 });
  const refusNginx = await page.getByRole('heading', { name: /^405 Not Allowed$/ }).isVisible().catch(() => false);
  expect(refusNginx, `page du CMI injoignable : ${page.url()} repond "405 Not Allowed" (CMI_GATEWAY_URL vise le front, `
    + 'qui ne relaie pas /api ; il doit viser la passerelle :8080)').toBe(false);
  await expect(page.getByRole('heading', { name: 'Paiement par carte (simulateur CMI)' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Simulateur de test : aucun paiement r\u00e9el n\u2019est effectu\u00e9.')).toBeVisible();
  await page.getByRole('button', { name: 'Accepter le paiement' }).click();

  // Retour sur JURIKA : le paiement n'est acquis qu'a la confirmation serveur.
  await expect(page).toHaveURL(/\/app\/billing\/paiement\/retour/, { timeout: 30_000 });
  const acquis = page.getByTestId('paiement-retour-acquis');
  await expect(acquis).toBeVisible({ timeout: 60_000 });
  return { texteAcquis: (await acquis.innerText()).trim() };
}

/**
 * Souscription par VIREMENT (RG-PAY-02) : le superviseur declare le mode, les instructions
 * s'affichent ; le paiement attend que JURIKA (super-admin) en enregistre la reception.
 */
export async function declarerVirement(page: Page, forfait: 'essentiel' | 'professionnel' | 'max'): Promise<void> {
  await allerA(page, '/app/billing');
  await page.getByTestId('billing-souscrire').click();
  await expect(page.getByTestId('payment-page')).toBeVisible();
  await page.getByTestId(`payment-plan-choisir-${forfait}`).click();
  await expect(page.getByTestId(`payment-plan-choisir-${forfait}`)).toHaveText('Forfait choisi');
  await page.locator('input[name="mode-paiement"][value="BANK_TRANSFER"]').check();
  await expect(page.getByTestId('payment-submit')).toHaveText('Confirmer le mode de paiement');
  await page.getByTestId('payment-submit').click();
  const suite = page.getByTestId('suite-paiement-hors-ligne');
  await expect(suite).toBeVisible();
  await expect(suite).toContainText('Virement bancaire');
  await expect(suite).toContainText('TTC');
}

/** Le super-admin enregistre la reception du paiement declare par le cabinet (code JUR-XXXXX). */
export async function enregistrerReception(admin: Page, codeCabinet: string, reference: string): Promise<void> {
  await allerA(admin, '/admin/paiements');
  const ligne = admin.getByTestId('paiements-a-enregistrer').locator('li[data-testid^="a-enregistrer-"]').filter({ hasText: codeCabinet });
  await expect(ligne).toHaveCount(1);
  await ligne.getByLabel('R\u00e9f\u00e9rence (virement, ch\u00e8que ou re\u00e7u)').fill(reference);
  const r = admin.waitForResponse((x) => /\/billing\/admin\/paiements\/\d+\/valider$/.test(x.url()));
  await ligne.getByRole('button', { name: 'Enregistrer la r\u00e9ception' }).click();
  expect((await r).status(), 'POST valider').toBe(200);
  await expect(ligne).toHaveCount(0);
}
