import { test, expect } from './fixtures';

/**
 * Sprint 14 ter E2 -- tickets CRUD + transition (2 scenarios).
 *
 * Couvre le State Pattern Sprint 2 (NOUVEAU -> EN_COURS -> CLOTURE).
 */

test.describe('Tickets -- CRUD + transition', () => {

  test('cas 1 : creer un nouveau ticket depuis la page Tickets', async ({ page, authenticatedUser }) => {
    void authenticatedUser;
    await page.goto('/tickets');

    // Bouton "Nouveau ticket" / "Nouveau" en haut a droite
    const newTicketBtn = page.getByRole('button', { name: /(nouveau|nouvelle).*(ticket|demande)/i });
    await newTicketBtn.first().click();

    // Le drawer NewTicketDrawer s'ouvre. Champs : titre / type / dossier
    await page.getByLabel(/titre/i).fill('E2E Ticket auto -- ' + Date.now());
    // Type : on prend l'option par defaut, le formulaire valide a la
    // condition que tous les champs requis soient remplis.
    const typeSelect = page.locator('select, [role="combobox"]').first();
    if (await typeSelect.isVisible()) {
      await typeSelect.click();
      await page.locator('option, [role="option"]').first().click();
    }

    // Submit
    await page.getByRole('button', { name: /(creer|enregistrer|valider)/i }).click();

    // Le ticket apparait dans la liste (toast OK ou redirect vers detail)
    await expect(page.locator('text=/E2E Ticket auto/').first()).toBeVisible({ timeout: 10_000 });
  });

  test('cas 2 : transition d un ticket NOUVEAU -> EN_COURS', async ({ page, authenticatedUser }) => {
    void authenticatedUser;
    await page.goto('/tickets');

    // Cherche un ticket NOUVEAU (badge / chip / cellule)
    const ticketRow = page.locator('tr, [role="row"]').filter({ has: page.locator('text=/NOUVEAU/i') }).first();
    if (await ticketRow.isVisible()) {
      await ticketRow.click();
      // Drawer detail s ouvre -> bouton "Demarrer" / "Prendre en charge"
      await page.getByRole('button', { name: /(demarrer|prendre|commencer)/i }).first().click();
      // Statut visible mis a jour
      await expect(page.locator('text=/EN.?COURS/i').first()).toBeVisible({ timeout: 5_000 });
    } else {
      test.skip(true, 'Aucun ticket NOUVEAU dans le workspace seede -- creer via cas 1 prerequis');
    }
  });
});
