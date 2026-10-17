import { expect, type Page } from '@playwright/test';
import { BASE_URL } from './env';
import { codeTotp, fenetreTotp } from './totp';

/**
 * Lot L6b (E16) : premiere connexion d'un membre invite, dans le navigateur, comme lui :
 * code du cabinet, identifiant et mot de passe temporaire, changement du mot de passe, choix de
 * l'application d'authentification (secret lu a l'ecran, code TOTP calcule ici), codes de
 * recuperation acquittes, tableau de bord. Le secret reste en memoire (jamais ecrit).
 */
export interface Invite {
  prenom: string;
  nom: string;
  identifiant: string;
  motDePasseTemporaire: string;
}

/** Mot de passe fictif conforme aux regles (longueur, classes de caracteres). */
export const NOUVEAU_MOT_DE_PASSE = 'Parcours-L6b-2026!';

let derniere = -1;
async function codeFrais(secret: string): Promise<string> {
  if (fenetreTotp() === derniere) await new Promise((r) => setTimeout(r, 30_000 - (Date.now() % 30_000) + 1_000));
  derniere = fenetreTotp();
  return codeTotp(secret);
}

export async function premiereConnexion(page: Page, codeCabinet: string, m: Invite): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Code du cabinet, les 5 caract\u00e8res apr\u00e8s JUR-').fill(codeCabinet.replace(/^JUR-/, ''));
  await page.getByRole('button', { name: 'Continuer' }).click();
  await page.locator('#login-email').fill(m.identifiant);
  await page.locator('#login-password').fill(m.motDePasseTemporaire);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page).toHaveURL(/\/account\/change-password/);
  await page.getByLabel('Mot de passe temporaire (re\u00e7u par courriel)').fill(m.motDePasseTemporaire);
  await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(NOUVEAU_MOT_DE_PASSE);
  await page.getByLabel('Confirmez le nouveau mot de passe').fill(NOUVEAU_MOT_DE_PASSE);
  await page.getByRole('button', { name: 'Enregistrer et continuer' }).click();

  await expect(page).toHaveURL(/\/account\/2fa-choose/);
  await page.getByRole('button', { name: /Application d\u2019authentification/ }).click();
  const secretAffiche = page.locator('code').filter({ hasText: /^[A-Z2-7]{16,}=*$/ }).first();
  await expect(secretAffiche).toBeVisible();
  const secret = (await secretAffiche.innerText()).trim();
  await page.getByLabel('Code \u00e0 6 chiffres affich\u00e9 par l\u2019application').fill(await codeFrais(secret));
  await page.getByRole('button', { name: 'Activer la double authentification' }).click();
  await page.getByLabel('J\u2019ai mis mes codes de r\u00e9cup\u00e9ration en lieu s\u00fbr.').check();
  await page.getByRole('button', { name: 'Terminer' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}
