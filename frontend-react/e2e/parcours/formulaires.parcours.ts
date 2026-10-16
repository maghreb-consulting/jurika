import type { Page } from '@playwright/test';
import { test, expect, calme } from './fixtures';
import type { Sentinelle } from './outils/sentinelle';

/**
 * Lot L6b (E13) : formulaires principaux soumis sur la pile reelle, une fois avec des donnees
 * fictives VALIDES (aucun 4xx, effet visible), une fois avec une donnee INVALIDE qui passe le
 * front mais que le serveur refuse : le 400 (tolere nommement pour cette seule requete) doit
 * s'afficher en francais, nommer le champ a corriger, et ne jamais etre un blocage silencieux ni
 * un message technique ("Request failed with status code", "must not be blank", "Donnees
 * invalides" seul).
 */
const TECHNIQUE = /Request failed|status code|must not|must be|size must|Cannot deserialize|JSON parse/i;

async function refusNomme(page: Page, s: Sentinelle, o: { methode: string; chemin: RegExp; champ: RegExp; soumettre: () => Promise<void> }) {
  s.tolerer({ methode: o.methode, chemin: o.chemin, statut: 400, motif: 'donnee invalide soumise expres (controle du message)' });
  const reponse = page.waitForResponse((r) => r.request().method() === o.methode && o.chemin.test(new URL(r.url()).pathname.replace(/^.*\/api\/v1/, '')));
  await o.soumettre();
  const r = await reponse;
  expect(r.status(), 'le serveur refuse la donnee invalide').toBe(400);
  const alerte = page.getByRole('alert').filter({ hasText: o.champ });
  await expect(alerte.first(), `message d'erreur nommant le champ (${o.champ})`).toBeVisible();
  const texte = await alerte.first().innerText();
  expect(texte, 'message en francais, sans texte technique').not.toMatch(TECHNIQUE);
  expect(texte.trim(), 'pas seulement "Donnees invalides"').not.toMatch(/^Donn\u00e9es invalides\.?$/);
}

test.describe('formulaires EMPLOYE', () => {
  test('formulaires mot de passe : refus nomme (12 caracteres), puis nom modifie', async ({ page, entrer, aller, sentinelle, comptes, capturer }) => {
    await entrer('EMPLOYE');
    const cabinet = await comptes.cabinet('EMPLOYE');
    await aller('/profile');
    await page.getByLabel('Mot de passe actuel', { exact: true }).fill(cabinet.motDePasse);
    await page.getByLabel('Nouveau mot de passe', { exact: true }).fill('abc');
    await page.getByLabel('Confirmation', { exact: true }).fill('abc');
    await refusNomme(page, sentinelle, {
      methode: 'POST', chemin: /^\/auth\/change-password$/, // Message du serveur (L6b E13) : "« nouveau mot de passe » : entre 12 et 100 caracteres".
      champ: /nouveau mot de passe\W+entre 12 et 100 caract/i,
      soumettre: () => page.getByRole('button', { name: 'Changer le mot de passe' }).click(),
    });
    // Valide : le prenom.
    const prenom = page.getByLabel('Pr\u00e9nom', { exact: true });
    await prenom.fill('Demo-Parcours');
    const ok = page.waitForResponse((r) => r.url().endsWith('/auth/me/nom') && r.request().method() === 'PATCH');
    await page.getByRole('button', { name: 'Enregistrer' }).first().click();
    expect((await ok).status()).toBeLessThan(400);
    await calme(page);
    await capturer('formulaires-profil');
  });

  test('formulaires nouveau ticket : titre trop long refuse et nomme', async ({ page, entrer, aller, sentinelle }) => {
    await entrer('EMPLOYE');
    await aller('/tickets');
    await page.getByRole('button', { name: 'Nouveau ticket' }).click();
    const tiroir = page.getByRole('dialog');
    await tiroir.getByLabel('Titre', { exact: true }).fill('T'.repeat(201));
    await tiroir.getByLabel('Type d\u2019op\u00e9ration').selectOption('DIVERS');
    await refusNomme(page, sentinelle, {
      methode: 'POST', chemin: /^\/tickets$/, champ: /titre[\s\S]*200 caract/i,
      soumettre: () => tiroir.getByRole('button', { name: /^Cr\u00e9er/ }).click(),
    });
  });
});

