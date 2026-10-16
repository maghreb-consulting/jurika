import { test, expect } from './fixtures';
import { analyserDocument, rapportDocument, textePdf } from './outils/documents';
import { allerA, cabinetDedie, capturerFenetre, connecter, fenetre, liberer, pdfFictif, telecharger } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Lot L6b (E16) : debours d'un ticket (ajout avec justificatif, etat des debours en PDF) et fiches
 * client et dossier telechargees depuis l'ecran de la Data Room. Chaque PDF est ouvert et controle
 * (outils/documents.ts). Cabinet EMPLOYE dedie. Sans corpus (PDF de PDFBox) : tourne en CI.
 */

test('debours : ajout avec justificatif, etat des debours PDF controle', async ({ browser }, info) => {
  test.setTimeout(240_000);
  let cabinet: Cabinet | null = null;
  try {
    cabinet = await cabinetDedie('EMPLOYE');
    const emp = await fenetre(browser, await connecter(cabinet, 'principal'));
    const page = emp.page;

    // Ticket de creation (il ouvre le dossier de la societe et sa Data Room).
    await allerA(page, '/tickets');
    await page.getByRole('button', { name: 'Nouveau ticket' }).click();
    const tiroir = page.getByRole('dialog');
    await tiroir.getByLabel('Titre', { exact: true }).fill('Constitution Debours Parcours');
    await tiroir.getByLabel('Type d\u2019op\u00e9ration').selectOption('CREATION');
    await tiroir.getByLabel('Raison sociale').fill('DEBOURS PARCOURS SARL');
    await tiroir.getByRole('button', { name: 'Cr\u00e9er et ouvrir le parcours' }).click();
    await expect(page).toHaveURL(/\/workflows\/[0-9a-f-]{36}$/);
    const ticketId = page.url().split('/').pop() as string;

    await allerA(page, `/tickets/${ticketId}`);
    const panneau = page.getByTestId('debours-ticket');
    await expect(panneau).toBeVisible();
    await panneau.getByRole('button', { name: 'Ajouter un frais' }).click();
    const form = panneau.getByRole('form', { name: 'Ajouter un d\u00e9bours' });
    await form.getByLabel('Libell\u00e9').fill('D\u00e9p\u00f4t des statuts au greffe');
    await form.getByLabel('Type de frais').selectOption('FRAIS_GREFFE');
    await form.getByLabel('Montant (DH)').fill('1234,50');
    await form.getByLabel('Date du frais').fill('2026-10-12');
    await form.locator('#piece-debours').setInputFiles({ name: 'quittance-greffe.pdf', mimeType: 'application/pdf', buffer: pdfFictif('Quittance fictive') });
    await form.getByRole('button', { name: 'Ajouter le d\u00e9bours' }).click();
    await expect(form).toBeHidden();
    await expect(panneau).toContainText('D\u00e9p\u00f4t des statuts au greffe');
    await expect(panneau.getByTestId('total-debours')).toContainText('1\u202f234,50');
    await expect(panneau.getByTestId('pieces-manquantes')).toHaveCount(0);

    const { nom, corps } = await telecharger(page, () => panneau.getByRole('button', { name: '\u00c9tat des d\u00e9bours (PDF)' }).click(), info);
    expect(nom).toMatch(/^Etat_des_debours_.+\.pdf$/);
    const a = analyserDocument(corps);
    expect(a.genre).toBe('pdf');
    expect(rapportDocument(nom, a)).toBeNull();
    const texte = textePdf(corps).join('\n');
    expect(texte).toContain('D\u00e9p\u00f4t des statuts au greffe');
    expect(texte).toContain('DEBOURS PARCOURS SARL');
    expect(texte).toMatch(/1[\s\u202f\u00a0.]?234,50/);
    expect(texte).toContain('quittance-greffe.pdf');
    await capturerFenetre(page, 'debours-etat', info);
    expect(await emp.fermer(), 'sentinelle de l\u2019employe').toBeNull();
  } finally {
    await liberer(cabinet);
  }
});

test('fiches client et dossier : telechargees depuis la Data Room, PDF controles', async ({ browser }, info) => {
  test.setTimeout(180_000);
  let cabinet: Cabinet | null = null;
  try {
    cabinet = await cabinetDedie('EMPLOYE');
    const emp = await fenetre(browser, await connecter(cabinet, 'principal'));
    const page = emp.page;
    await allerA(page, `/data-rooms?dossier=${cabinet.dossierId}`);
    for (const fiche of ['client', 'dossier'] as const) {
      await page.getByTestId(`open-fiche-${fiche}`).click();
      const tiroir = page.getByRole('dialog', { name: fiche === 'client' ? 'Fiche client' : 'Fiche dossier' });
      await expect(tiroir).toBeVisible();
      await expect(tiroir.getByTestId('fiche-identifiants-table')).toContainText('001234567000001');
      const { nom, corps } = await telecharger(page, () => tiroir.getByTestId('fiche-generer').click(), info);
      expect(nom).toMatch(/\.pdf$/);
      const a = analyserDocument(corps);
      expect(a.genre).toBe('pdf');
      expect(rapportDocument(`fiche-${fiche}.pdf`, a)).toBeNull();
      const texte = textePdf(corps).join('\n');
      expect(texte).toContain(`SARL Demo ${cabinet.workspaceCode}`);
      expect(texte).toContain('001234567000001');
      await expect(tiroir).toContainText('conserv\u00e9e dans la Data Room');
      await capturerFenetre(page, `fiche-${fiche}`, info);
      await tiroir.getByRole('button', { name: /Fermer/ }).first().click();
      await expect(tiroir).toBeHidden();
    }
    expect(await emp.fermer(), 'sentinelle de l\u2019employe').toBeNull();
  } finally {
    await liberer(cabinet);
  }
});
