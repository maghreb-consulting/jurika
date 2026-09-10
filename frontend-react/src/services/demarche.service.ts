import { api } from '../lib/api';
import type { VueDemarches } from '../types/demarche';

/**
 * Cochage des démarches d'un ticket.
 *
 * Chaque appel renvoie la vue COMPLÈTE et à jour : l'avancement, les points
 * d'attention et l'état de chaque démarche se recalculent côté serveur. Le
 * front n'en recalcule aucun — les règles (justificatif exigé, conditionnelle
 * écartable, obligatoire non) sont vérifiées là où elles engagent.
 */
export const demarcheService = {
  async vue(ticketId: string): Promise<VueDemarches> {
    const { data } = await api.get<VueDemarches>(`/tickets/${ticketId}/demarches`);
    return data;
  },

  /**
   * Coche une démarche. Les `documentIds` désignent des documents DÉJÀ déposés
   * dans la Data Room et rattachés à ce ticket : le serveur vérifie leur type
   * et refuse si un justificatif attendu manque.
   */
  async cocher(ticketId: string, ordre: number, documentIds: string[]): Promise<VueDemarches> {
    const { data } = await api.post<VueDemarches>(
      `/tickets/${ticketId}/demarches/${ordre}/cocher`,
      { documentIds },
    );
    return data;
  },

  async decocher(ticketId: string, ordre: number): Promise<VueDemarches> {
    const { data } = await api.post<VueDemarches>(
      `/tickets/${ticketId}/demarches/${ordre}/decocher`,
      {},
    );
    return data;
  },

  /** Écarte une démarche CONDITIONNELLE. Le motif est obligatoire. */
  async marquerNonApplicable(
    ticketId: string,
    ordre: number,
    motif: string,
  ): Promise<VueDemarches> {
    const { data } = await api.post<VueDemarches>(
      `/tickets/${ticketId}/demarches/${ordre}/non-applicable`,
      { motif },
    );
    return data;
  },

  /**
   * Lot 5 (2026-09-07) — propage la réponse « la gérance est-elle désignée dans
   * les statuts ? » aux TROIS démarches qui portent cette condition : établir
   * l'acte de nomination (9), le faire signer et légaliser (15), l'enregistrer
   * (18). Une réponse, trois démarches — au lieu de les écarter une par une en
   * retapant le même motif.
   *
   * <p>Le serveur ne défait que son propre écartement : un choix de l'employé,
   * ou une démarche déjà cochée, n'est jamais annulé.
   */
  async appliquerConditionGerance(
    ticketId: string,
    statutaire: boolean,
  ): Promise<VueDemarches> {
    const { data } = await api.post<VueDemarches>(
      `/tickets/${ticketId}/demarches/condition-gerance`,
      { statutaire },
    );
    return data;
  },
};
