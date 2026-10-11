import { api } from '../lib/api';

/** Lot L4b (E4, RG-DEM-01) : décisions d'un ticket hors création (référentiel du corpus). */
export interface DecisionProposee {
  matrice: string;
  numero: number;
  section: string | null;
  code: string;
  libelle: string | null;
  typeDecision: string | null;
  matriceRenvoi: string | null;
  numeroRenvoi: number | null;
  choisie: boolean;
}

export interface ChoixDecision {
  matrice: string;
  code: string;
}

export interface DecisionsTicket {
  corpus: boolean;
  versionReferentiel: string | null;
  forme: string | null;
  matrices: string[];
  proposees: DecisionProposee[];
  choisies: ChoixDecision[];
  manques: string[];
}

export const decisionsService = {
  async lire(ticketId: string): Promise<DecisionsTicket> {
    const { data } = await api.get<DecisionsTicket>(`/tickets/${ticketId}/decisions`);
    return data;
  },
  async choisir(ticketId: string, decisions: ChoixDecision[]): Promise<DecisionsTicket> {
    const { data } = await api.put<DecisionsTicket>(`/tickets/${ticketId}/decisions`, { decisions });
    return data;
  },
};

/** Libellé lisible d'une matrice du corpus (nom du classeur, sans invention). */
export const LIBELLES_MATRICES: Record<string, string> = {
  MATRICE_DECISIONS_MODIFICATIONS_SARL: 'Modifications et vie sociale — SARL',
  MATRICE_DECISIONS_MODIFICATIONS_SARL_AU: 'Modifications et vie sociale — SARL AU',
  MATRICE_COMPTES_RESULTAT_SARL: 'Comptes, résultat et distributions — SARL',
  MATRICE_COMPTES_RESULTAT_SARL_AU: 'Comptes, résultat et distributions — SARL AU',
  MATRICE_SUCCURSALE_SARL: 'Succursale et établissements secondaires — SARL',
  MATRICE_SUCCURSALE_SARL_AU: 'Succursale et établissements secondaires — SARL AU',
  MATRICE_SUCCURSALE_SOCIETE_ETRANGERE: "Succursale au Maroc d'une société étrangère",
  MATRICE_DISSOLUTION_LIQUIDATION_SARL: 'Dissolution et liquidation — SARL',
  MATRICE_DISSOLUTION_LIQUIDATION_SARL_AU: 'Dissolution et liquidation — SARL AU',
  MATRICE_INCIDENTS_ASSEMBLEE: "Incidents d'assemblée",
  MATRICE_IMPORT_DOSSIER: "Import d'un dossier existant",
};
