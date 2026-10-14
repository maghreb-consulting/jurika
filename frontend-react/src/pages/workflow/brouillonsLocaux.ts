import axios from 'axios';
import { isPermissionDenied } from '../../lib/api';
import { workflowService } from '../../services/workflow.service';
import type { WorkflowProgress } from '../../types/workflow';
import { ErreurEnvoiFiable } from './enregistrementAuto';

/**
 * Lot L7 (E12) -- BROUILLON LOCAL des saisies d'un parcours.
 *
 * Decision de l'utilisateur du 2026-10-14 : "Si la session a expire, garde un
 * brouillon local et renvoie-le a la reconnexion. Aucune saisie ne doit se perdre."
 *
 * - Toute saisie d'etape qui n'a pas encore recu l'accuse du serveur est ecrite dans
 *   ce navigateur (localStorage) AVANT tout envoi : un 401, une coupure du reseau, la
 *   fermeture de l'onglet ou la redirection vers la page de connexion ne la perdent pas.
 * - Cle : utilisateur, ticket et cle d'etape (`step3`...). Une entree porte le cabinet,
 *   l'horodatage et la version de la saisie, et `base` : la version du serveur sur
 *   laquelle la saisie a ete faite (forme canonique). Aucun jeton, aucun mot de passe :
 *   seule la saisie des champs de l'etape y figure.
 * - Elle est effacee seulement apres l'accuse du serveur pour cette meme saisie.
 * - Au renvoi (reconnexion, retour du reseau, ouverture du parcours), un brouillon n'est
 *   envoye que si le serveur a encore la version sur laquelle il a ete fait. Sinon
 *   (CONFLIT : l'etape a ete modifiee ailleurs depuis), rien n'est ecrase : le brouillon
 *   est mis a part et l'employe choisit a l'ouverture du parcours la version a garder.
 * - Un brouillon d'un autre utilisateur (ou d'un autre cabinet) n'est jamais lu, envoye
 *   ni affiche.
 */

/** Identite de l'auteur d'un brouillon (utilisateur connecte). */
export interface AuteurBrouillon {
  userId: string;
  workspaceId: string;
}

export interface BrouillonLocal {
  v: 1;
  userId: string;
  workspaceId: string;
  ticketId: string;
  /** Cle de l'etape dans `workflow_progress.data` (`step3`...). */
  cle: string;
  /** Etape envoyee comme `currentStep` (affichee a l'employe). */
  step: number;
  contenu: Record<string, unknown>;
  /** Forme canonique de la version du serveur sur laquelle la saisie a ete faite. */
  base: string;
  /** Horodatage ISO de la derniere ecriture locale. */
  ecritA: string;
  /** Version de la saisie (croissante a chaque ecriture). */
  version: number;
  /** Present quand le serveur a ete modifie ailleurs depuis `base`. */
  conflit?: true;
  /** Identifiant d'un brouillon mis a part (plusieurs conflits possibles pour une etape). */
  idConflit?: string;
}

const PREFIXE = 'jurika_brouillon:v1:';

function cleStockage(userId: string, ticketId: string, cle: string, idConflit?: string): string {
  return `${PREFIXE}${userId}:${ticketId}:${cle}${idConflit ? `:conflit:${idConflit}` : ''}`;
}

/** JSON canonique (cles triees, valeurs indefinies omises) : la base JSONB reordonne les cles. */
export function canonique(valeur: unknown): string {
  return JSON.stringify(trier(valeur ?? null));
}

function trier(v: unknown): unknown {
  if (Array.isArray(v)) return v.map((x) => (x === undefined ? null : trier(x)));
  if (v && typeof v === 'object') {
    const out: Record<string, unknown> = {};
    for (const k of Object.keys(v as Record<string, unknown>).sort()) {
      const x = (v as Record<string, unknown>)[k];
      if (x !== undefined) out[k] = trier(x);
    }
    return out;
  }
  return v;
}

