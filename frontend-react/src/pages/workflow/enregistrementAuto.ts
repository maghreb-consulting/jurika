import { createContext, useContext } from 'react';
import { tokenStorage } from '../../lib/api';
import type { WorkflowProgress } from '../../types/workflow';

/**
 * Lot L7 (E2) -- AUCUNE SAISIE PERDUE dans les parcours.
 *
 * Etat de l'enregistrement automatique des saisies d'etape, expose a l'ecran par
 * {@link IndicateurEnregistrement} (components/workflow). Le contexte vit dans ce
 * module, a part de `useWorkflow`, pour qu'un ecran rendu sans le moteur (tests,
 * apercu) n'affiche simplement rien.
 */
export type EtatEnregistrement =
  | { type: 'repos' }
  | { type: 'en-cours' }
  | { type: 'enregistre'; a: Date }
  | { type: 'erreur'; message: string };

export const EnregistrementCtx = createContext<EtatEnregistrement | null>(null);

/** Etat courant, ou null hors d'un parcours charge. */
export function useEtatEnregistrement(): EtatEnregistrement | null {
  return useContext(EnregistrementCtx);
}

/** Cadence de lecture des champs de l'etape affichee. */
export const LECTURE_SAISIE_MS = 1000;
/**
 * Une saisie est envoyee des que deux lectures successives sont identiques (pause
 * d'environ une seconde apres la frappe) ; une frappe continue est envoyee au plus
 * tard apres ce delai.
 */
export const ATTENTE_MAX_MS = 5000;

/** Au-dela, `keepalive` est refuse par les navigateurs (limite de 64 Ko). */
const LIMITE_KEEPALIVE_OCTETS = 60_000;

const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8080/api/v1';

/** Refus du serveur lors d'un envoi fiable (message deja redige pour l'ecran). */
export class ErreurEnvoiFiable extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ErreurEnvoiFiable';
  }
}

/**
 * Envoi FIABLE d'un brouillon quand la page se masque ou se ferme
 * (visibilitychange, pagehide) : `fetch` avec `keepalive`, qui survit a la
 * fermeture de l'onglet, contrairement a une requete axios ordinaire. Meme route,
 * meme corps que `workflowService.save`.
 */
export async function envoyerBrouillonFiable(
  ticketId: string,
  currentStep: number,
  data: Record<string, unknown>,
): Promise<WorkflowProgress | null> {
  const corps = JSON.stringify({ currentStep, data });
  const entetes: Record<string, string> = { 'Content-Type': 'application/json' };
  const jeton = tokenStorage.getAccessToken();
  if (jeton) entetes.Authorization = `Bearer ${jeton}`;
  const reponse = await fetch(`${API_URL}/workflows/${ticketId}/save`, {
    method: 'POST',
    headers: entetes,
    body: corps,
    keepalive: new TextEncoder().encode(corps).length < LIMITE_KEEPALIVE_OCTETS,
  });
  if (!reponse.ok) {
    throw new ErreurEnvoiFiable(
      reponse.status === 401
        ? 'Votre session a expiré. Reconnectez-vous, puis réessayez.'
        : `Le serveur a refusé l’enregistrement (code ${reponse.status}).`,
    );
  }
  try {
    return (await reponse.json()) as WorkflowProgress;
  } catch {
    return null;
  }
}
