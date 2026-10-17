import { test, expect, fermerVisite } from './fixtures';
import { api } from './outils/comptes';
import { premiereConnexion } from './outils/connexion';
import { accepterContrat, remplirIdentiteCabinet } from './outils/contrat';
import { BASE_URL, SEED_URL } from './outils/env';
import { dernierCodeSms, dernierCourriel } from './outils/messages';
import { souscrireParCarte } from './outils/paiement';
import { allerA } from './outils/sessions';

/**
 * Lot L6b (E16), complete par la recette R1 : essai gratuit (/essai) de bout en bout, comme un
 * visiteur, jusqu'a la souscription.
 *
 * Le code SMS et le courriel (lien de confirmation, identifiants) sont lus par la route de test
 * d'auth-service (outils/messages.ts : identites @jurika.test seulement, route absente en
 * production). Effets reels : un courriel part vers une adresse fictive @jurika.test ; numero de
 * telephone fictif different a chaque execution (un numero deja servi est refuse) ; le paiement
 * passe par le SIMULATEUR du CMI (aucun paiement reel) ; le cabinet d'essai est supprime a la fin
 * (route de nettoyage du seed).
 */

const LIBELLE_ESSAI = 'Essai gratuit (fonctions du forfait Professionnel)';

interface Identite { n: string; prenom: string; nom: string; email: string }

function identite(): Identite {
  const n = String(Date.now()).slice(-8);
  return { n, prenom: 'Yasmine', nom: 'Fictive', email: `essai-${n}@jurika.test` };
}

async function remplirDemande(page: import('@playwright/test').Page, id: Identite): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Essai gratuit de 7 jours' })).toBeVisible();
  await page.getByLabel('Pr\u00e9nom').fill(id.prenom);
  await page.getByLabel('Nom', { exact: true }).fill(id.nom);
  await page.getByLabel('Organisation (nom du cabinet)').fill(`Cabinet fictif des parcours ${id.n}`);
  await page.getByTestId('essai-telephone').fill(`6${id.n}`);
  await page.getByLabel('Adresse \u00e9lectronique').fill(id.email);
  await expect(page.getByLabel('Texte des conditions d\u2019utilisation de l\u2019essai')).toBeVisible();
  await page.getByLabel('J\u2019ai lu et j\u2019accepte les conditions d\u2019utilisation de l\u2019essai gratuit JURIKA.').check();
  await page.getByRole('button', { name: 'Recevoir mes codes de v\u00e9rification' }).click();
}

test('essai gratuit : un code SMS faux est refuse, sans ouvrir d\u2019essai', async ({ page, aller, sentinelle, capturer }) => {
  test.setTimeout(180_000);
  await aller('/essai');
  await remplirDemande(page, identite());
  const verifs = page.getByRole('region', { name: 'V\u00e9rifications' });
  await expect(verifs).toBeVisible({ timeout: 30_000 });
  const champCode = verifs.getByLabel(/^Code re\u00e7u par SMS/);
  await expect(champCode).toBeVisible();
  await expect(verifs).not.toContainText('n\u2019a pas pu \u00eatre envoy\u00e9');
  sentinelle.tolerer({ methode: 'POST', chemin: /\/public\/essai\/demandes\/[^/]+\/telephone$/, statut: 401, motif: 'code SMS faux saisi expres : refus attendu (401 "Code recu par SMS incorrect.")' });
  await champCode.fill('000000');
  await verifs.getByRole('button', { name: 'V\u00e9rifier le code' }).click();
  await expect(champCode).toHaveAttribute('aria-invalid', 'true');
  await expect(verifs.getByRole('alert').first()).toContainText('Code re\u00e7u par SMS incorrect.');
  await expect(page.getByTestId('essai-ouvert')).toHaveCount(0);
  await capturer('essai-code-refuse');
});