function lireEntree(cleSt: string): BrouillonLocal | null {
  try {
    const brut = localStorage.getItem(cleSt);
    if (!brut) return null;
    const b = JSON.parse(brut) as BrouillonLocal;
    if (b?.v !== 1 || typeof b.userId !== 'string' || typeof b.ticketId !== 'string'
        || typeof b.cle !== 'string' || !b.contenu || typeof b.contenu !== 'object') {
      return null;
    }
    return b;
  } catch {
    return null;
  }
}

function toutesLesCles(): string[] {
  try {
    const out: string[] = [];
    for (let i = 0; i < localStorage.length; i += 1) {
      const k = localStorage.key(i);
      if (k && k.startsWith(PREFIXE)) out.push(k);
    }
    return out;
  } catch {
    return [];
  }
}

/** Brouillons de CET utilisateur dans CE cabinet (ceux d'un autre ne sont jamais rendus). */
export function lireBrouillons(
  auteur: AuteurBrouillon,
  ticketId?: string,
): BrouillonLocal[] {
  const prefixe = ticketId ? `${PREFIXE}${auteur.userId}:${ticketId}:` : `${PREFIXE}${auteur.userId}:`;
  return toutesLesCles()
    .filter((k) => k.startsWith(prefixe))
    .map(lireEntree)
    .filter((b): b is BrouillonLocal =>
      b != null && b.userId === auteur.userId && b.workspaceId === auteur.workspaceId
      && (!ticketId || b.ticketId === ticketId));
}

/** Brouillons mis a part pour conflit (choix de l'employe attendu). */
export function lireConflits(auteur: AuteurBrouillon, ticketId: string): BrouillonLocal[] {
  return lireBrouillons(auteur, ticketId).filter((b) => b.conflit);
}

/**
 * Ecrit (ou remplace) le brouillon de l'etape. `base` n'est pose qu'a la premiere
 * ecriture : il reste la version du serveur sur laquelle la saisie a diverge, jusqu'au
 * prochain accuse. Renvoie faux si le navigateur refuse l'ecriture (stockage plein,
 * navigation privee) : l'appelant le dit a l'ecran.
 */
export function ecrireBrouillon(
  auteur: AuteurBrouillon,
  ticketId: string,
  cle: string,
  step: number,
  contenu: Record<string, unknown>,
  baseServeur: string,
): boolean {
  const k = cleStockage(auteur.userId, ticketId, cle);
  const precedent = lireEntree(k);
  const entree: BrouillonLocal = {
    v: 1,
    userId: auteur.userId,
    workspaceId: auteur.workspaceId,
    ticketId,
    cle,
    step,
    contenu,
    base: precedent?.base ?? baseServeur,
    ecritA: new Date().toISOString(),
    version: (precedent?.version ?? 0) + 1,
  };
  try {
    localStorage.setItem(k, JSON.stringify(entree));
    return true;
  } catch {
    return false;
  }
}

/**
 * Accuse du serveur pour `envoye` (saisie de l'etape `cle`) : le brouillon est efface
 * s'il porte cette meme saisie ; s'il porte une saisie plus recente (frappe pendant
 * l'envoi), il reste et sa base devient la version desormais au serveur.
 */
export function accuserReception(
  auteur: AuteurBrouillon,
  ticketId: string,
  cle: string,
  envoye: Record<string, unknown>,
): void {
  const k = cleStockage(auteur.userId, ticketId, cle);
  const b = lireEntree(k);
  if (!b) return;
  const serveur = canonique(envoye);
  try {
    if (canonique(b.contenu) === serveur) {
      localStorage.removeItem(k);
    } else {
      localStorage.setItem(k, JSON.stringify({ ...b, base: serveur }));
    }
  } catch {
    // stockage indisponible : rien a nettoyer.
  }
}

/** Refus definitif du serveur pour `contenu` : le brouillon qui le porte est efface. */
export function retirerSiIdentique(
  auteur: AuteurBrouillon,
  ticketId: string,
  cle: string,
  contenu: Record<string, unknown>,
): void {
  const k = cleStockage(auteur.userId, ticketId, cle);
  const b = lireEntree(k);
  if (!b || canonique(b.contenu) !== canonique(contenu)) return;
  try {
    localStorage.removeItem(k);
  } catch {
    // stockage indisponible.
  }
}

