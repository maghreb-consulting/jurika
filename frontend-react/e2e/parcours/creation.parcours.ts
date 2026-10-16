import type { Locator, Page } from '@playwright/test';
import { test, expect, calme } from './fixtures';

/**
 * Lot L6b (E13) : parcours de CREATION d'une SARL, etapes 1 a 5, dans le navigateur, sur la pile
 * reelle, avec la sentinelle (aucun 4xx/5xx, aucune erreur de console).
 *
 * Il garde deux defauts trouves au lot :
 *  - etape 5 en HTTP : crypto.randomUUID absent hors contexte securise (commit 077514a) ;
 *  - etape 5 avec deux gerants aux modes de remuneration differents : l'etape partait avec
 *    gerance.remunerationMode vide, le serveur repondait 400 "Mode de remuneration de la gerance
 *    requis." (corrige au lot : phrase enumeree).
 * Les etapes 1 a 5 ne generent aucun acte : le corpus (reel ou fictif) n'intervient pas, le
 * parcours tourne aussi en CI. Selecteurs repris des reproductions faites a l'ecran sur le Z440.
 */

/** Champ de la zone principale par le debut de son libelle (n-ieme occurrence). */
async function champ(page: Page, debut: string, n = 0, exact = false): Promise<Locator> {
  const ids = await page.evaluate(([d, ex]) => {
    // exact : le libelle est le mot seul, suivi au plus de l'asterisque d'un champ obligatoire.
    const ok = (t: string) => (ex ? t.replace(/\s*\*$/, '') === d : t.startsWith(d));
    const parLibelle = Array.from(document.querySelectorAll('main label'))
      .filter((x) => ok((x.textContent ?? '').trim()) && (x as HTMLLabelElement).htmlFor)
      .map((x) => (x as HTMLLabelElement).htmlFor);
    const parAria = Array.from(document.querySelectorAll('main input, main textarea, main select'))
      .filter((x) => ok(x.getAttribute('aria-label') ?? '') && x.id)
      .map((x) => x.id);
    return [...new Set([...parLibelle, ...parAria])];
  }, [debut, exact] as const);
  expect(ids.length, `champ introuvable : ${debut} (occurrence ${n + 1})`).toBeGreaterThan(n);
  return page.locator(`[id="${ids[n]}"]`);
}

async function etapeCourante(page: Page): Promise<string> {
  return (await page.locator('[aria-current="step"]').first().getAttribute('aria-label').catch(() => null)) ?? '-';
}

/** Confirme les valeurs proposees (boutons "Confirmer"), puis valide l'etape et attend la suivante. */
async function valider(page: Page): Promise<string> {
  for (let i = 0; i < 8; i++) {
    const b = page.getByRole('button', { name: 'Confirmer', exact: true }).first();
    if (!(await b.isVisible().catch(() => false)) || !(await b.isEnabled())) break;
    await b.click();
    await calme(page);
  }
  const avant = await etapeCourante(page);
  const bouton = page.getByRole('button', { name: 'Valider et continuer' });
  await expect(bouton, `validation possible a l'etape ${avant}`).toBeEnabled();
  const reponse = page.waitForResponse((r) => r.url().includes('/execute-step') && r.request().method() === 'POST');
  await bouton.click();
  const r = await reponse;
  expect(r.status(), `etape ${avant} : POST execute-step`).toBeLessThan(400);
  await calme(page);
  await expect.poll(() => etapeCourante(page), { message: `passage a l'etape suivante apres ${avant}` }).not.toBe(avant);
  return etapeCourante(page);
}

function pdfFictif(): Buffer {
  return Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n'
    + 'trailer << /Root 1 0 R >>\n%%EOF\n', 'latin1');
}