test.describe('formulaires CLIENT', () => {
  test('formulaires nouvelle demande : sujet trop long refuse et nomme, puis demande envoyee', async ({ page, entrer, aller, sentinelle, capturer }) => {
    await entrer('CLIENT');
    await aller('/mes-demandes');
    await page.getByRole('button', { name: 'Nouvelle demande' }).click();
    const fenetre = page.getByRole('dialog');
    await fenetre.getByLabel('Sujet', { exact: true }).fill('S'.repeat(201));
    await refusNomme(page, sentinelle, {
      methode: 'POST', chemin: /^\/dataroom\/demandes$/, champ: /sujet[\s\S]*200 caract/i,
      soumettre: () => fenetre.getByRole('button', { name: 'Envoyer' }).click(),
    });
    await fenetre.getByLabel('Sujet', { exact: true }).fill('Attestation de domiciliation');
    const ok = page.waitForResponse((r) => r.url().endsWith('/dataroom/demandes') && r.request().method() === 'POST');
    await fenetre.getByRole('button', { name: 'Envoyer' }).click();
    expect((await ok).status()).toBeLessThan(400);
    await calme(page);
    await capturer('formulaires-demande-client');
  });
});

test.describe('formulaires SUPERVISEUR', () => {
  test('formulaires invitation : prenom trop long refuse et nomme, puis membre invite', async ({ page, entrer, aller, sentinelle, capturer }) => {
    await entrer('SUPERVISEUR');
    await aller('/app/team');
    await page.getByRole('button', { name: 'Inviter un membre' }).click();
    const fenetre = page.getByRole('dialog');
    await fenetre.getByLabel('Pr\u00e9nom', { exact: true }).fill('P'.repeat(81));
    await fenetre.getByLabel('Nom', { exact: true }).fill('Parcours');
    await fenetre.getByLabel('Adresse \u00e9lectronique professionnelle').fill(`invite-${Date.now()}@jurika.test`);
    await refusNomme(page, sentinelle, {
      methode: 'POST', chemin: /^\/auth\/invite-employee$/, champ: /pr\u00e9nom[\s\S]*80 caract/i,
      soumettre: () => fenetre.getByRole('button', { name: 'Inviter', exact: true }).click(),
    });
    await fenetre.getByLabel('Pr\u00e9nom', { exact: true }).fill('Invite');
    const ok = page.waitForResponse((r) => r.url().endsWith('/auth/invite-employee') && r.request().method() === 'POST');
    await fenetre.getByRole('button', { name: 'Inviter', exact: true }).click();
    expect((await ok).status()).toBeLessThan(400);
    await calme(page);
    await capturer('formulaires-invitation');
  });
});

test.describe('formulaires PUBLIC', () => {
  test('formulaires connexion : code de cabinet inconnu, message clair', async ({ page, aller, capturer, sentinelle }) => {
    sentinelle.tolerer({ methode: 'POST', chemin: /^\/auth\/workspace-check$/, statut: 404, motif: 'code de cabinet inconnu saisi expres' });
    await aller('/login');
    await page.getByLabel(/^Code du cabinet/).fill('ZZZZZ');
    await page.getByRole('button', { name: 'Continuer' }).click();
    await calme(page);
    const alerte = page.getByRole('alert');
    await expect(alerte.first(), 'message visible (jamais un blocage silencieux)').toBeVisible();
    const texte = await alerte.first().innerText();
    expect(texte).not.toMatch(TECHNIQUE);
    expect(texte).toMatch(/code/i);
    await capturer('formulaires-connexion-cabinet-inconnu');
  });
});
