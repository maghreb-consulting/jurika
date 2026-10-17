import { test, expect } from './fixtures';
import { accepterContrat, remplirIdentiteCabinet } from './outils/contrat';
import { completerEditeur, restaurerEditeur } from './outils/editeur';
import { allerA, cabinetDedie, capturerFenetre, connecter, fenetre, liberer } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Lot L6b (E16) : contrat d'abonnement. Le super-admin ouvre des conditions particulieres pour un
 * cabinet (ecran /admin/contrats) ; le superviseur, bloque sur l'ecran du contrat tant qu'il ne
 * les a pas acceptees, les accepte en ligne ; l'exemplaire PDF est ouvert et controle ; le
 * super-admin voit le contrat "accepte".
 *
 * Refus d'une nouvelle version des conditions generales : NON joue. Il faudrait PUBLIER une
 * version (ecran du super-admin) : elle vaut pour TOUS les cabinets de la plateforme (chaque
 * superviseur doit y repondre), et aucun ecran ne la retire. Parametre partage et irreversible :
 * hors d'un parcours de test (voir le rapport du lot).
 *
 * Mentions de l'editeur (parametre GLOBAL) : completees si vides, puis remises (outils/editeur.ts).
 */
test('contrat : conditions particulieres ouvertes par le super-admin, acceptees par le superviseur, exemplaire PDF', async ({ page, browser, entrer }, info) => {
  test.setTimeout(240_000);
  const admin = await entrer('SUPER_ADMIN');
  let cabinet: Cabinet | null = null;
  let saisis: string[] = [];
  try {
    saisis = await completerEditeur(page);
    cabinet = await cabinetDedie('SUPERVISEUR');
    const sup = await fenetre(browser, await connecter(cabinet, 'principal'));
    await remplirIdentiteCabinet(sup.page);

    // Super-admin : conditions particulieres du cabinet.
    await allerA(page, '/admin/contrats');
    const ligne = page.getByRole('row').filter({ hasText: cabinet.workspaceCode });
    await expect(ligne).toHaveCount(1);
    await ligne.getByRole('button', { name: 'Saisir des conditions' }).click();
    const form = page.locator(`[aria-label="Conditions particuli\u00e8res de Cabinet Demo ${cabinet.workspaceCode}"]`);
    await expect(form).toBeVisible();
    await form.getByLabel('Forfait *').selectOption('PROFESSIONNEL');
    await form.getByLabel('Rythme de paiement *').selectOption('ANNUEL');
    await expect(form.getByLabel('Prix HT de l\u2019\u00e9ch\u00e9ance (DH)')).not.toHaveValue('');
    await form.getByLabel('Mode de paiement').selectOption('VIREMENT');
    const r = page.waitForResponse((x) => x.url().includes(`/billing/admin/contrats/${cabinet?.workspaceId}/conditions`));
    await form.getByRole('button', { name: 'Enregistrer les conditions' }).click();
    expect((await r).status(), 'POST conditions').toBe(200);
    await expect(ligne).toContainText('Professionnel');

    // Superviseur : seul l'ecran du contrat est accessible, jusqu'a l'acceptation.
    await allerA(sup.page, '/dashboard');
    await expect(sup.page).toHaveURL(/\/app\/contrat/);
    const texte = await accepterContrat(sup.page, info, 'contrat-conditions-admin.pdf');
    expect(texte).toContain('Professionnel');
    expect(texte).toMatch(/Mode de paiement choisi\s+virement bancaire/);
    expect(texte).toContain(cabinet.workspaceCode);
    await capturerFenetre(sup.page, 'contrat-accepte', info);
    await allerA(sup.page, '/dashboard');
    await expect(sup.page).toHaveURL(/\/dashboard/);
    expect(await sup.fermer(), 'sentinelle du superviseur').toBeNull();

    // Super-admin : le contrat du cabinet est accepte, son historique liste l'acceptation.
    await allerA(page, '/admin/contrats');
    await expect(page.getByRole('row').filter({ hasText: cabinet.workspaceCode })).toContainText(/Accept/);
  } finally {
    await restaurerEditeur(page, saisis, admin.accessToken);
    await liberer(cabinet);
  }
});
