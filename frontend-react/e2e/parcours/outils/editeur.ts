import { expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { api } from './comptes';
import { allerA } from './sessions';

/**
 * Lot L6b (E16) : identite de l'editeur (Maghreb Consulting) saisie par l'ecran du super-admin
 * (/admin/editeur) pour qu'une facture puisse etre emise.
 *
 * ATTENTION : ce parametre est GLOBAL a la plateforme (billing, /billing/admin/editeur : une seule
 * identite, imprimee sur les factures et les contrats de TOUS les cabinets). Le parcours ne saisit
 * que les mentions VIDES, avec des valeurs visiblement fictives ("FICTIF PARCOURS ..."), et
 * `restaurer` les vide de nouveau a la fin (par le meme ecran). Une mention deja saisie n'est
 * jamais modifiee. Les factures emises pendant le parcours gardent les mentions fictives (elles
 * sont figees a l'emission) ; elles appartiennent au cabinet de test, supprime a la fin.
 *
 * Verrou entre processus de test (dossier cree atomiquement dans le dossier temporaire) : de
 * `completerEditeur` a `restaurerEditeur`, un seul test tient l'identite ; sinon un test la
 * viderait pendant qu'un autre attend sa facture. `restaurerEditeur` le libere toujours : il
 * s'appelle dans un `finally`. Un verrou de plus de 10 minutes (processus tue) est repris.
 */
const VERROU = path.join(os.tmpdir(), 'jurika-parcours-editeur.verrou');

async function prendreVerrou(): Promise<void> {
  const limite = Date.now() + 8 * 60_000;
  for (;;) {
    try { fs.mkdirSync(VERROU); return; } catch { /* tenu par un autre test */ }
    try {
      if (Date.now() - fs.statSync(VERROU).mtimeMs > 10 * 60_000) fs.rmSync(VERROU, { recursive: true, force: true });
    } catch { /* libere entre-temps */ }
    if (Date.now() > limite) throw new Error(`Verrou de l'identite de l'editeur tenu depuis trop longtemps : ${VERROU}`);
    await new Promise((r) => setTimeout(r, 1_000));
  }
}

function rendreVerrou(): void {
  fs.rmSync(VERROU, { recursive: true, force: true });
}
export const MENTIONS_FICTIVES: Record<string, string> = {
  'Raison sociale': 'FICTIF PARCOURS Editeur',
  'Forme juridique': 'SARL',
  'Capital social (en dirhams)': '100000',
  'Adresse du si\u00e8ge': '1 rue fictive des Parcours',
  'Ville du si\u00e8ge': 'Casablanca',
  'Num\u00e9ro du registre du commerce': '999999',
  'Ville du registre du commerce': 'Casablanca',
  ICE: '000000000000099',
  'Identifiant fiscal (IF)': '99999999',
  'Taxe professionnelle (TP)': '99999999',
  'Nom du repr\u00e9sentant l\u00e9gal': 'M. Fictif Parcours',
  'Qualit\u00e9 du repr\u00e9sentant l\u00e9gal': 'g\u00e9rant',
};

/** Saisit les mentions vides ; rend la liste des libelles saisis (a restaurer). */
export async function completerEditeur(page: Page): Promise<string[]> {
  await prendreVerrou();
  await allerA(page, '/admin/editeur');
  await expect(page.getByRole('heading', { name: 'Param\u00e8tres de l\u2019\u00e9diteur' })).toBeVisible();
  await expect(page.getByLabel('Taux de TVA (%)')).toBeVisible();
  const saisis: string[] = [];
  for (const [libelle, valeur] of Object.entries(MENTIONS_FICTIVES)) {
    const champ = page.getByLabel(libelle, { exact: true });
    if ((await champ.inputValue()).trim() !== '') continue;
    await champ.fill(valeur);
    saisis.push(libelle);
  }
  if (saisis.length) {
    const r = page.waitForResponse((x) => x.url().endsWith('/billing/admin/editeur') && x.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    expect((await r).status(), 'PUT /billing/admin/editeur').toBe(200);
    await expect(page.getByRole('note', { name: 'Mentions manquantes' })).toBeHidden();
  }
  return saisis;
}

/** Correspondance libelle de l'ecran -> champ de l'API (pour la restauration de secours). */
const CHAMPS: Record<string, string> = {
  'Raison sociale': 'raisonSociale', 'Forme juridique': 'formeJuridique', 'Capital social (en dirhams)': 'capitalSocial',
  'Adresse du si\u00e8ge': 'siegeSocial', 'Ville du si\u00e8ge': 'ville', 'Num\u00e9ro du registre du commerce': 'rcNumero',
  'Ville du registre du commerce': 'rcTribunal', ICE: 'ice', 'Identifiant fiscal (IF)': 'identifiantFiscal',
  'Taxe professionnelle (TP)': 'taxeProfessionnelle', 'Nom du repr\u00e9sentant l\u00e9gal': 'representantNom',
  'Qualit\u00e9 du repr\u00e9sentant l\u00e9gal': 'representantQualite',
};

/**
 * Vide de nouveau les mentions saisies par le parcours (etat d'origine), par l'ecran ; si la page
 * n'est plus utilisable (test interrompu par son delai), par l'API avec le jeton du super-admin :
 * le parametre est global, il ne doit jamais rester fictif.
 */
export async function restaurerEditeur(page: Page, saisis: string[], jeton: string): Promise<void> {
  try {
    await viderMentions(page, saisis, jeton);
  } finally {
    rendreVerrou();
  }
}

async function viderMentions(page: Page, saisis: string[], jeton: string): Promise<void> {
  if (!saisis.length) return;
  try {
    await allerA(page, '/admin/editeur');
    await expect(page.getByLabel('Taux de TVA (%)')).toBeVisible();
    for (const libelle of saisis) {
      const champ = page.getByLabel(libelle, { exact: true });
      // Garde : on ne vide que la valeur fictive posee par le parcours.
      if ((await champ.inputValue()) === MENTIONS_FICTIVES[libelle]) await champ.fill('');
    }
    const r = page.waitForResponse((x) => x.url().endsWith('/billing/admin/editeur') && x.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    expect((await r).status(), 'PUT /billing/admin/editeur (restauration)').toBe(200);
  } catch (e) {
    console.warn(`Restauration de l'editeur par l'ecran impossible (${(e as Error).message.split('\n')[0]}) : par l'API.`);
    const avant = await api.get<{ valeurs: Record<string, string | null>; tauxTvaPourcent: number }>('/billing/admin/editeur', jeton);
    const v = { ...avant.valeurs };
    for (const libelle of saisis) {
      const k = CHAMPS[libelle];
      if (k && v[k] === MENTIONS_FICTIVES[libelle]) v[k] = null;
    }
    await api.put('/billing/admin/editeur', { ...v, tauxTvaPourcent: avant.tauxTvaPourcent }, jeton);
  }
}
