import { deflateSync } from 'node:zlib';
import type { Locator, Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Lot L6b (E14) : gestes de saisie communs aux parcours (champ par son libelle, validation d'une
 * etape, fichiers fictifs generes par le test).
 */

/** Attend que le reseau se calme (les ecrans chargent en plusieurs vagues), borne a 15 s. */
export async function calme(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
}

/** Champ de la zone principale par le debut de son libelle (n-ieme occurrence). */
export async function champ(page: Page, debut: string, n = 0, exact = false): Promise<Locator> {
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

export async function etapeCourante(page: Page): Promise<string> {
  return (await page.locator('[aria-current="step"]').first().getAttribute('aria-label').catch(() => null)) ?? '-';
}

/** Confirme les valeurs proposees (boutons "Confirmer"), puis valide l'etape et attend la suivante. */
export async function valider(page: Page): Promise<string> {
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

/** Fichier depose dans la zone d'import qui porte ce texte (libelle cliquable contenant l'input). */
export async function importer(page: Page, texteZone: string | RegExp, fichier: { name: string; mimeType: string; buffer: Buffer }): Promise<void> {
  const zone = page.locator('main label').filter({ hasText: texteZone }).locator('input[type="file"]').first();
  await zone.setInputFiles(fichier, { timeout: 15_000 });
  await calme(page);
}

/** PDF minimal valide (une page vide), genere par le test. */
export function pdfFictif(): Buffer {
  return Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n'
    + 'trailer << /Root 1 0 R >>\n%%EOF\n', 'latin1');
}

const TABLE_CRC = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(b: Buffer): number {
  let c = 0xffffffff;
  for (const o of b) c = TABLE_CRC[(c ^ o) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function bloc(type: string, donnees: Buffer): Buffer {
  const t = Buffer.from(type, 'latin1');
  const l = Buffer.alloc(4); l.writeUInt32BE(donnees.length);
  const c = Buffer.alloc(4); c.writeUInt32BE(crc32(Buffer.concat([t, donnees])));
  return Buffer.concat([l, t, donnees, c]);
}

/** Image PNG fictive (aplat gris clair, 64 x 40), generee par le test : aucune piece reelle. */
export function pngFictif(largeur = 64, hauteur = 40): Buffer {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(largeur, 0); ihdr.writeUInt32BE(hauteur, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const ligne = Buffer.concat([Buffer.from([0]), Buffer.alloc(largeur * 3, 0xe0)]);
  const brut = Buffer.concat(Array.from({ length: hauteur }, () => ligne));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloc('IHDR', ihdr), bloc('IDAT', deflateSync(brut)), bloc('IEND', Buffer.alloc(0)),
  ]);
}
