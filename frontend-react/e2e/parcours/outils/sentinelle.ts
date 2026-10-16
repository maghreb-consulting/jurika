import type { ConsoleMessage, Page, Request, Response } from '@playwright/test';
import { API_URL, BASE_URL } from './env';

/**
 * Lot L6b (E13) : sentinelle commune des parcours de bout en bout.
 *
 * Elle ecoute la page et consigne, avec l'ecran (chemin de la page) ou c'est arrive :
 *  - toute reponse HTTP 4xx ou 5xx d'un hote de la plateforme (front, passerelle, temps reel),
 *    sauf celles qu'un test TOLERE explicitement (methode, chemin, statut, motif nomme) ;
 *  - toute erreur de la console du navigateur (console.error) et toute exception de page
 *    (pageerror), sauf tolerance nommee et justifiee ;
 *  - toute requete reseau en echec (requestfailed).
 *
 * Exclusions fixes, justifiees :
 *  - "Failed to load resource: the server responded with a status of N" : c'est l'echo, dans la
 *    console, d'une reponse HTTP deja consignee (ou toleree) par l'ecoute des reponses ; la
 *    compter deux fois rendrait toute tolerance inoperante ;
 *  - requete annulee "net::ERR_ABORTED" : annulation par le navigateur ou l'application
 *    (navigation, AbortController d'un ecran demonte), pas un echec du serveur.
 *
 * Aucun jeton, mot de passe, code ni secret n'est recopie : les champs sensibles des corps sont
 * masques avant d'etre consignes.
 */
export interface Tolerance {
  methode?: string;
  /** Chemin d'API (sans la racine) ou adresse complete. */
  chemin: RegExp;
  statut: number | number[];
  /** Pourquoi cette reponse est attendue (obligatoire : une tolerance se justifie). */
  motif: string;
}

export interface ToleranceConsole {
  texte: RegExp;
  motif: string;
}

export interface Incident {
  ecran: string;
  genre: 'http' | 'console' | 'exception' | 'reseau';
  resume: string;
  corpsEnvoye?: string;
  reponse?: string;
}

const HOTES = new Set([new URL(BASE_URL).hostname, new URL(API_URL).hostname]);
const ECHO_RESSOURCE = /^Failed to load resource: the server responded with a status of \d+/;
const CLES_SENSIBLES = /("(?:password|motDePasse|currentPassword|newPassword|accessToken|refreshToken|token|secret|otp|totpCode|recoveryCode)"\s*:\s*)"[^"]*"/gi;

export function masquer(texte: string | null | undefined, max = 800): string {
  if (!texte) return '';
  const m = texte.replace(CLES_SENSIBLES, '$1"<masque>"')
    // Un code de double authentification (chiffres) est masque ; un code d'erreur (VALIDATION_ERROR) reste lisible.
    .replace(/("code"\s*:\s*)"\d{4,10}"/g, '$1"<masque>"')
    .replace(/Bearer\s+[\w.-]+/g, 'Bearer <masque>');
  return m.length > max ? `${m.slice(0, max)}... (${m.length} caracteres)` : m;
}

function chemin(url: string): string {
  if (url.startsWith(API_URL)) return url.slice(API_URL.length).split('?')[0] || '/';
  try { return new URL(url).pathname; } catch { return url; }
}

export class Sentinelle {
  readonly incidents: Incident[] = [];
  private readonly tolerances: Tolerance[] = [];
  private readonly tolerancesConsole: ToleranceConsole[] = [];
  private readonly page: Page;
  private readonly enCours: Promise<void>[] = [];

  constructor(page: Page) {
    this.page = page;
    page.on('response', (r) => { this.enCours.push(this.surReponse(r)); });
    page.on('console', (m) => this.surConsole(m));
    page.on('pageerror', (e) => this.incidents.push({ ecran: this.ecran(), genre: 'exception', resume: `${e.name}: ${e.message}` }));
    page.on('requestfailed', (q) => this.surEchec(q));
  }

