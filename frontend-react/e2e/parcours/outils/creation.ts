import type { Locator, Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type { Sentinelle } from './sentinelle';
import { calme, champ, importer, pdfFictif, pngFictif, valider } from './saisie';

/**
 * Lot L6b (E14) : gestes du parcours de creation (etapes 1 a 6), partages par le parcours des
 * etapes 1 a 5 (sans corpus, CI comprise) et par les parcours complets SARL / SARL AU.
 * Toutes les donnees sont fictives.
 */
export type Forme = 'SARL' | 'SARL_AU';

export interface Personne {
  civilite: 'Monsieur' | 'Madame';
  nom: string;
  prenom: string;
  cin: string;
  naissance: string;
  lieu: string;
  validite: string;
  adresse: string;
}

export interface Gerant extends Personne {
  remuneration: 'non_remunere' | 'decision_collective';
  associe: boolean;
}

export const KARIM: Gerant = {
  civilite: 'Monsieur', nom: 'Benali', prenom: 'Karim', cin: 'BE123456', naissance: '1985-04-12', lieu: 'Casablanca',
  validite: '2031-04-12', adresse: '5 avenue de la D\u00e9monstration, Casablanca', remuneration: 'non_remunere', associe: true,
};
export const SALMA: Gerant = {
  civilite: 'Madame', nom: 'Tazi', prenom: 'Salma', cin: 'BK654321', naissance: '1988-09-03', lieu: 'Casablanca',
  validite: '2032-09-03', adresse: '7 rue des Essais, Casablanca', remuneration: 'decision_collective', associe: false,
};
export const NADIA: Personne = {
  civilite: 'Madame', nom: 'El Idrissi', prenom: 'Nadia', cin: 'BJ112233', naissance: '1990-02-20', lieu: 'Rabat',
  validite: '2032-02-20', adresse: '8 boulevard de la D\u00e9monstration, Rabat',
};

/** Premiere ouverture d'un parcours : le front lit le parcours (404 : aucun), puis le demarre. */
export function tolererPremiereOuverture(s: Sentinelle): void {
  s.tolerer({
    methode: 'GET', chemin: /^\/workflows\/[0-9a-f-]{36}$/, statut: 404,
    motif: 'premiere ouverture du parcours : le front lit le parcours (aucun encore), puis le demarre (POST /start)',
  });
}

/** Nouveau ticket de creation depuis la liste des tickets ; renvoie l'identifiant du ticket. */
export async function nouveauTicketCreation(page: Page, aller: (c: string) => Promise<void>, o: { titre: string; raison: string; forme: Forme }): Promise<string> {
  await aller('/tickets');
  await page.getByRole('button', { name: 'Nouveau ticket' }).click();
  const tiroir = page.getByRole('dialog');
  await tiroir.getByLabel('Titre', { exact: true }).fill(o.titre);
  await tiroir.getByLabel('Type d\u2019op\u00e9ration').selectOption('CREATION');
  await tiroir.getByLabel('Raison sociale').fill(o.raison);
  await tiroir.getByLabel('Forme juridique').selectOption(o.forme);
  await tiroir.getByRole('button', { name: 'Cr\u00e9er et ouvrir le parcours' }).click();
  await expect(page).toHaveURL(/\/workflows\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { name: 'Certificat n\u00e9gatif' })).toBeVisible({ timeout: 60_000 });
  await calme(page);
  return page.url().match(/workflows\/([0-9a-f-]{36})/)?.[1] as string;
}

/** Etape 1 : denomination et certificat negatif (saisie manuelle, la lecture du certificat reste une aide). */
/**
 * ICE fictif UNIQUE par appel (15 chiffres) : un ICE designe une seule societe du cabinet, et les
 * parcours d'un meme cabinet de test tournent en parallele (le serveur refuse le doublon).
 */