export function effacerBrouillon(b: BrouillonLocal): void {
  try {
    localStorage.removeItem(cleStockage(b.userId, b.ticketId, b.cle, b.conflit ? b.idConflit : undefined));
  } catch {
    // stockage indisponible.
  }
}

/** Efface tous les brouillons de l'utilisateur (deconnexion volontaire confirmee). */
export function effacerBrouillonsUtilisateur(userId: string): void {
  const prefixe = `${PREFIXE}${userId}:`;
  for (const k of toutesLesCles()) {
    if (!k.startsWith(prefixe)) continue;
    try {
      localStorage.removeItem(k);
    } catch {
      // stockage indisponible.
    }
  }
}

function mettreAPart(b: BrouillonLocal): void {
  // Identifiant propre : un conflit deja en attente pour la meme etape n'est jamais remplace.
  const idConflit = `${Date.now()}-${b.version}`;
  try {
    localStorage.setItem(
      cleStockage(b.userId, b.ticketId, b.cle, idConflit),
      JSON.stringify({ ...b, conflit: true, idConflit }),
    );
    localStorage.removeItem(cleStockage(b.userId, b.ticketId, b.cle));
  } catch {
    // stockage indisponible : le brouillon reste ou il est (jamais envoye en conflit).
  }
}

// ----------------------------------------------------------------------
//  Nature d'un echec d'envoi
// ----------------------------------------------------------------------

/**
 * - `session` : jeton expire et non renouvele (401, ou 403 sans refus de droit) ;
 * - `hors-ligne` : aucune reponse du serveur ;
 * - `transitoire` : 408, 429, 5xx ou erreur inconnue (le brouillon est garde) ;
 * - `refus` : refus definitif du serveur (droit, ticket clos, donnee invalide) : renvoyer
 *   la meme saisie ne changerait rien.
 */
export type NatureEchec = 'session' | 'hors-ligne' | 'transitoire' | 'refus';