test('essai gratuit de bout en bout : inscription, verifications, premiere connexion avec le code du cabinet, souscription', async ({ page, aller, capturer }, info) => {
  test.setTimeout(480_000);
  const id = identite();
  let workspaceId: string | null = null;
  try {
    // 1. Inscription : cinq informations et conditions acceptees.
    await aller('/essai');
    await remplirDemande(page, id);
    const verifs = page.getByRole('region', { name: 'V\u00e9rifications' });
    await expect(verifs).toBeVisible({ timeout: 30_000 });
    await capturer('essai-verifications');

    // 2. Telephone : le code recu par SMS.
    await verifs.getByLabel(/^Code re\u00e7u par SMS/).fill(await dernierCodeSms(id.email));
    await verifs.getByRole('button', { name: 'V\u00e9rifier le code' }).click();
    await expect(page.getByText('Votre num\u00e9ro est confirm\u00e9.')).toBeVisible();

    // 3. Adresse : le lien du courriel, ouvert dans un autre onglet (comme depuis la messagerie).
    const confirmation = await dernierCourriel(id.email, 'essai-confirmation');
    expect(confirmation.variables.firstName).toBe(id.prenom);
    const lien = confirmation.variables.verificationUrl;
    expect(lien).toMatch(/\/essai\/confirmer\?jeton=/);
    const onglet = await page.context().newPage();
    await onglet.goto(`${BASE_URL}/essai/confirmer${lien.slice(lien.indexOf('?'))}`);
    const ouvert = onglet.getByTestId('essai-ouvert');
    await expect(ouvert).toBeVisible({ timeout: 30_000 });
    const codeCabinet = (await ouvert.innerText()).match(/JUR-[A-Z0-9]{5}/)?.[0];
    expect(codeCabinet, 'code du cabinet affiche a l\u2019ouverture de l\u2019essai').toBeTruthy();
    await onglet.close();

    // L'onglet d'origine (qui relit l'etat) montre aussi le code du cabinet et l'identifiant.
    await expect(page.getByTestId('essai-ouvert')).toContainText(codeCabinet!, { timeout: 20_000 });
    await capturer('essai-ouvert');

    // 4. Courriel de bienvenue : memes code et identifiant, mot de passe provisoire, forfait de l'essai.
    const bienvenue = await dernierCourriel(id.email, 'welcome');
    expect(bienvenue.variables.firstName, 'le nom saisi a l\u2019inscription, jamais une donnee de test').toBe(id.prenom);
    expect(bienvenue.variables.workspaceCode).toBe(codeCabinet);
    expect(bienvenue.variables.planLabel).toBe(LIBELLE_ESSAI);
    const identifiant = bienvenue.variables.loginEmail;
    await expect(page.getByTestId('essai-ouvert')).toContainText(identifiant);

    // Activation (bouton "Activer mon compte" du courriel de bienvenue).
    await page.goto(bienvenue.variables.verificationUrl.replace(/^https?:\/\/[^/]+/, BASE_URL));
    await expect(page.getByText('Adresse v\u00e9rifi\u00e9e')).toBeVisible({ timeout: 30_000 });

    // 5. Premiere connexion avec le code du cabinet (mot de passe change, double authentification).
    await premiereConnexion(page, codeCabinet!, {
      prenom: id.prenom, nom: id.nom, identifiant, motDePasseTemporaire: bienvenue.variables.temporaryPassword,
    });
    await fermerVisite(page);
    const jeton = await page.evaluate(() => window.localStorage.getItem('jurika_access_token'));
    workspaceId = (await api.get<{ workspaceId: string }>('/auth/me', jeton!)).workspaceId;
    await expect(page.getByRole('button', { name: `Menu du compte : ${identifiant}` })).toBeVisible();
    await expect(page.locator('body')).not.toContainText('Smoke');

    // 6. Forfait affiche pendant l'essai (RG-ESS-07) : bandeau, facturation, equipe.
    await expect(page.getByTestId('bandeau-essai-forfait')).toHaveText(`Forfait : ${LIBELLE_ESSAI}`);
    await allerA(page, '/app/billing');
    await expect(page.getByTestId('billing-essai')).toContainText(LIBELLE_ESSAI);
    await expect(page.getByTestId('billing-usage')).not.toContainText('Essentiel');
    await expect(page.getByTestId('billing-fonctions')).not.toContainText('disponible d\u00e8s le forfait Professionnel');
    await capturer('essai-facturation-pendant');
    await allerA(page, '/app/team');
    await expect(page.getByText(LIBELLE_ESSAI, { exact: true })).toBeVisible();

    // 7. Souscription au forfait Essentiel, par carte (simulateur du CMI).
    await remplirIdentiteCabinet(page);
    const { texteAcquis } = await souscrireParCarte(page, 'essentiel');
    expect(texteAcquis).toMatch(/TTC/);
    // L'etat de l'essai est garde 60 s par billing (RemoteTrialAccessChecker) : l'exemplaire du
    // contrat ne se telecharge qu'une fois l'essai converti vu par billing.
    await expect.poll(async () => (await api.get<{ statut: string }>('/workspace/essai', jeton!)).statut,
      { timeout: 30_000 }).toBe('CONVERTED');
    await page.waitForTimeout(61_000);
    const contrat = await accepterContrat(page, info, 'contrat-essai-essentiel.pdf', { exigerComplet: false });
    expect(contrat).toContain('Essentiel');

    // 8. Apres la souscription : le forfait choisi, partout ; rien n'annonce Professionnel.
    await allerA(page, '/app/billing');
    const abonnement = page.getByTestId('billing-abonnement');
    await expect(abonnement).toContainText('Essentiel');
    await expect(abonnement).not.toContainText('Professionnel');
    await expect(page.getByTestId('billing-essai')).toHaveCount(0);
    await expect(page.getByTestId('bandeau-essai-forfait')).toHaveCount(0);
    await capturer('essai-facturation-apres');
    await allerA(page, '/app/team');
    await expect(page.getByText('Forfait Essentiel', { exact: true })).toBeVisible();
    await expect(page.locator('body')).not.toContainText(LIBELLE_ESSAI);
  } finally {
    if (workspaceId) {
      const r = await fetch(`${SEED_URL}/test/seed/cleanup/${workspaceId}`, { method: 'DELETE' });
      if (!r.ok) info.annotations.push({ type: 'nettoyage', description: `cabinet d\u2019essai ${workspaceId} non supprime (${r.status})` });
    }
  }
});
