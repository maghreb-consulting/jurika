import { api } from '../lib/api';
import type { RecapitulatifCloture, VueDemarches } from '../types/demarche';

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

  /**
   * Lot B — ANNULE UN COCHAGE. Le motif est OBLIGATOIRE : le serveur refuse
   * sans lui.
   *
   * Les deux horodatages — celui du cochage et celui de l'annulation — sont
   * conservés tous les deux au journal de la démarche. Une démarche décochée
   * puis recochée garde la trace des trois événements.
   */
  async decocher(ticketId: string, ordre: number, motif: string): Promise<VueDemarches> {
    const { data } = await api.post<VueDemarches>(
      `/tickets/${ticketId}/demarches/${ordre}/decocher`,
      { motif },
    );
    return data;
  },

  /** Lot B — le récapitulatif du ticket : ce qui a été fait, et ce qui manque. */
  async recapitulatif(ticketId: string): Promise<RecapitulatifCloture> {
    const { data } = await api.get<RecapitulatifCloture>(
      `/tickets/${ticketId}/demarches/recapitulatif`,
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
   * l'acte de nomination (ligne 5), déposer son enregistrement (21) et en
   * retirer l'attestation (22). Une réponse, trois démarches — au lieu de les
   * écarter une par une en retapant le même motif.
   *
   * Lot B — les numéros ont changé avec le parcours du 9 septembre ; c'est le
   * serveur qui les porte, ce commentaire ne fait que le rappeler.
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
