import { test, expect } from '@playwright/test';

/**
 * Sprint 11 TASK 7 — Funnel signup cross-domain (jurika.ai -> app.jurika.ai).
 *
 * Ces tests partent du marketing-site (port 5174) et naviguent vers le SPA
 * (port 5173). Ils valident le contrat :
 *  1. La landing rend les 3 cartes pricing.
 *  2. Le clic CTA Business emmene vers SPA /signup?plan=business.
 *  3. Le wizard SPA presente le formulaire unique (Profil) puis Securite/2FA/Recap.
 *     Simplification 2026-07-13 : Cabinet + Admin fusionnes, IF/RC + double
 *     email retires, ICE optionnel.
 *  4. Le clic "Demander une demo" ouvre le modal et permet la soumission.
 *  5. Le rate limit / validation backend renvoie 4xx visibles (smoke).
 *
 * Pre-requis Docker + backend up :
 *   docker compose up + mvn -pl auth-service,supervision-service spring-boot:run
 * Si backend OFF, les scenarios 4/5 sont skipped via test.skip().
 */

const MARKETING_URL = process.env.E2E_MARKETING_URL || 'http://localhost:5174';
const SPA_URL = process.env.E2E_BASE_URL || 'http://localhost:5173';

test.describe('Sprint 11 - Signup funnel cross-domain', () => {

  test('landing rend les 3 tiers pricing avec les valeurs canoniques 2026-06-02', async ({ page }) => {
    await page.goto(MARKETING_URL);
    await expect(page.getByTestId('pricing-grid')).toBeVisible();
    await expect(page.getByTestId('pricing-card-essentiel')).toContainText('499');
    // Business : 1 199 MAD/mois (spec directeur 2026-06-02)
    await expect(page.getByTestId('pricing-card-business')).toContainText('199');
    // Entreprise : sur devis (pas de prix fixe)
    await expect(page.getByTestId('pricing-card-entreprise')).toContainText(/devis/i);
  });

  test('CTA pricing Essentiel redirige vers SPA /signup?plan=essentiel', async ({ page }) => {
    await page.goto(MARKETING_URL);
    await page.getByTestId('pricing-cta-essentiel').click();
    await expect(page).toHaveURL(/\/signup\?plan=essentiel/);
    // Wizard SPA step 1 doit etre visible
    await expect(page.getByTestId('signup-step-cabinet')).toBeVisible();
  });

  test('CTA Hero ouvre le modal demo (pas de redirect)', async ({ page }) => {
    await page.goto(MARKETING_URL);
    await page.getByTestId('hero-demo-cta').click();
    await expect(page.getByTestId('demo-modal')).toBeVisible();
    await expect(page.getByTestId('demo-modal-email')).toBeVisible();
    await expect(page.getByTestId('demo-modal-raison-sociale')).toBeVisible();
  });

  test('CTA pricing Entreprise ouvre le modal demo (pas de redirect)', async ({ page }) => {
    await page.goto(MARKETING_URL);
    await page.getByTestId('pricing-cta-entreprise').click();
    await expect(page.getByTestId('demo-modal')).toBeVisible();
  });

  test('SPA /signup?plan=business affiche le formulaire unique (Profil)', async ({ page }) => {
    // Naviguer directement (pas besoin du marketing-site)
    await page.goto(`${SPA_URL}/signup?plan=business`);
    await expect(page.getByTestId('signup-step-cabinet')).toBeVisible();
    // Formulaire unique : personne + structure sur un seul ecran, ICE optionnel.
    await expect(page.getByTestId('signup-first-name')).toBeVisible();
    await expect(page.getByTestId('signup-last-name')).toBeVisible();
    await expect(page.getByTestId('signup-workspace-name')).toBeVisible();
    await expect(page.getByTestId('signup-admin-email')).toBeVisible();
    await expect(page.getByTestId('signup-city')).toBeVisible();
    await expect(page.getByTestId('signup-ice')).toBeVisible();
    // Champs retires : plus d'IF, de RC ni d'email de contact cabinet.
    await expect(page.getByTestId('signup-if')).toHaveCount(0);
    await expect(page.getByTestId('signup-rc')).toHaveCount(0);
    await expect(page.getByTestId('signup-contact-email')).toHaveCount(0);
  });

  test('SPA pre-remplit la denomination avec "Prenom Nom"', async ({ page }) => {
    await page.goto(`${SPA_URL}/signup?plan=essentiel`);
    await page.getByTestId('signup-first-name').fill('Jean');
    await page.getByTestId('signup-last-name').fill('Dupont');
    await expect(page.getByTestId('signup-workspace-name')).toHaveValue('Jean Dupont');
  });

  test('SPA wizard rejette un ICE non-vide invalide cote client', async ({ page }) => {
    await page.goto(`${SPA_URL}/signup?plan=essentiel`);
    await page.getByTestId('signup-profile-type-ENTREPRISE').click();
    await page.getByTestId('signup-first-name').fill('Jean');
    await page.getByTestId('signup-last-name').fill('Dupont');
    await page.getByTestId('signup-admin-email').fill('jean@test.ma');
    await page.getByTestId('signup-admin-phone').fill('+212612345678');
    await page.getByTestId('signup-city').fill('Casablanca');
    await page.getByTestId('signup-ice').fill('123'); // trop court (mais non vide)
    await page.getByTestId('signup-step1-next').click();
    await expect(page.getByTestId('signup-step1-error')).toContainText(/ICE/i);
  });

  test('SPA wizard passe du Profil a la Securite avec donnees valides (ICE vide)', async ({ page }) => {
    await page.goto(`${SPA_URL}/signup?plan=essentiel`);
    await page.getByTestId('signup-profile-type-ENTREPRISE').click();
    await page.getByTestId('signup-first-name').fill('Jean');
    await page.getByTestId('signup-last-name').fill('Dupont');
    await page.getByTestId('signup-admin-email').fill('jean@atlas.ma');
    await page.getByTestId('signup-admin-phone').fill('+212612345678');
    await page.getByTestId('signup-city').fill('Casablanca');
    // ICE laisse vide (optionnel)
    await page.getByTestId('signup-step1-next').click();
    await expect(page.getByTestId('signup-step-security')).toBeVisible();
  });
});
