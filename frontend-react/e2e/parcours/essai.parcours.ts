import { test, expect } from './fixtures';

/**
 * Lot L6b (E16) : inscription a l'essai gratuit (/essai), comme un visiteur.
 *
 * Joue jusqu'a l'etape des verifications : formulaire (cinq informations, conditions lues et
 * acceptees), demande creee, ecran "code recu par SMS" et "lien recu par courriel". La suite exige
 * le code SMS (sans fournisseur reel, il est ecrit dans le JOURNAL d'auth, lisible seulement par
 * Docker) et le lien du courriel (remis par Brevo sur le Z440) : le parcours ne peut pas aller au
 * bout sans eux et ne les contourne pas. Il verifie en revanche qu'un code faux est refuse avec
 * un message, sans ouvrir d'essai.
 *
 * Effets reels : un courriel de confirmation part vers une adresse fictive (@jurika.test). Un
 * numero de telephone fictif different a chaque execution (un numero deja servi est refuse).
 */
test('essai gratuit : demande jusqu\u2019aux verifications (code SMS et lien du courriel hors de portee du test)', async ({ page, aller, sentinelle, capturer }, info) => {
  test.setTimeout(180_000);
  const n = String(Date.now()).slice(-8);
  await aller('/essai');
  await expect(page.getByRole('heading', { name: 'Essai gratuit de 7 jours' })).toBeVisible();
  await page.getByLabel('Pr\u00e9nom').fill('Yasmine');
  await page.getByLabel('Nom', { exact: true }).fill('Fictive');
  await page.getByLabel('Organisation (nom du cabinet)').fill(`Cabinet fictif des parcours ${n}`);
  await page.getByTestId('essai-telephone').fill(`6${n}`);
  await page.getByLabel('Adresse \u00e9lectronique').fill(`essai-${n}@jurika.test`);
  await expect(page.getByLabel('Texte des conditions d\u2019utilisation de l\u2019essai')).toBeVisible();
  await page.getByLabel('J\u2019ai lu et j\u2019accepte les conditions d\u2019utilisation de l\u2019essai gratuit JURIKA.').check();
  await page.getByRole('button', { name: 'Recevoir mes codes de v\u00e9rification' }).click();

  const verifs = page.getByRole('region', { name: 'V\u00e9rifications' });
  await expect(verifs).toBeVisible({ timeout: 30_000 });
  const champCode = verifs.getByLabel(/^Code re\u00e7u par SMS/);
  await expect(champCode).toBeVisible();
  await expect(verifs).not.toContainText('n\u2019a pas pu \u00eatre envoy\u00e9');
  await capturer('essai-verifications');

  // Un code faux est refuse, nommement ; aucun essai n'est ouvert.
  sentinelle.tolerer({ methode: 'POST', chemin: /\/public\/essai\/demandes\/[^/]+\/telephone$/, statut: 401, motif: 'code SMS faux saisi expres : refus attendu (401 "Code recu par SMS incorrect.")' });
  await champCode.fill('000000');
  await verifs.getByRole('button', { name: 'V\u00e9rifier le code' }).click();
  await expect(champCode).toHaveAttribute('aria-invalid', 'true');
  await expect(verifs.getByRole('alert').first()).toContainText('Code re\u00e7u par SMS incorrect.');
  await expect(page.getByTestId('essai-ouvert')).toHaveCount(0);
  info.annotations.push({ type: 'essai', description: 'arret aux verifications : code SMS (journal d\u2019auth, Docker) et lien du courriel (Brevo) non lisibles par le test' });
  await capturer('essai-code-refuse');
});