  tolerer(...t: Tolerance[]): this { this.tolerances.push(...t); return this; }
  tolererConsole(...t: ToleranceConsole[]): this { this.tolerancesConsole.push(...t); return this; }

  /** Chemin de la page en cours (l'ecran) ; "-" avant la premiere navigation. */
  ecran(): string {
    const u = this.page.url();
    try { const p = new URL(u); return p.pathname + p.search; } catch { return u || '-'; }
  }

  private tolere(methode: string, url: string, statut: number): boolean {
    const c = chemin(url);
    return this.tolerances.some((t) => (!t.methode || t.methode === methode)
      && (t.chemin.test(c) || t.chemin.test(url))
      && (Array.isArray(t.statut) ? t.statut.includes(statut) : t.statut === statut));
  }

  private async surReponse(r: Response): Promise<void> {
    const statut = r.status();
    if (statut < 400) return;
    let hote = '';
    try { hote = new URL(r.url()).hostname; } catch { return; }
    if (!HOTES.has(hote)) return;
    const q = r.request();
    if (this.tolere(q.method(), r.url(), statut)) return;
    const ecran = this.ecran();
    let corps = '';
    try { corps = await r.text(); } catch { corps = '(corps illisible)'; }
    this.incidents.push({
      ecran,
      genre: 'http',
      resume: `HTTP ${statut} ${q.method()} ${chemin(r.url())}`,
      corpsEnvoye: masquer(q.postData()),
      reponse: masquer(corps),
    });
  }

  private surConsole(m: ConsoleMessage): void {
    if (m.type() !== 'error') return;
    const texte = m.text();
    if (ECHO_RESSOURCE.test(texte)) return;
    if (this.tolerancesConsole.some((t) => t.texte.test(texte))) return;
    const lieu = m.location();
    this.incidents.push({
      ecran: this.ecran(),
      genre: 'console',
      resume: `console.error : ${masquer(texte, 600)}${lieu?.url ? ` (${chemin(lieu.url)}:${lieu.lineNumber})` : ''}`,
    });
  }

  private surEchec(q: Request): void {
    const motif = q.failure()?.errorText ?? 'inconnu';
    if (motif.includes('ERR_ABORTED')) return;
    if (this.tolerancesConsole.some((t) => t.texte.test(`${motif} ${q.url()}`))) return;
    this.incidents.push({
      ecran: this.ecran(),
      genre: 'reseau',
      resume: `requete en echec (${motif}) ${q.method()} ${chemin(q.url())}`,
      corpsEnvoye: masquer(q.postData()),
    });
  }

  /** Laisse arriver les corps des reponses en cours de lecture. */
  async attendre(): Promise<void> {
    await Promise.allSettled(this.enCours.splice(0));
  }

  /** Rapport lisible des incidents, ou null s'il n'y en a aucun. */
  async rapport(): Promise<string | null> {
    await this.attendre();
    if (this.incidents.length === 0) return null;
    return this.incidents.map((i, n) => [
      `${n + 1}. [ecran ${i.ecran}] ${i.resume}`,
      i.corpsEnvoye ? `   corps envoye : ${i.corpsEnvoye}` : null,
      i.reponse !== undefined ? `   reponse : ${i.reponse || '(vide)'}` : null,
    ].filter(Boolean).join('\n')).join('\n');
  }

  /** Vide la liste (apres un rapport intermediaire). */
  vider(): void { this.incidents.splice(0); }
}

/** Tolerance nommee : le temps reel (socket.io) absent de la pile visee (E2E_SANS_TEMPS_REEL=1). */
export const TEMPS_REEL_ABSENT: ToleranceConsole = {
  texte: /WebSocket connection to .*socket\.io|socket\.io\/\?EIO=/,
  motif: 'service temps reel absent de la pile visee (E2E_SANS_TEMPS_REEL=1) : notifications hors sujet',
};
