import { api } from '../lib/api';

/**
 * Lot L4d (E7) : référentiel des délais de dépôt au greffe par nature d'acte. Lu dans le
 * corpus du cabinet (article cité, « à confirmer » sinon) ; ajustable par le superviseur.
 */
export type StatutDelai = 'CITE_PAR_LE_CORPUS' | 'A_CONFIRMER';

export interface AjustementDelai {
  delaiValeur: number;
  delaiUnite: 'JOURS' | 'MOIS';
  article: string | null;
  note: string | null;
  modifiePar: string | null;
  modifieLe: string | null;
}

export interface NatureActe {
  matrice: string;
  decisionCode: string;
  decisionLibelle: string | null;
  delaiTexte: string | null;
  delaiValeur: number | null;
  delaiUnite: 'JOURS' | 'MOIS' | null;
  article: string | null;
  statut: StatutDelai;
  ajustement: AjustementDelai | null;
}

export interface SaisieDelai {
  delaiValeur: number;
  delaiUnite: 'JOURS' | 'MOIS';
  article?: string;
  note?: string;
}

const base = '/tickets/delais-greffe';

export const delaisGreffeService = {
  async lister(): Promise<NatureActe[]> {
    const { data } = await api.get<NatureActe[]>(base);
    return data;
  },
  async ajuster(matrice: string, decision: string, saisie: SaisieDelai): Promise<void> {
    await api.put(`${base}/${encodeURIComponent(matrice)}/${encodeURIComponent(decision)}`, saisie);
  },
  async retirer(matrice: string, decision: string): Promise<void> {
    await api.delete(`${base}/${encodeURIComponent(matrice)}/${encodeURIComponent(decision)}`);
  },
};
