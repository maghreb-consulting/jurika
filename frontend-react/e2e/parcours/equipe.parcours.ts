import type { Page } from '@playwright/test';
import { test, expect } from './fixtures';
import { accepterContrat, remplirIdentiteCabinet } from './outils/contrat';
import { premiereConnexion, type Invite } from './outils/connexion';
import { declarerVirement, enregistrerReception } from './outils/paiement';
import { allerA, cabinetDedie, capturerFenetre, connecter, fenetre, liberer, type Fenetre } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Lot L6b (E16) : le cabinet a plusieurs.
 *
 * 1. Invitation d'un employe par le superviseur (ecran Equipe) : identifiant attribue, membre "En
 *    attente". Sur une pile qui remet le courriel (Brevo sur le Z440), le mot de passe temporaire
 *    part par courriel et n'est pas lisible par le test : la premiere connexion n'est jouee que si
 *    l'ecran l'affiche (courriel non remis) ; sinon le test le DIT (annotation) et s'arrete la.
 *    Chaque execution envoie donc un courriel reel a une adresse fictive (@jurika.test).
 * 2. Chaine du cabinet (employes crees par le seed, `employes=2`, faute de pouvoir lire le
 *    courriel d'invitation) : forfait Professionnel par le parcours reel (virement declare par le
 *    superviseur, reception enregistree par le super-admin, contrat accepte), puis
 *    - reaffectation du dossier par le superviseur a l'employee A ;
 *    - le superviseur regle une permission du client (trace a son nom) ;
 *    - transfert propose par A a B, accepte par B ; B devient responsable ;
 *    - messagerie interne entre le superviseur et B (fonction du forfait Professionnel).
 * Cabinets SUPERVISEUR dedies. Sans corpus : tournent en CI.
 */
const INVITE = { prenom: 'Nadia', nom: 'Idrissi', courriel: 'nadia.idrissi' };

async function inviter(page: Page, m: typeof INVITE, code: string): Promise<Invite | null> {
  await allerA(page, '/app/team');
  await page.getByRole('button', { name: 'Inviter un membre' }).click();
  const d = page.getByRole('dialog');
  await d.getByLabel('Pr\u00e9nom').fill(m.prenom);
  await d.getByLabel('Nom', { exact: true }).fill(m.nom);
  await d.getByLabel('Adresse \u00e9lectronique professionnelle').fill(`${m.courriel}.${code.toLowerCase()}@jurika.test`);
  await d.getByRole('radio', { name: 'Employ\u00e9' }).check();
  await d.getByRole('button', { name: 'Inviter' }).click();
  await expect(d.getByText(/^Invitation envoy\u00e9e \u00e0 /)).toBeVisible();
  const identifiant = (await d.locator('code').first().innerText()).trim();
  expect(identifiant).toMatch(/@jurika\.ma$/);
  const avis = d.getByText('Courriel non remis : communiquez le mot de passe temporaire');
  const lisible = await avis.isVisible();
  const motDePasseTemporaire = lisible ? (await d.locator('span.font-mono').last().innerText()).trim() : '';
  await d.getByRole('button', { name: 'Terminer' }).click();
  return lisible ? { prenom: m.prenom, nom: m.nom, identifiant, motDePasseTemporaire } : null;
}

test('equipe : invitation d\u2019un employe par le superviseur, premiere connexion si le mot de passe est lisible', async ({ browser }, info) => {
  test.setTimeout(240_000);
  let cabinet: Cabinet | null = null;
  try {
    cabinet = await cabinetDedie('SUPERVISEUR');
    const sup = await fenetre(browser, await connecter(cabinet, 'principal'));
    const inv = await inviter(sup.page, INVITE, cabinet.workspaceCode);
    const ligne = sup.page.getByRole('row').filter({ hasText: `${INVITE.prenom} ${INVITE.nom}` });
    await expect(ligne).toContainText('En attente');
    await capturerFenetre(sup.page, 'equipe-invitation', info);
    if (!inv) {
      info.annotations.push({ type: 'invitation', description: 'courriel remis (Brevo) : mot de passe temporaire non lisible par le test ; premiere connexion non jouee' });
    } else {
      const emp = await fenetre(browser);
      await premiereConnexion(emp.page, cabinet.workspaceCode, inv);
      await allerA(sup.page, '/app/team');
      await expect(ligne).toContainText('Actif');
      expect(await emp.fermer(), 'sentinelle de l\u2019invite').toBeNull();
    }
    expect(await sup.fermer(), 'sentinelle du superviseur').toBeNull();
  } finally {
    await liberer(cabinet);
  }
});

