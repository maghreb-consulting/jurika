import { test, expect } from './fixtures';
import { allerA, fenetre } from './outils/sessions';

/**
 * Lot L6b (E16) : chatbot juridique (panneau lateral, RG-COM-01 a 04).
 *
 *  1. question avec une donnee personnelle (numero de CIN) : bloquee AVANT tout envoi, message
 *     clair qui nomme la nature de la donnee ; aucune requete ne part vers le serveur ;
 *  2. question generale de droit des societes : reponse SOURCEE ("Sources citees"). Si le
 *     fournisseur du modele de langage est absent de la pile visee, le serveur refuse nommement
 *     ("service indisponible") : le test le constate et le DIT (annotation), sans le compter vert
 *     pour la reponse sourcee (test.fail n'est pas utilise : l'absence est une donnee de la pile).
 *
 * Les sources du chatbot sont COMMUNES a tous les cabinets (ecran du super-admin). Sur une pile
 * sans source, aucune reponse ne peut etre sourcee : le parcours depose donc, par l'ecran du
 * super-admin, une source de TEST au nom explicite, et la RETIRE a la fin (meme ecran, ou API).
 */
const SOURCE = 'SOURCE-TEST-PARCOURS-L6B.txt';
const TEXTE_SOURCE = 'Texte de test des parcours JURIKA, depose puis retire par le test. '
  + 'Selon ce texte de test, le nombre des associes d\u2019une societe a responsabilite limitee ne peut etre superieur a cinquante.\n';
test('chatbot : donnee personnelle refusee avant envoi, question generale sourcee', async ({ page, browser, comptes, entrer, aller, sentinelle, capturer }, info) => {
  test.setTimeout(240_000);
  const admin = await fenetre(browser, await comptes.session('SUPER_ADMIN'));
  let deposee = false;
  try {
  await allerA(admin.page, '/admin/chatbot-sources');
  if (!(await admin.page.getByRole('button', { name: `Retirer ${SOURCE}` }).isVisible().catch(() => false))) {
    await admin.page.getByTestId('sources-champ-fichier').setInputFiles({ name: SOURCE, mimeType: 'text/plain', buffer: Buffer.from(TEXTE_SOURCE, 'utf8') });
    const depot = admin.page.waitForResponse((r) => /\/chatbot\/sources$/.test(r.url()) && r.request().method() === 'POST', { timeout: 120_000 });
    await admin.page.getByRole('button', { name: 'D\u00e9poser', exact: true }).click();
    expect((await depot).status(), 'depot de la source de test').toBeLessThan(400);
    deposee = true;
    await expect(admin.page.getByRole('button', { name: `Retirer ${SOURCE}` })).toBeVisible();
  }
  await entrer('EMPLOYE');
  await aller('/chatbot');
  const panneau = page.getByTestId('assistant-panneau');
  await expect(panneau).toBeVisible();
  const saisie = panneau.getByLabel('Votre question juridique');

  // 1. Donnee personnelle : refus avant envoi.
  const envois: string[] = [];
  page.on('request', (r) => { if (/\/chatbot\//.test(r.url()) && r.method() === 'POST') envois.push(r.url()); });
  await saisie.fill('Mon client Ahmed Alaoui, CIN BE123456, peut-il ceder ses parts ?');
  const blocage = panneau.getByTestId('assistant-blocage');
  await expect(blocage).toBeVisible();
  await expect(blocage).toContainText('elle n\u2019a pas \u00e9t\u00e9 envoy\u00e9e');
  await expect(blocage).toContainText('Reformulez-la sans cette donn\u00e9e');
  await expect(panneau.getByRole('button', { name: 'Envoyer la question' })).toBeDisabled();
  await saisie.press('Enter');
  await page.waitForTimeout(500);
  expect(envois, 'aucune question envoyee au serveur').toEqual([]);
  await capturer('chatbot-donnee-personnelle');

  // 2. Question generale.
  await saisie.fill('Quel est le nombre maximal d\u2019associ\u00e9s dans une SARL au Maroc ?');
  await expect(blocage).toBeHidden();
  sentinelle.tolerer({
    methode: 'POST',
    chemin: /\/chatbot\/conversations/,
    statut: [502, 503],
    motif: 'fournisseur du modele de langage absent de la pile visee : refus nomme "service indisponible" (verifie ci-dessous)',
  });
  const reponse = page.waitForResponse((r) => /\/chatbot\/conversations/.test(r.url()) && r.request().method() === 'POST', { timeout: 120_000 });
  await panneau.getByRole('button', { name: 'Envoyer la question' }).click();
  const r = await reponse;
  if (r.status() >= 500) {
    const erreur = panneau.getByRole('alert').last();
    await expect(erreur).toBeVisible();
    await expect(erreur).toContainText(/indisponible/i);
    info.annotations.push({ type: 'chatbot', description: `fournisseur du modele absent : ${r.status()} "${(await erreur.innerText()).trim()}"` });
    await capturer('chatbot-service-indisponible');
    throw new Error(`Reponse sourcee non verifiable sur cette pile : le chatbot repond ${r.status()} (service indisponible).`);
  }
  expect(r.status()).toBeLessThan(400);
  await expect(panneau.getByText('Sources cit\u00e9es')).toBeVisible({ timeout: 60_000 });
  await expect(panneau).toContainText(SOURCE);
  await capturer('chatbot-reponse-sourcee');
  } finally {
    if (deposee) {
      await allerA(admin.page, '/admin/chatbot-sources');
      await admin.page.getByRole('button', { name: `Retirer ${SOURCE}` }).click();
      await admin.page.getByRole('button', { name: /^Retirer$/ }).last().click();
      await expect(admin.page.getByRole('button', { name: `Retirer ${SOURCE}` })).toHaveCount(0);
    }
    expect(await admin.fermer(), 'sentinelle du super-admin').toBeNull();
  }
});
