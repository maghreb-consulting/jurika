import type { Locator, Page } from '@playwright/test';
import { test, expect } from './fixtures';
import { allerA, cabinetDedie, capturerFenetre, connecter, fenetre, liberer, pdfFictif, telecharger } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Lot L6b (E16) : permissions du client (RG-CLI-01). L'employe responsable du dossier regle ce
 * que le client peut faire ; le client, connecte dans sa propre fenetre, ne voit et ne fait que ce
 * qui est permis (telechargement, depot, demandes) ; chaque reglage est trace.
 *
 * Cabinet EMPLOYE dedie (les reglages changent l'etat du dossier). Sans corpus : tourne en CI.
 * Le reglage par le superviseur est joue dans equipe.parcours.ts (cabinet a superviseur).
 */

function accesClient(page: Page): Locator {
  return page.locator('section[aria-labelledby="acces-client-titre"]');
}

/** Coche ou decoche une permission ; attend l'enregistrement (PATCH/PUT des reglages). */
async function regler(page: Page, libelle: string, valeur: boolean): Promise<void> {
  const c = accesClient(page).getByRole('checkbox', { name: libelle, exact: true });
  if ((await c.isChecked()) === valeur) return;
  const r = page.waitForResponse((x) => /\/dataroom\/.*settings/.test(x.url()) && x.request().method() !== 'GET');
  // Case controlee : son etat suit la reponse du serveur (pas de setChecked, qui verifie aussitot).
  await c.click();
  expect((await r).status(), `enregistrement de "${libelle}"`).toBeLessThan(400);
  await expect(c).toBeChecked({ checked: valeur });
}

test('permissions du client : reglees par l\u2019employe responsable, appliquees dans l\u2019espace du client', async ({ browser }, info) => {
  test.setTimeout(240_000);
  let cabinet: Cabinet | null = null;
  try {
    cabinet = await cabinetDedie('EMPLOYE');
    const emp = await fenetre(browser, await connecter(cabinet, 'principal'));
    const cli = await fenetre(browser, await connecter(cabinet, 'client'));

    // 1. L'employe verse un document dans la Data Room du dossier.
    await allerA(emp.page, `/data-rooms?dossier=${cabinet.dossierId}`);
    await emp.page.getByTestId('open-versioned-upload').click();
    const dialogue = emp.page.getByTestId('upload-document-dialog');
    // Type "Statuts" : visible du client par defaut (le type "Autre" ne l'est pas, RG-DR-07).
    await dialogue.getByTestId('upload-new-type').selectOption('STATUTS');
    await dialogue.getByTestId('upload-new-title').fill('Statuts fictifs du parcours');
    const piece = pdfFictif('Statuts fictifs du parcours');
    await dialogue.getByTestId('upload-file-input').setInputFiles({ name: 'statuts-fictifs.pdf', mimeType: 'application/pdf', buffer: piece });
    await dialogue.getByTestId('upload-submit').click();
    await expect(dialogue).toBeHidden();
    await expect(emp.page.getByText('Statuts fictifs du parcours').first()).toBeVisible();

    // 2. Reglages : ni telechargement ni demandes ; depot ouvert.
    await expect(accesClient(emp.page)).toBeVisible();
    await regler(emp.page, 'T\u00e9l\u00e9charger', false);
    await regler(emp.page, 'Envoyer des demandes', false);
    await regler(emp.page, 'D\u00e9poser des documents', true);
    await expect(accesClient(emp.page)).toContainText('T\u00e9l\u00e9charger : oui \u2192 non');

    // 3. Le client : il voit le document, sans pouvoir le telecharger ; il depose ; il ne peut pas
    //    envoyer de demande (et l'ecran le lui dit).
    await allerA(cli.page, '/data-rooms');
    await expect(cli.page.getByText('T\u00e9l\u00e9charger (non autoris\u00e9)')).toBeVisible();
    const ligneDoc = cli.page.getByRole('listitem').filter({ hasText: 'Statuts fictifs du parcours' });
    await expect(ligneDoc.getByRole('button', { name: 'Voir' })).toBeVisible();
    await expect(ligneDoc.getByRole('button', { name: 'T\u00e9l\u00e9charger' })).toHaveCount(0);

    await cli.page.getByRole('tab', { name: /D\u00e9p\u00f4ts du client/ }).click();
    await cli.page.locator('input[type="file"]').setInputFiles({ name: 'releve-fictif.pdf', mimeType: 'application/pdf', buffer: pdfFictif('Releve fictif') });
    await expect(cli.page.getByText('releve-fictif').first()).toBeVisible();

    await expect(cli.page.getByText('Envoyer des demandes (non autoris\u00e9)')).toBeVisible();
    await expect(cli.page.getByRole('button', { name: 'Faire une demande' })).toHaveCount(0);
    await cli.page.getByRole('tab', { name: /Demandes du client/ }).click();
    await expect(cli.page.getByRole('button', { name: 'Nouvelle demande' })).toHaveCount(0);
    await expect(cli.page.getByText(/n\u2019a pas ouvert l\u2019envoi de demandes/)).toBeVisible();
    await capturerFenetre(cli.page, 'client-permissions-restreintes', info);

    // 4. L'employe ouvre le telechargement et les demandes.
    await regler(emp.page, 'T\u00e9l\u00e9charger', true);
    await regler(emp.page, 'Envoyer des demandes', true);

    // 5. Le client telecharge le document (le fichier recu est celui verse) et envoie une demande.
    await allerA(cli.page, '/data-rooms');
    const ligne2 = cli.page.getByRole('listitem').filter({ hasText: 'Statuts fictifs du parcours' });
    const { corps } = await telecharger(cli.page, () => ligne2.getByRole('button', { name: 'T\u00e9l\u00e9charger' }).click(), info);
    expect(corps.equals(piece), 'le document telecharge est celui verse par l\u2019employe').toBe(true);
    await cli.page.getByRole('tab', { name: /Demandes du client/ }).click();
    await cli.page.getByRole('button', { name: 'Nouvelle demande' }).click();
    await cli.page.getByLabel('Sujet').fill('Modification de l\u2019objet social (fictif)');
    await cli.page.getByRole('button', { name: 'Envoyer' }).click();
    await expect(cli.page.getByRole('heading', { name: 'Demande envoy\u00e9e' })).toBeVisible();
    await capturerFenetre(cli.page, 'client-permissions-ouvertes', info);

    // 6. L'employe retrouve le depot et la demande du client.
    await allerA(emp.page, `/data-rooms?dossier=${cabinet.dossierId}`);
    await emp.page.getByRole('tab', { name: /D\u00e9p\u00f4ts du client/ }).click();
    await expect(emp.page.getByText('releve-fictif').first()).toBeVisible();
    await emp.page.getByRole('tab', { name: /Demandes du client/ }).click();
    await expect(emp.page.getByText('Modification de l\u2019objet social (fictif)').first()).toBeVisible();
    await capturerFenetre(emp.page, 'client-permissions-employe', info);

    expect(await cli.fermer(), 'sentinelle du client').toBeNull();
    expect(await emp.fermer(), 'sentinelle de l\u2019employe').toBeNull();
  } finally {
    await liberer(cabinet);
  }
});