test('equipe : forfait par virement, reaffectation, transfert accepte, permissions, messagerie', async ({ browser, comptes }, info) => {
  test.setTimeout(600_000);
  let cabinet: Cabinet | null = null;
  const fenetres: Fenetre[] = [];
  try {
    cabinet = await cabinetDedie('SUPERVISEUR', { employes: 2 });
    const [A, B] = cabinet.employes;
    const admin = await fenetre(browser, await comptes.session('SUPER_ADMIN'));
    const sup = await fenetre(browser, await connecter(cabinet, 'principal'));
    fenetres.push(admin, sup);

    // 1. Forfait Professionnel (messagerie, deux employes) par le parcours reel.
    await remplirIdentiteCabinet(sup.page);
    await declarerVirement(sup.page, 'professionnel');
    await enregistrerReception(admin.page, cabinet.workspaceCode, 'VIR-FICTIF-EQUIPE');
    await accepterContrat(sup.page, info, 'contrat-equipe.pdf', { exigerComplet: false });

    // 2. Les deux employes du seed se connectent (sessions injectees, double authentification faite).
    const empA = await fenetre(browser, await connecter(cabinet, { employe: 0 }));
    const empB = await fenetre(browser, await connecter(cabinet, { employe: 1 }));
    fenetres.push(empA, empB);
    await allerA(sup.page, '/app/team');
    await expect(sup.page.getByText(`${A.prenom} ${A.nom}`).first()).toBeVisible();
    await capturerFenetre(sup.page, 'equipe-membres', info);

    // 3. Reaffectation par le superviseur : le dossier passe a A.
    await allerA(sup.page, `/data-rooms?dossier=${cabinet.dossierId}`);
    const historique = sup.page.locator('section[aria-labelledby="historique-responsables"]');
    await historique.getByRole('button', { name: 'R\u00e9affecter le dossier' }).click();
    const form = sup.page.locator('section[aria-labelledby="reaffecter-titre"]');
    await form.getByLabel('Nouvel employ\u00e9 responsable').selectOption({ label: `${A.prenom} ${A.nom}` });
    await form.getByLabel('Motif (obligatoire)').fill('R\u00e9partition de la charge (parcours fictif)');
    await form.getByRole('button', { name: 'R\u00e9affecter le dossier' }).click();
    await expect(historique).toContainText(`Responsable actuel : ${A.prenom} ${A.nom}`);

    // 4. Le superviseur regle une permission du client ; la trace porte son nom.
    const acces = sup.page.locator('section[aria-labelledby="acces-client-titre"]');
    const imprimer = acces.getByRole('checkbox', { name: 'Imprimer', exact: true });
    const avant = await imprimer.isChecked();
    const r = sup.page.waitForResponse((x) => /\/dataroom\/.*settings/.test(x.url()) && x.request().method() !== 'GET');
    await imprimer.click();
    expect((await r).status()).toBeLessThan(400);
    await expect(imprimer).toBeChecked({ checked: !avant });
    await expect(acces).toContainText(`Imprimer : ${avant ? 'oui' : 'non'} \u2192 ${avant ? 'non' : 'oui'}`);
    await expect(acces).toContainText('par Demo Superviseur');
    await capturerFenetre(sup.page, 'equipe-reaffectation-permissions', info);

    // 5. Transfert : A le propose a B, B l'accepte.
    await allerA(empA.page, `/data-rooms?dossier=${cabinet.dossierId}`);
    await empA.page.getByRole('button', { name: 'Transf\u00e9rer \u00e0 un coll\u00e8gue' }).click();
    const tiroir = empA.page.getByRole('dialog', { name: /Transf\u00e9rer le dossier/ });
    await tiroir.getByLabel('Nouveau responsable').selectOption({ label: `${B.prenom} ${B.nom} (employ\u00e9)` });
    await tiroir.getByLabel('Motif (facultatif)').fill('Cong\u00e9 prolong\u00e9 (parcours fictif)');
    await tiroir.getByRole('button', { name: 'Envoyer la demande' }).click();
    await expect(tiroir.getByText('Demande de transfert envoy\u00e9e.', { exact: false })).toBeVisible();

    await allerA(empB.page, '/dashboard');
    const recu = empB.page.getByRole('listitem').filter({ hasText: `SARL Demo ${cabinet.workspaceCode}` }).filter({ has: empB.page.getByRole('button', { name: 'Accepter' }) });
    await expect(recu).toBeVisible();
    await recu.getByRole('button', { name: 'Accepter' }).click();
    await expect(recu).toHaveCount(0);
    await allerA(empB.page, `/data-rooms?dossier=${cabinet.dossierId}`);
    await expect(empB.page.locator('section[aria-labelledby="historique-responsables"]')).toContainText(`Responsable actuel : ${B.prenom} ${B.nom}`);
    await capturerFenetre(empB.page, 'equipe-transfert-accepte', info);

    // 6. Messagerie : le superviseur ecrit a B, B repond.
    await allerA(sup.page, '/chat');
    await sup.page.getByTestId('chat-new-conversation').click();
    await sup.page.getByTestId('chat-contact-item').filter({ hasText: `${B.prenom} ${B.nom}` }).click();
    await sup.page.getByTestId('chat-message-input').fill('Bonjour Karim, le dossier vous est confi\u00e9 (parcours fictif).');
    await sup.page.getByRole('button', { name: 'Envoyer' }).click();
    await expect(sup.page.getByRole('log', { name: 'Messages de la conversation' })).toContainText('le dossier vous est confi\u00e9');

    await allerA(empB.page, '/chat');
    await empB.page.getByRole('button', { name: /Demo Superviseur/ }).first().click();
    const filB = empB.page.getByRole('log', { name: 'Messages de la conversation' });
    await expect(filB).toContainText('le dossier vous est confi\u00e9');
    await empB.page.getByTestId('chat-message-input').fill('Bien re\u00e7u, je le reprends.');
    await empB.page.getByRole('button', { name: 'Envoyer' }).click();
    await expect(filB).toContainText('Bien re\u00e7u, je le reprends.');
    await expect(sup.page.getByRole('log', { name: 'Messages de la conversation' })).toContainText('Bien re\u00e7u, je le reprends.', { timeout: 30_000 });
    await capturerFenetre(sup.page, 'equipe-messagerie', info);

    for (const f of fenetres.splice(0)) {
      expect(await f.fermer(), 'sentinelle d\u2019une fenetre').toBeNull();
    }
  } finally {
    for (const f of fenetres) await f.fermer().catch(() => null);
    await liberer(cabinet);
  }
});