test('parcours creation SARL : etapes 1 a 5, deux gerants aux remunerations differentes', async ({ page, entrer, aller, capturer }) => {
  await entrer('EMPLOYE');
  await aller('/tickets');
  await page.getByRole('button', { name: 'Nouveau ticket' }).click();
  const tiroir = page.getByRole('dialog');
  await tiroir.getByLabel('Titre', { exact: true }).fill('Constitution Atlas Parcours');
  await tiroir.getByLabel('Type d\u2019op\u00e9ration').selectOption('CREATION');
  await tiroir.getByLabel('Raison sociale').fill('ATLAS PARCOURS SARL');
  await tiroir.getByRole('button', { name: 'Cr\u00e9er et ouvrir le parcours' }).click();
  await expect(page).toHaveURL(/\/workflows\/[0-9a-f-]{36}$/);
  await calme(page);
  await capturer('creation-etape-1');

  // Etape 1 : denomination et certificat negatif.
  await (await champ(page, 'ICE')).fill('001234567000089');
  await (await champ(page, 'D\u00e9nomination (raison sociale)')).fill('Atlas Parcours SARL');
  await (await champ(page, 'Num\u00e9ro du certificat n\u00e9gatif')).fill('2026/123456');
  await (await champ(page, 'Date de d\u00e9livrance')).fill('2026-10-01');
  await (await champ(page, 'Activit\u00e9 port\u00e9e sur le certificat')).fill('Conseil en organisation et gestion des entreprises');
  await (await champ(page, 'B\u00e9n\u00e9ficiaire du certificat')).fill('Youssef Tazi');
  await valider(page);

  // Etape 2 : siege social, bail (piece fictive).
  await (await champ(page, 'Adresse du si\u00e8ge social')).fill('12 rue de la D\u00e9monstration, quartier des Affaires');
  await page.locator('input[name="creation-justificatif-siege"][value="BAIL"]').check();
  await page.locator('main input[type="file"]').first().setInputFiles({ name: 'bail-fictif.pdf', mimeType: 'application/pdf', buffer: pdfFictif() });
  await calme(page);
  await (await champ(page, 'Province ou pr\u00e9fecture')).fill('Pr\u00e9fecture de Casablanca');
  await (await champ(page, 'Commune')).fill('Casablanca');
  await (await champ(page, 'Code postal')).fill('20000');
  await (await champ(page, 'Ville du greffe')).fill('Casablanca');
  await (await champ(page, 'Date de d\u00e9but du contrat')).fill('2026-10-01');
  await (await champ(page, 'Date de fin du contrat')).fill('2027-09-30');
  await valider(page);

  // Etape 3 : capital.
  await (await champ(page, 'Montant total (MAD)')).fill('10000');
  await (await champ(page, 'Dont lib\u00e9r\u00e9 (MAD)')).fill('10000');
  await (await champ(page, 'Nombre total de parts sociales')).fill('100');
  await (await champ(page, 'Date de commencement')).fill('2026-11-01');
  await valider(page);

  // Etape 4 : activite (la saisie a laquelle l'utilisateur a signale un 400).
  const activites = await champ(page, 'Activit\u00e9s de la soci\u00e9t\u00e9');
  await activites.click();
  await activites.pressSequentially('Conseil en organisation et gestion des entreprises', { delay: 10 });
  await activites.press('Enter');
  await activites.pressSequentially('Formation professionnelle continue', { delay: 10 });
  await calme(page);
  await valider(page);
  await capturer('creation-etape-5');

  // Etape 5 : deux gerants, remunerations differentes.
  await page.getByRole('button', { name: 'Ajouter un dirigeant' }).click();
  const gerants = [
    { nom: 'Benali', prenom: 'Karim', cin: 'BE123456', naissance: '1985-04-12', remuneration: 'non_remunere' },
    { nom: 'Tazi', prenom: 'Salma', cin: 'BK654321', naissance: '1988-09-03', remuneration: 'decision_collective' },
  ];
  for (const [i, g] of gerants.entries()) {
    await (await champ(page, 'Nom', i, true)).fill(g.nom);
    await (await champ(page, 'Pr\u00e9nom', i, true)).fill(g.prenom);
    await (await champ(page, 'N\u00b0 CIN', i)).fill(g.cin);
    await (await champ(page, 'Date de naissance', i)).fill(g.naissance);
    await (await champ(page, 'Lieu de naissance', i)).fill('Casablanca');
    await (await champ(page, 'Pi\u00e8ce valable jusqu', i)).fill('2031-04-12');
    await (await champ(page, 'Adresse', i, true)).fill('5 avenue de la D\u00e9monstration, Casablanca');
    await (await champ(page, 'R\u00e9mun\u00e9ration de', i)).selectOption(g.remuneration);
  }
  const apres = await valider(page);
  expect(apres).toMatch(/Associ/);
  await capturer('creation-etape-6');
});
