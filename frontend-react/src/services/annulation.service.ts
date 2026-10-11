import { api } from '../lib/api';
import type { TicketStatut } from '../types/ticket';

/**
 * Lot L4a (E4-E6, RG-TKT-03 a 05) : annulation commune a tous les parcours.
 *
 * - reprise : ce que la reprise ferait (statut anterieur retrouve) ou pourquoi elle est
 *   impossible (retrait effectue, statut anterieur inconnu) ; le serveur tranche.
 * - documents d'annulation : la charge du ticket (faits, statut atteint, demarches
 *   interrompues) et les saisies propres a l'annulation.
 */
export interface EtatReprise {
  annule: boolean;
  statutAnterieur: TicketStatut | null;
  possible: boolean;
  motif: string | null;
}

export type Ligne = Record<string, string>;

export interface ChargeAnnulation {
  variables: Record<string, unknown>;
  typeTicket: string;
  dossierId?: string;
}

export interface SaisiesAnnulation {
  variables?: Record<string, string>;
  demarches?: Ligne[];
  pieces?: Ligne[];
}

export const annulationService = {
  async reprise(ticketId: string): Promise<EtatReprise> {
    const { data } = await api.get<EtatReprise>(`/tickets/${ticketId}/reprise`);
    return data;
  },

  async lire(ticketId: string): Promise<ChargeAnnulation> {
    const { data } = await api.get<ChargeAnnulation>(`/workflows/${ticketId}/annulation`);
    return data;
  },

  async enregistrer(ticketId: string, saisies: SaisiesAnnulation): Promise<ChargeAnnulation> {
    const { data } = await api.put<ChargeAnnulation>(`/workflows/${ticketId}/annulation/saisies`, saisies);
    return data;
  },
};