export function natureEchec(err: unknown): NatureEchec {
  let status: number | undefined;
  let corps: unknown;
  if (err instanceof ErreurEnvoiFiable) {
    status = err.status;
    corps = err.corps;
  } else if (axios.isAxiosError(err)) {
    if (!err.response) return 'hors-ligne';
    status = err.response.status;
    corps = err.response.data;
  } else if (err instanceof TypeError) {
    // `fetch` rejette par TypeError quand le reseau est coupe.
    return 'hors-ligne';
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return 'hors-ligne';
  if (status === undefined) return 'transitoire';
  if (status === 401) return 'session';
  if (status === 403) return isPermissionDenied(corps) ? 'refus' : 'session';
  if (status === 408 || status === 429 || status >= 500) return 'transitoire';
  return 'refus';
}

// ----------------------------------------------------------------------
//  Renvoi des brouillons
// ----------------------------------------------------------------------

export interface ResultatSynchro {
  /** Progression du serveur apres les renvois (ou telle que lue). */
  progress: WorkflowProgress;
  /** Brouillons en conflit, a presenter a l'employe. */
  conflits: BrouillonLocal[];
  /** Brouillons gardes faute d'avoir pu les envoyer (session, reseau). */
  restants: number;
}

const synchrosEnCours = new Map<string, Promise<ResultatSynchro>>();

/**
 * Renvoie les brouillons de l'utilisateur pour un ticket, AVANT que le parcours ne
 * monte ses champs (WorkflowDataProvider) : la saisie gardee devient la donnee du
 * serveur, que les champs relisent. `connu` : progression deja lue (sinon relue).
 */
export function synchroniserBrouillons(
  auteur: AuteurBrouillon,
  ticketId: string,
  connu?: WorkflowProgress,
  /** Cles d'etape laissees au moteur (etape a l'ecran : sa saisie est plus recente). */
  exclure?: string,
): Promise<ResultatSynchro> {
  const cleSynchro = `${auteur.userId}:${ticketId}:${exclure ?? ''}`;
  const enCours = synchrosEnCours.get(cleSynchro);
  if (enCours) return enCours;
  const p = (async (): Promise<ResultatSynchro> => {
    let progress = connu ?? (await workflowService.get(ticketId));
    let restants = 0;
    for (const b of lireBrouillons(auteur, ticketId)) {
      if (exclure && b.cle === exclure) continue;
      const serveur = canonique(progress.data?.[b.cle]);
      if (serveur === canonique(b.contenu)) {
        // Deja au serveur (accuse perdu, envoi fiable a la fermeture...).
        effacerBrouillon(b);
        continue;
      }
      if (b.conflit) continue;
      if (serveur !== b.base) {
        mettreAPart(b);
        continue;
      }
      try {
        progress = await workflowService.save(ticketId, b.step, { [b.cle]: b.contenu });
        accuserReception(auteur, ticketId, b.cle, b.contenu);
      } catch (err) {
        if (natureEchec(err) === 'refus') {
          // Refus definitif (ticket clos, droit) : le renvoyer a chaque connexion
          // n'y changerait rien.
          effacerBrouillon(b);
        } else {
          restants += 1;
        }
      }
    }
    return { progress, conflits: lireConflits(auteur, ticketId), restants };
  })();
  synchrosEnCours.set(cleSynchro, p);
  void p.finally(() => synchrosEnCours.delete(cleSynchro)).catch(() => undefined);
  return p;
}

/**
 * Tickets dont le parcours est ouvert dans cet onglet : leur moteur renvoie lui-meme
 * ses brouillons (il connait la saisie a l'ecran, plus recente).
 */
const ticketsOuverts = new Map<string, number>();

export function signalerParcoursOuvert(ticketId: string): () => void {
  ticketsOuverts.set(ticketId, (ticketsOuverts.get(ticketId) ?? 0) + 1);
  return () => {
    const n = (ticketsOuverts.get(ticketId) ?? 1) - 1;
    if (n <= 0) ticketsOuverts.delete(ticketId);
    else ticketsOuverts.set(ticketId, n);
  };
}

/**
 * Renvoi de tous les brouillons de l'utilisateur (reconnexion, retour du reseau,
 * demarrage de l'application). Renvoie le nombre de brouillons encore gardes
 * (non envoyes ou en conflit).
 */
export async function renvoyerBrouillons(auteur: AuteurBrouillon): Promise<number> {
  const tickets = new Set(lireBrouillons(auteur).map((b) => b.ticketId));
  let gardes = 0;
  for (const ticketId of tickets) {
    if (ticketsOuverts.has(ticketId)) continue;
    try {
      const r = await synchroniserBrouillons(auteur, ticketId);
      gardes += r.restants + r.conflits.length;
    } catch {
      // Parcours illisible (reseau, droits) : le brouillon reste.
      gardes += lireBrouillons(auteur, ticketId).length;
    }
  }
  return gardes;
}

/** Evenement emis juste avant une deconnexion volontaire : les parcours ouverts ecrivent leur saisie. */
export const EVENEMENT_AVANT_DECONNEXION = 'jurika:avant-deconnexion';
/** Evenement emis a l'ouverture d'une session (connexion, recuperation par code). */
export const EVENEMENT_SESSION_OUVERTE = 'jurika:session-ouverte';

/**
 * Deconnexion volontaire : la saisie des parcours ouverts est ecrite, puis tout est
 * renvoye. Renvoie le nombre de brouillons qui n'ont pas pu partir (l'appelant demande
 * alors confirmation, car la deconnexion les efface).
 */
export async function envoyerAvantDeconnexion(auteur: AuteurBrouillon): Promise<number> {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(EVENEMENT_AVANT_DECONNEXION));
  for (const ticketId of new Set(lireBrouillons(auteur).map((b) => b.ticketId))) {
    try {
      await synchroniserBrouillons(auteur, ticketId);
    } catch {
      // reste garde ; compte ci-dessous.
    }
  }
  return lireBrouillons(auteur).length;
}
