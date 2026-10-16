import { expect, type Page, type TestInfo } from '@playwright/test';
import { analyserDocument, rapportDocument, textePdf } from './documents';
import { allerA, telecharger } from './sessions';

/**
 * Lot L6b (E16) : identite du cabinet (Parametres > Cabinet) et acceptation du contrat
 * d'abonnement par le superviseur (/app/contrat), exemplaire PDF ouvert et controle.
 *
 * L'identite du cabinet se saisit AVANT que des conditions particulieres soient ouvertes : tant
 * qu'elles attendent l'acceptation, le superviseur n'accede qu'a l'ecran du contrat (RG-CTR-03).
 * Sans elle, le contrat imprime "[non renseigne : ...]" (refuse par le controle des documents).
 */
export async function remplirIdentiteCabinet(page: Page): Promise<void> {
  await allerA(page, '/settings/cabinet');
  await expect(page.getByTestId('cabinet-tab')).toBeVisible();
  await page.getByTestId('cabinet-ice-input').fill('001122334000055');
  await page.getByTestId('cabinet-rc-input').fill('123456');
  await page.getByTestId('cabinet-if-input').fill('44556677');
  await page.getByTestId('cabinet-city-input').fill('Casablanca');
  await page.getByTestId('cabinet-adresse-input').fill('10 boulevard fictif des Parcours');
  await page.getByTestId('cabinet-save').click();
  await expect(page.getByTestId('cabinet-saved')).toBeVisible();
}

/** Accepte le contrat presente (conditions particulieres) ; rend le texte de l'exemplaire PDF. */
export async function accepterContrat(page: Page, info: TestInfo, nomPdf: string): Promise<string> {
  await allerA(page, '/app/contrat');
  const bloc = page.getByRole('region', { name: 'Contrat \u00e0 accepter' }).or(page.locator('[aria-label="Contrat \u00e0 accepter"]'));
  await expect(bloc.first()).toBeVisible();
  await page.getByLabel('Civilit\u00e9 *').selectOption('M.');
  await page.getByLabel('Qualit\u00e9 au sein du cabinet *').fill('g\u00e9rant');
  await page.getByLabel('Forme juridique du cabinet').fill('SARL');
  await page.getByLabel('Ville du registre du commerce du cabinet').fill('Casablanca');
  await page.getByRole('button', { name: 'Afficher le contrat \u00e0 accepter' }).click();
  await expect(page.getByLabel('Texte du contrat d\u2019abonnement')).toBeVisible();
  const manquants = page.getByRole('note').filter({ hasText: 'Mentions non renseign\u00e9es dans ce contrat' });
  // Texte lu seulement s'il existe (innerText attendrait l'element sans fin).
  const nbManquants = await manquants.count();
  expect(nbManquants, `mentions manquantes au contrat : ${nbManquants ? await manquants.innerText() : ''}`).toBe(0);
  await page.getByLabel('J\u2019ai lu et j\u2019accepte le contrat d\u2019abonnement JURIKA').check();
  await page.getByRole('button', { name: 'Accepter le contrat' }).click();
  const r = page.waitForResponse((x) => x.url().endsWith('/billing/contrat/accepter'));
  await page.getByRole('button', { name: 'Je confirme mon acceptation' }).click();
  expect((await r).status(), 'POST /billing/contrat/accepter').toBe(200);
  const bouton = page.getByRole('button', { name: 'T\u00e9l\u00e9charger l\u2019exemplaire (PDF)' }).first();
  await expect(bouton).toBeVisible();
  const { nom, corps } = await telecharger(page, () => bouton.click(), info);
  expect(nom).toMatch(/^contrat-abonnement-jurika-.+\.pdf$/);
  const a = analyserDocument(corps);
  expect(a.genre).toBe('pdf');
  expect(rapportDocument(nomPdf, a)).toBeNull();
  return textePdf(corps).join('\n');
}
