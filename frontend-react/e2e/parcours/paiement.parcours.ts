import type { Page, TestInfo } from '@playwright/test';
import { test, expect } from './fixtures';
import { analyserDocument, rapportDocument, textePdf } from './outils/documents';
import { accepterContrat, remplirIdentiteCabinet } from './outils/contrat';
import { completerEditeur, restaurerEditeur } from './outils/editeur';
import { declarerVirement, enregistrerReception, souscrireParCarte } from './outils/paiement';
import { allerA, cabinetDedie, capturerFenetre, connecter, fenetre, liberer, telecharger } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Lot L6b (E16) : paiement du cabinet, de la page de paiement jusqu'a la facture (PDF ouvert et
 * controle), par les deux voies reelles :
 *  - carte, avec le SIMULATEUR du CMI (actif sur le Z440, aucun paiement reel) ;
 *  - virement declare par le superviseur, dont le super-admin enregistre la reception.
 * Cabinet SUPERVISEUR dedie a chaque test (l'abonnement souscrit change son etat).
 *
 * L'identite de l'editeur (parametre GLOBAL, voir outils/editeur.ts) est completee par l'ecran du
 * super-admin si elle est vide (sinon la facture est refusee, mentions nommees), puis remise dans
 * son etat d'origine. Sans corpus : tourne aussi en CI (la voie carte exige le simulateur).
 */

/** Facturation du superviseur : forfait actif, facture telechargee et controlee. */
async function verifierFacture(page: Page, info: TestInfo, capture: string): Promise<void> {
  await allerA(page, '/app/billing');
  await expect(page.getByTestId('billing-page')).toBeVisible();
  await expect(page.getByTestId('billing-abonnement')).toContainText('Professionnel');
  await expect(page.getByTestId('billing-fonctions')).toContainText('Messagerie interne');
  const bouton = page.getByTestId('billing-factures').getByRole('button', { name: /^T\u00e9l\u00e9charger la facture .+ en PDF$/ }).first();
  await expect(bouton, 'facture emise apres le paiement acquis').toBeVisible({ timeout: 30_000 });
  const numero = ((await bouton.getAttribute('aria-label')) ?? '').replace(/^T\u00e9l\u00e9charger la facture | en PDF$/g, '');
  const { nom, corps } = await telecharger(page, () => bouton.click(), info);
  expect(nom).toBe(`Facture-${numero}.pdf`);
  const a = analyserDocument(corps);
  expect(a.genre).toBe('pdf');
  expect(rapportDocument(nom, a)).toBeNull();
  const texte = textePdf(corps).join('\n');
  expect(texte).toContain(numero);
  expect(texte).toContain('Professionnel');
  expect(texte, 'aucune mention de l\u2019editeur manquante').not.toMatch(/non renseign/i);
  await capturerFenetre(page, capture, info);
}

test('paiement par carte (simulateur CMI) : forfait Professionnel, facture PDF', async ({ page, browser, entrer }, info) => {
  test.setTimeout(240_000);
  const admin = await entrer('SUPER_ADMIN');
  let cabinet: Cabinet | null = null;
  let saisis: string[] = [];
  try {
    saisis = await completerEditeur(page);
    info.annotations.push({ type: 'editeur', description: saisis.length ? `mentions fictives saisies puis videes : ${saisis.join(', ')}` : 'identite deja complete, non modifiee' });
    cabinet = await cabinetDedie('SUPERVISEUR');
    const sup = await fenetre(browser, await connecter(cabinet, 'principal'));
    await remplirIdentiteCabinet(sup.page);
    const { texteAcquis } = await souscrireParCarte(sup.page, 'professionnel');
    expect(texteAcquis).toMatch(/TTC/);
    expect(texteAcquis, 'facture emise a la confirmation du CMI').toMatch(/Facture \S+ disponible dans Facturation/);
    // Le paiement acquis ouvre les conditions particulieres du forfait : le superviseur les accepte.
    const contrat = await accepterContrat(sup.page, info, 'contrat-paiement-cmi.pdf');
    expect(contrat).toContain('Professionnel');
    await verifierFacture(sup.page, info, 'paiement-cmi-facture');
    expect(await sup.fermer(), 'sentinelle du superviseur').toBeNull();
  } finally {
    await restaurerEditeur(page, saisis, admin.accessToken);
    await liberer(cabinet);
  }
});

test('paiement par virement enregistre par le super-admin : forfait Professionnel, facture PDF', async ({ page, browser, entrer }, info) => {
  test.setTimeout(240_000);
  const admin = await entrer('SUPER_ADMIN');
  let cabinet: Cabinet | null = null;
  let saisis: string[] = [];
  try {
    saisis = await completerEditeur(page);
    cabinet = await cabinetDedie('SUPERVISEUR');
    const sup = await fenetre(browser, await connecter(cabinet, 'principal'));
    await remplirIdentiteCabinet(sup.page);
    await declarerVirement(sup.page, 'professionnel');
    await capturerFenetre(sup.page, 'paiement-virement-instructions', info);
    await enregistrerReception(page, cabinet.workspaceCode, 'VIR-FICTIF-PARCOURS');
    const contrat = await accepterContrat(sup.page, info, 'contrat-paiement-virement.pdf');
    expect(contrat).toContain('Professionnel');
    expect(contrat).toContain(cabinet.workspaceCode);
    await verifierFacture(sup.page, info, 'paiement-virement-facture');
    expect(await sup.fermer(), 'sentinelle du superviseur').toBeNull();
  } finally {
    await restaurerEditeur(page, saisis, admin.accessToken);
    await liberer(cabinet);
  }
});
