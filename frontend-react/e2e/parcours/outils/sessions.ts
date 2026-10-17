import type { Browser, BrowserContext, Download, Page, TestInfo } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { BASE_URL, DOSSIER_CAPTURES } from './env';
import { connecter, creerCabinet, supprimerCabinet, type Cabinet, type RoleSeed, type Session } from './comptes';
import { Sentinelle, TEMPS_REEL_ABSENT } from './sentinelle';

/**
 * Lot L6b (E16) : sessions SECONDAIRES des parcours qui font agir plusieurs personnes dans un
 * meme test (superviseur, puis employe, puis client...) et cabinets DEDIES.
 *
 * Pourquoi un cabinet dedie : les cabinets de la fixture `comptes` sont partages par tous les tests
 * d'un meme processus (un par role). Un parcours qui change l'etat du cabinet (abonnement souscrit,
 * conditions particulieres en attente qui bloquent le superviseur sur l'ecran du contrat, membres
 * invites) le ferait sur le cabinet des autres tests : il cree donc le sien (route de seed) et le
 * supprime a la fin.
 *
 * Chaque session secondaire a son propre contexte de navigateur (localStorage distinct) et sa
 * propre sentinelle ; `fermer()` rend le rapport de la sentinelle (null si aucun incident), que
 * le test affirme.
 */
export interface Fenetre {
  page: Page;
  contexte: BrowserContext;
  sentinelle: Sentinelle;
  /** Ferme le contexte ; rend le rapport de la sentinelle (null : aucun incident). */
  fermer: () => Promise<string | null>;
}

export async function fenetre(browser: Browser, session?: Pick<Session, 'accessToken' | 'refreshToken'>): Promise<Fenetre> {
  const contexte = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'fr-FR',
    timezoneId: 'Africa/Casablanca',
    reducedMotion: 'reduce',
    acceptDownloads: true,
  });
  if (session) {
    await contexte.addInitScript(([a, r]) => {
      if (!window.localStorage.getItem('jurika_access_token')) {
        window.localStorage.setItem('jurika_access_token', a);
        window.localStorage.setItem('jurika_refresh_token', r);
      }
    }, [session.accessToken, session.refreshToken]);
  }
  const page = await contexte.newPage();
  const sentinelle = new Sentinelle(page);
  if (process.env.E2E_SANS_TEMPS_REEL === '1') sentinelle.tolererConsole(TEMPS_REEL_ABSENT);
  return {
    page,
    contexte,
    sentinelle,
    fermer: async () => {
      const r = await sentinelle.rapport();
      await contexte.close();
      return r;
    },
  };
}

/** Cabinet dedie a un parcours (supprime par `liberer`). */
export async function cabinetDedie(role: Exclude<RoleSeed, 'CLIENT'>, options: { employes?: number } = {}): Promise<Cabinet> {
  return creerCabinet(role, options);
}

export async function liberer(c: Cabinet | null | undefined): Promise<void> {
  if (!c) return;
  try { await supprimerCabinet(c); } catch (e) { console.warn(`Cabinet ${c.workspaceCode} non supprime : ${(e as Error).message}`); }
}

export { connecter };

/** Telechargement declenche par un clic : contenu lu, joint au rapport, nom suggere. */
export async function telecharger(page: Page, clic: () => Promise<void>, info: TestInfo): Promise<{ nom: string; corps: Buffer }> {
  const [d] = await Promise.all([page.waitForEvent('download', { timeout: 60_000 }), clic()]);
  return lireTelechargement(d, info);
}

export async function lireTelechargement(d: Download, info: TestInfo): Promise<{ nom: string; corps: Buffer }> {
  const chemin = await d.path();
  const corps = fs.readFileSync(chemin);
  const nom = d.suggestedFilename();
  await info.attach(nom, { body: corps, contentType: nom.endsWith('.pdf') ? 'application/pdf' : 'application/octet-stream' });
  return { nom, corps };
}

/** Capture de fin d'une fenetre secondaire. */
export async function capturerFenetre(page: Page, nom: string, info: TestInfo): Promise<void> {
  fs.mkdirSync(DOSSIER_CAPTURES, { recursive: true });
  const fichier = path.join(DOSSIER_CAPTURES, `${nom.replace(/[^a-zA-Z0-9_-]+/g, '-')}.png`);
  await page.screenshot({ path: fichier, fullPage: true });
  await info.attach(nom, { path: fichier, contentType: 'image/png' });
}

/** Navigation depuis l'origine du front, attente du reseau, visite guidee fermee. */
export async function allerA(page: Page, chemin: string): Promise<void> {
  await page.goto(BASE_URL + chemin, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
  const passer = page.getByRole('button', { name: 'Passer la visite' });
  if (await passer.isVisible().catch(() => false)) {
    await passer.click();
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
  }
}

/** PDF minimal fictif (justificatif). */
export function pdfFictif(texte = 'Piece fictive des parcours'): Buffer {
  const flux = `BT /F1 12 Tf 72 720 Td (${texte}) Tj ET`;
  const objets = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${flux.length} >>\nstream\n${flux}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let out = '%PDF-1.4\n';
  const positions: number[] = [];
  objets.forEach((o, i) => { positions.push(out.length); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = out.length;
  out += `xref\n0 ${objets.length + 1}\n0000000000 65535 f \n`;
  for (const p of positions) out += `${String(p).padStart(10, '0')} 00000 n \n`;
  out += `trailer\n<< /Size ${objets.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}
