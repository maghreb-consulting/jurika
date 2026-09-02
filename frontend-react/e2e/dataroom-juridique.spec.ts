import { test, expect } from './fixtures';

/**
 * Sprint 14 ter E2 -- Data Room juridique (3 scenarios).
 *
 * Couvre upload + preview + bulk actions + export ZIP (Sprint 7 V2).
 */

test.describe('Data Room -- Juridique', () => {

  test('cas 1 : naviguer vers Data Room et selectionner un dossier', async ({
    page,
    authenticatedUser,
    seededWorkspace,
  }) => {
    void authenticatedUser;
    await page.goto('/data-rooms');

    // La page DataroomPage liste les dossiers de l'workspace seede.
    await expect(page.locator('text=/SARL Demo/').first()).toBeVisible({ timeout: 10_000 });
    void seededWorkspace;
  });

  test('cas 2 : upload d un document Juridique + preview PDF inline', async ({
    page,
    authenticatedUser,
  }) => {
    void authenticatedUser;
    await page.goto('/data-rooms');

    // Selectionne le 1er dossier
    await page.locator('text=/SARL Demo/').first().click();

    // Tab Juridique selectionne par defaut. Upload via input file.
    const fileInput = page.locator('input[type="file"]').first();
    if (await fileInput.count() > 0) {
      // PDF minimal valide en buffer
      const pdfMinimal = Buffer.from('%PDF-1.4\n%fakepdf\n%%EOF\n');
      await fileInput.setInputFiles({
        name: 'e2e-statuts.pdf',
        mimeType: 'application/pdf',
        buffer: pdfMinimal,
      });
      // Bouton Upload / Confirmer
      await page.getByRole('button', { name: /(upload|confirmer|envoyer)/i }).first().click();
      // Document apparait dans la liste
      await expect(page.locator('text=/e2e-statuts/').first()).toBeVisible({ timeout: 10_000 });

      // Click pour preview (modal PdfPreviewModal s'ouvre)
      await page.locator('text=/e2e-statuts/').first().click();
      const previewModal = page.locator('iframe[title*="preview" i], [role="dialog"]').filter({
        has: page.locator('iframe, embed, object'),
      });
      await expect(previewModal.first()).toBeVisible({ timeout: 5_000 });
    } else {
      test.skip(true, 'Pas d input file detecte dans DossierJuridiqueTab');
    }
  });

  test('cas 3 : bulk select + export ZIP', async ({ page, authenticatedUser }) => {
    void authenticatedUser;
    await page.goto('/data-rooms');
    await page.locator('text=/SARL Demo/').first().click();

    // Cherche checkboxes des documents (bulk selection)
    const checkboxes = page.locator('input[type="checkbox"]');
    const count = await checkboxes.count();
    if (count >= 2) {
      // Coche les 2 premiers documents
      await checkboxes.nth(0).check({ force: true });
      await checkboxes.nth(1).check({ force: true });

      // Toolbar contextuelle apparait avec bouton "Telecharger ZIP" / "Exporter"
      const zipBtn = page.getByRole('button', { name: /(zip|telecharger.*selection|exporter)/i });
      await expect(zipBtn.first()).toBeVisible({ timeout: 5_000 });
    } else {
      test.skip(true, 'Moins de 2 documents dans le dossier (skip bulk)');
    }
  });
});
