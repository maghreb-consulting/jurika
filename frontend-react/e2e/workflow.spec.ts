import { test, expect } from './fixtures';

/**
 * Sprint 14 ter E2 -- workflow run (1 scenario sur SARL_CREATION).
 *
 * Couvre le Strategy Pattern Sprint 2 : un ticket de type SARL_CREATION
 * declenche le workflow Creation SARL 9 etapes.
 */

test.describe('Workflow -- Creation SARL', () => {

  test('cas 1 : ouvrir un workflow depuis un ticket -> 1ere etape visible', async ({
    page,
    authenticatedUser,
  }) => {
    void authenticatedUser;
    await page.goto('/tickets');

    // Cherche un ticket dont le type est CREATION (SARL/SARL AU). Si absent
    // on cree d'abord un ticket de type CREATION (fallback).
    const row = page.locator('tr, [role="row"]').filter({ has: page.locator('text=/CREATION/i') }).first();
    if (!(await row.isVisible())) {
      // Cree un ticket CREATION via le drawer
      const newTicketBtn = page.getByRole('button', { name: /(nouveau|nouvelle).*(ticket|demande)/i });
      await newTicketBtn.first().click();
      await page.getByLabel(/titre/i).fill('E2E SARL Creation -- ' + Date.now());
      const typeSelect = page.locator('select, [role="combobox"]').first();
      await typeSelect.click();
      await page.getByRole('option', { name: /creation/i }).first().click();
      await page.getByRole('button', { name: /(creer|enregistrer|valider)/i }).click();
      await page.waitForLoadState('networkidle');
    }

    // Clique sur le ticket CREATION pour ouvrir son workflow
    await page.locator('tr, [role="row"]').filter({ has: page.locator('text=/CREATION/i') }).first().click();
    // Bouton "Ouvrir workflow" / "Demarrer" du drawer
    await page.getByRole('button', { name: /(workflow|demarrer|ouvrir)/i }).first().click();

    // On atteint la page Workflow et la 1ere etape est rendue
    await expect(page).toHaveURL(/\/workflows\//, { timeout: 5_000 });
    await expect(page.locator('text=/(etape|step).*1/i').first()).toBeVisible({ timeout: 5_000 });
  });
});
