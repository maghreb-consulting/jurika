import { expect, type Page } from '@playwright/test';
import { test } from './fixtures';
import { API_URL, BASE_URL } from './outils/env';
import { cabinetDedie, capturerFenetre, fenetre, liberer } from './outils/sessions';
import type { Cabinet } from './outils/comptes';

/**
 * Recette R2 (2026-10-17) : double authentification par SMS, SIMULEE en environnement de test.
 *
 * Premiere connexion d'un compte de cabinet de test (route de seed) : choix du SMS, saisie d'un
 * numero, code recu (simule), codes de recuperation, tableau de bord ; deconnexion ; reconnexion
 * avec le code SMS. Le code est lu par la route reservee aux piles de test
 * GET /api/v1/test/auth-2fa-sms/dernier-code?telephone=... (auth-service, simulation + hors prod) :
 * le test n'a pas acces aux journaux (SMS-DEV-STUB). Exige JURIKA_SMS_SIMULATION=true pour
 * auth-service et JURIKA_TEST_SEED_ENABLED=true pour la passerelle. Jamais les comptes JUR-DEMO2.
 */

/** Numero fictif propre a l'execution (+2126 suivi de 8 chiffres). */
function numeroFictif(): string {
  return `+2126${String(Date.now()).slice(-8)}`;
}

async function dernierCode(telephone: string, different?: string): Promise<string> {
  const limite = Date.now() + 20_000;
  for (;;) {
    const r = await fetch(`${API_URL}/test/auth-2fa-sms/dernier-code?telephone=${encodeURIComponent(telephone)}`);
    if (r.status === 404 && Date.now() > limite) {
      throw new Error('Aucun code SMS simule (404) : la pile doit transmettre JURIKA_SMS_SIMULATION=true a auth-service '
        + '(profil hors prod) et la passerelle router /api/v1/test/** (JURIKA_TEST_SEED_ENABLED=true).');
    }
    if (r.ok) {
      const { code } = (await r.json()) as { code: string };
      if (code !== different) return code;
    }
    if (Date.now() > limite) throw new Error('Aucun NOUVEAU code SMS simule pour ce numero.');
    await new Promise((f) => setTimeout(f, 500));
  }
}

async function identifiants(page: Page, c: Cabinet): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Code du cabinet, les 5 caractères après JUR-').fill(c.workspaceCode.replace(/^JUR-/, ''));
  await page.getByRole('button', { name: 'Continuer' }).click();
  await page.locator('#login-email').fill(c.principal.email);
  await page.locator('#login-password').fill(c.motDePasse);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test('double authentification : activation du SMS simule, deconnexion, reconnexion par code SMS', async ({ browser }, info) => {
  test.setTimeout(180_000);
  let cabinet: Cabinet | null = null;
  try {
    cabinet = await cabinetDedie('EMPLOYE');
    const f = await fenetre(browser);
    const page = f.page;
    const telephone = numeroFictif();

    // 1. Premiere connexion : la double authentification est exigee.
    await identifiants(page, cabinet);
    await expect(page).toHaveURL(/\/account\/2fa-choose/);
    const sms = page.getByRole('button', { name: /^SMS/ });
    await expect(sms, 'option SMS proposee (simulation de la pile de test)').toBeVisible();
    await expect(sms).toContainText('Simulé (test)');
    await expect(page.getByRole('note', { name: /SMS simulé/ })).toBeVisible();
    await sms.click();

    // 2. Numero saisi, code recu (simule), verification.
    await page.getByLabel('Numéro de téléphone mobile').fill(telephone);
    await page.getByRole('button', { name: 'Recevoir le code' }).click();
    await expect(page.getByText('Vérification par SMS')).toBeVisible();
    const codeActivation = await dernierCode(telephone);
    await page.getByLabel('Code reçu par SMS').fill(codeActivation);
    await page.getByRole('button', { name: 'Activer la double authentification' }).click();

    // 3. Codes de recuperation acquittes, tableau de bord.
    await page.getByLabel('J’ai mis mes codes de récupération en lieu sûr.').check();
    await page.getByRole('button', { name: 'Terminer' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    // 4. Deconnexion par le menu du compte (visite guidee fermee si elle s'ouvre).
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
    const passer = page.getByRole('button', { name: 'Passer la visite' });
    if (await passer.isVisible().catch(() => false)) await passer.click();
    await page.getByRole('button', { name: /^Menu du compte/ }).click();
    await page.getByRole('menuitem', { name: 'Déconnexion' }).click();
    await expect(page).toHaveURL(/\/login/);

    // 5. Reconnexion : code SMS exige, nouveau code (simule), acces.
    await identifiants(page, cabinet);
    await expect(page.getByText('Saisissez le code à 6 chiffres que nous venons de vous envoyer par SMS.')).toBeVisible();
    await expect(page.getByRole('note', { name: /SMS simulé/ })).toBeVisible();
    const codeConnexion = await dernierCode(telephone, codeActivation);
    await page.getByLabel('Code de vérification à 6 chiffres').fill(codeConnexion);
    await page.getByRole('button', { name: 'Vérifier et accéder' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await capturerFenetre(page, 'double-authentification-sms', info);

    // 6. Ecran Securite : methode active SMS.
    await page.goto(`${BASE_URL}/settings/security`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('methode-2fa-active')).toContainText('Méthode active : SMS');

    expect(await f.fermer(), 'sentinelle').toBeNull();
  } finally {
    await liberer(cabinet);
  }
});