export function iceFictif(): string {
  return `00${String(Date.now()).slice(-9)}${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
}

/** Suffixe de denomination unique par execution (une denomination vivante est unique au cabinet). */
export function suffixeUnique(): string {
  return `${String(Date.now()).slice(-5)}${Math.floor(Math.random() * 100)}`;
}

/** Etape 1 ; renvoie l'ICE saisi. */
export async function etape1(page: Page, denomination: string, ice = iceFictif()): Promise<string> {
  await (await champ(page, 'ICE')).fill(ice);
  await (await champ(page, 'D\u00e9nomination (raison sociale)')).fill(denomination);
  await (await champ(page, 'Num\u00e9ro du certificat n\u00e9gatif')).fill('2026/123456');
  await (await champ(page, 'Date de d\u00e9livrance')).fill('2026-10-01');
  await (await champ(page, 'Activit\u00e9 port\u00e9e sur le certificat')).fill('Conseil en organisation et gestion des entreprises');
  await (await champ(page, 'B\u00e9n\u00e9ficiaire du certificat')).fill('Karim Benali');
  await valider(page);
  return ice;
}

/** Etape 2 : siege, bail et certificat de propriete (pieces fictives). */
export async function etape2(page: Page): Promise<void> {
  await (await champ(page, 'Adresse du si\u00e8ge social')).fill('12 rue de la D\u00e9monstration, quartier des Affaires');
  await page.locator('input[name="creation-justificatif-siege"][value="BAIL"]').check();
  await importer(page, 'Cliquez pour importer le fichier', { name: 'bail-fictif.pdf', mimeType: 'application/pdf', buffer: pdfFictif() });
  await importer(page, 'Importer le certificat de propri', { name: 'certificat-propriete-fictif.pdf', mimeType: 'application/pdf', buffer: pdfFictif() });
  await (await champ(page, 'Province ou pr\u00e9fecture')).fill('Pr\u00e9fecture de Casablanca');
  await (await champ(page, 'Commune')).fill('Casablanca');
  await (await champ(page, 'Code postal')).fill('20000');
  await (await champ(page, 'Ville du greffe')).fill('Casablanca');
  await (await champ(page, 'Date de d\u00e9but du contrat')).fill('2026-10-01');
  await (await champ(page, 'Date de fin du contrat')).fill('2029-09-30');
  await valider(page);
}

/** Etape 3 : capital de 10 000 MAD, 100 parts, liberation integrale. */
export async function etape3(page: Page): Promise<void> {
  await (await champ(page, 'Montant total (MAD)')).fill('10000');
  await (await champ(page, 'Dont lib\u00e9r\u00e9 (MAD)')).fill('10000');
  await (await champ(page, 'Nombre total de parts sociales')).fill('100');
  await (await champ(page, 'Date de commencement')).fill('2026-11-01');
  await valider(page);
}

/** Etape 4 : deux activites, saisies au clavier (la saisie a laquelle un 400 a ete signale). */
export async function etape4(page: Page): Promise<void> {
  const activites = await champ(page, 'Activit\u00e9s de la soci\u00e9t\u00e9');
  await activites.click();
  await activites.pressSequentially('Conseil en organisation et gestion des entreprises', { delay: 10 });
  await activites.press('Enter');
  await activites.pressSequentially('Formation professionnelle continue', { delay: 10 });
  await calme(page);
  await valider(page);
}

/**
 * Lecture de la CIN par l'assistant d'extraction : recto (image fictive) importe par le
 * selecteur de fichiers, \u00ab Extraire \u00bb, puis verification humaine : chaque champ lu est remplace
 * par la vraie valeur (ou vide), et \u00ab Appliquer au formulaire \u00bb. L'extraction automatique lit
 * une image fictive au hasard (constate : "TAZI" lu sur un aplat gris) : la verification est le
 * geste attendu de l'employe.
 */
export async function lireCin(page: Page, region: Locator, p: Personne): Promise<void> {
  const choix = page.waitForEvent('filechooser');
  await region.locator('label').filter({ hasText: 'Recto' }).click();
  await (await choix).setFiles({ name: `cin-recto-${p.nom}.png`, mimeType: 'image/png', buffer: pngFictif() });
  const lecture = page.waitForResponse((r) => r.url().includes('/identity/extract'), { timeout: 90_000 });
  await region.getByRole('button', { name: 'Extraire' }).click();
  expect((await lecture).status(), `lecture de la CIN de ${p.prenom} ${p.nom}`).toBeLessThan(400);
  const panneau = region.getByTestId('id-extracted-panel');
  await expect(panneau).toBeVisible();
  const vrais: Record<string, string> = { nom: p.nom, prenom: p.prenom, cin: p.cin };
  for (const t of await panneau.locator('[data-testid^="id-field-"]').all()) {
    const cle = ((await t.getAttribute('data-testid')) ?? '').replace('id-field-', '');
    await t.fill(vrais[cle] ?? '');
  }
  await panneau.getByRole('button', { name: 'Appliquer au formulaire' }).click();
  await calme(page);
}

/** Identite d'une personne au rang n (champs de la zone principale, par leur libelle). */
async function identite(page: Page, p: Personne, n: number): Promise<void> {
  await (await champ(page, 'Civilit\u00e9', n, true)).selectOption({ label: p.civilite });
  await (await champ(page, 'Nom', n, true)).fill(p.nom);
  await (await champ(page, 'Pr\u00e9nom', n, true)).fill(p.prenom);
  await (await champ(page, 'N\u00b0 CIN', n)).fill(p.cin);
  await (await champ(page, 'Date de naissance', n)).fill(p.naissance);
  await (await champ(page, 'Lieu de naissance', n)).fill(p.lieu);
  await (await champ(page, 'Pi\u00e8ce valable jusqu', n)).fill(p.validite);
  await (await champ(page, 'Adresse', n, true)).fill(p.adresse);
}

/** Etape 5 : la gerance (un dirigeant par entree), CIN lue pour chacun ; renvoie l'etape suivante. */
export async function etape5(page: Page, gerants: Gerant[]): Promise<string> {
  for (let i = 1; i < gerants.length; i++) await page.getByRole('button', { name: 'Ajouter un dirigeant' }).click();
  for (const [i, g] of gerants.entries()) {
    await identite(page, g, i);
    await (await champ(page, 'R\u00e9mun\u00e9ration de', i)).selectOption(g.remuneration);
    if (g.associe) await page.getByRole('switch', { name: '\u00c9galement associ\u00e9' }).nth(i).click();
  }
  // Une CIN lue disparait de l'ecran (remplacee par la mention "archivee") : on lit toujours
  // la premiere region restante.
  for (const g of gerants) await lireCin(page, page.getByRole('region', { name: 'Lecture automatique de la CIN' }).first(), g);
  return valider(page);
}

/**
 * Etape 6 : associes. Les dirigeants "egalement associes" sont repris (rang 0). Les autres
 * personnes sont saisies a la main, CIN lue ; les parts sont reparties dans l'ordre.
 */
export async function etape6(page: Page, autres: Personne[], parts: number[]): Promise<string> {
  for (const [k, p] of autres.entries()) {
    const n = k + 1;
    if (!(await page.getByRole('button', { name: `Retirer l\u2019associ\u00e9 ${n + 1}` }).isVisible().catch(() => false))) {
      await page.getByRole('button', { name: 'Ajouter un associ\u00e9' }).click();
    }
    await (await champ(page, 'Genre (accord', n)).selectOption({ label: p.civilite === 'Madame' ? 'F\u00e9minin' : 'Masculin' });
    await identite(page, p, n);
  }
  const lignes = page.getByRole('spinbutton', { name: 'Parts de l\u2019apport 1' });
  for (const [i, nb] of parts.entries()) await lignes.nth(i).fill(String(nb));
  for (const p of autres) await lireCin(page, page.getByRole('region', { name: 'Lecture automatique de la CIN' }).first(), p);
  return valider(page);
}
