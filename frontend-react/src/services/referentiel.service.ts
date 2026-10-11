import { api } from '../lib/api';

/** Lot L4b (E2, RG-DEM-01) : import du referentiel des demarches du corpus (super-administrateur). */
export interface ImportReferentiel {
  id: string;
  corpus_version: string;
  statut: 'APPLIQUE' | 'REFUSE';
  lignes: number;
  decisions: number;
  renvois: number;
  motif: string | null;
  importe_le: string;
  rapport: string | null;
}

export interface EtatReferentiel {
  versionActive: string | null;
  imports: ImportReferentiel[];
}

export interface ResultatImport {
  id: string;
  version: string;
  statut: 'APPLIQUE' | 'REFUSE';
  lignes: number;
  decisions: number;
  renvois: number;
  motif: string | null;
}

export interface RapportImport {
  lignesParOnglet?: Record<string, number>;
  total?: number;
  conditionsPhrases?: string[];
  delaisTextuels?: string[];
  erreurs?: string[];
  /** Lot L4b (E3) : parcours de creation du corpus compare aux lignes en service. */
  ecartsCreation?: string[];
}

export const referentielService = {
  async etat(): Promise<EtatReferentiel> {
    const { data } = await api.get<EtatReferentiel>('/tickets/referentiel-demarches');
    return data;
  },
  async importer(): Promise<ResultatImport> {
    const { data } = await api.post<ResultatImport>('/tickets/referentiel-demarches/import');
    return data;
  },
};

export function lireRapport(brut: string | null): RapportImport {
  if (!brut) return {};
  try {
    return JSON.parse(brut) as RapportImport;
  } catch {
    return {};
  }
}
