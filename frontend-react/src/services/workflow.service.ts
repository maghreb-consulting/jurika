import { api } from '../lib/api';
import type { ExecuteStepResult, WorkflowCatalog, WorkflowProgress, WorkflowType } from '../types/workflow';

/**
 * Succursale persistee (2026-07-05) — miroir de SuccursaleDto (ticket-service).
 * `id` est le VRAI UUID DB (remplace l'ancien id logique "RC{rc}@{ville}").
 */
export interface SuccursaleSummary {
  id: string;
  type: 'MA' | 'ETR';
  denomination: string | null;
  activite: string | null;
  adresse: string | null;
  ville: string | null;
  rcSecondaire: string | null;
  directeurNom: string | null;
  directeurPrenom: string | null;
  directeurCin: string | null;
  paysOrigine: string | null;
  statut: 'ACTIVE' | 'FERMEE' | string;
}

/** Partie prenante d'un dossier (associe ou gerant) — champs souples issus de la fiche. */
export interface DossierPartie {
  nom?: string;
  prenom?: string;
  denomination?: string;
  cin?: string;
  nombreParts?: number | string;
  typePersonne?: string;
  [k: string]: unknown;
}

/**
 * Liquidateur nomme lors de la DISSOLUTION et persiste sur le dossier (fiche structuree).
 * Source unique du workflow LIQUIDATION, qui l'affiche en LECTURE SEULE. Absent pour les
 * dossiers dissous avant cette evolution (2026-08-13) -> cas de secours (saisie une fois).
 */
export interface DossierLiquidateur {
  source?: 'BD' | 'EXTERNE' | string;
  civilite?: string;
  prenom?: string;
  nom?: string;
  cin?: string;
  adresse?: string;
  remuneration?: string;
  siege?: string;
  [k: string]: unknown;
}

export interface DossierParties {
  associes: DossierPartie[];
  gerants: DossierPartie[];
  denomination?: string;
  formeJuridique?: string;
  statut?: string;
  /** Liquidateur nomme a la dissolution (lecture seule en liquidation). */
  liquidateur?: DossierLiquidateur | null;
  /** Siege de la liquidation fixe a la dissolution. */
  siegeLiquidation?: string | null;
  /** Date d'effet de la dissolution (`entreprise_dossiers.date_dissolution`), ISO. */
  dateDissolution?: string | null;
  /**
   * Identite de la SOCIETE (2026-08-14) — siege, capital, RC, greffe, parts.
   *
   * Le backend la calculait deja mais ne l'exposait pas ici. Sans elle, les
   * formulaires de seance laissaient vides le « Lieu » de l'assemblee (defaut =
   * siege social) et le « Lieu de signature » (defaut = ville du greffe), alors
   * que la base les connait.
   */
  siegeSocial?: string | null;
  villeGreffe?: string | null;
  capitalSocial?: number | string | null;
  rcNumero?: string | null;
  nombreParts?: number | string | null;
}


/** Lot L3 : une donnee externe attendue par un document du ticket. */
export interface DonneeAttendue {
  templateCode: string;
  workflowCode: string;
  variable: string;
  libelle: string;
  reclameeLe: string;
  /** Presente au magasin du ticket ou a la fiche societe : le document peut etre regenere. */
  recue: boolean;
}


/** Lot L3 : une clause libre d'un document (RG-GEN-05/06). */
export interface ClauseLibre {
  document: string;
  emplacement?: string | null;
  titre: string;
  texte: string;
  resultat?: string | null;
  voixPour?: string | null;
  voixContre?: string | null;
  abstentions?: string | null;
  saisiePar?: string | null;
  saisieLe?: string | null;
}

export const workflowService = {
  async start(ticketId: string, type: WorkflowType): Promise<WorkflowProgress> {
    const { data } = await api.post<WorkflowProgress>(`/workflows/${ticketId}/start`, { type });
    return data;
  },

  async get(ticketId: string): Promise<WorkflowProgress> {
    const { data } = await api.get<WorkflowProgress>(`/workflows/${ticketId}`);
    return data;
  },

  async save(ticketId: string, currentStep: number, payload: Record<string, unknown>): Promise<WorkflowProgress> {
    const { data } = await api.post<WorkflowProgress>(`/workflows/${ticketId}/save`, {
      currentStep,
      data: payload,
    });
    return data;
  },

  async executeStep(ticketId: string, step: number, payload: Record<string, unknown>): Promise<ExecuteStepResult> {
    const { data } = await api.post<ExecuteStepResult>(`/workflows/${ticketId}/execute-step`, { step, payload });
    return data;
  },

  /**
   * Lot L3 (regle des variables) : donnees externes (RC, ICE, IF...) que les documents
   * du ticket attendent d'un organisme, et si elles sont arrivees.
   */
  async donneesAttendues(ticketId: string): Promise<DonneeAttendue[]> {
    const { data } = await api.get<DonneeAttendue[]>(`/workflows/${ticketId}/donnees-attendues`);
    return data;
  },

  /** Lot L3 (RG-GEN-05/06) : clauses libres du ticket (auteur, date). */
  async clausesLibres(ticketId: string): Promise<ClauseLibre[]> {
    const { data } = await api.get<ClauseLibre[]>(`/workflows/${ticketId}/clauses-libres`);
    return data;
  },

  /** Lot L3 : remplace les clauses libres du ticket (employe en charge seulement). */
  async remplacerClausesLibres(ticketId: string, clauses: ClauseLibre[]): Promise<ClauseLibre[]> {
    const { data } = await api.put<ClauseLibre[]>(`/workflows/${ticketId}/clauses-libres`, clauses);
    return data;
  },

  async catalog(): Promise<WorkflowCatalog> {
    const { data } = await api.get<WorkflowCatalog>(`/workflows/catalog`);
    return data;
  },

  /**
   * Parties prenantes (associes + gerants) d'un dossier, lues depuis la fiche
   * structuree cote backend. Sert a peupler le selecteur « associes a convoquer »
   * (etape 1 Modification) DES la selection de la societe, sans re-saisie
   * d'identite. Renvoie des tableaux vides si la fiche est absente.
   */
  async getDossierParties(dossierId: string): Promise<DossierParties> {
    const { data } = await api.get<DossierParties>(
      `/workflows/dossiers/${dossierId}/parties`,
    );
    return data;
  },

  /**
   * Liste les succursales ACTIVE d'une societe mere (endpoint ticket-service,
   * scope workspace + responsable). Sert l'auto-remplissage a la fermeture.
   * Renvoie [] si la mere n'a aucune succursale en base (cas legacy) -> le front
   * bascule sur la saisie manuelle.
   */
  async listSuccursales(parentDossierId: string): Promise<SuccursaleSummary[]> {
    const { data } = await api.get<SuccursaleSummary[]>(
      `/dossiers/${parentDossierId}/succursales`,
    );
    return data;
  },

  /**
   * P2 2026-06-04 — Enregistre/maj une piece jointe persistante cross-step.
   * Le `code` est unique par ticket (CN, JUSTIFICATIF_SIEGE, CIN_DIRIGEANTS,...).
   * Re-poster le meme code met a jour la metadonnee sans dupliquer.
   */
  async registerPiece(ticketId: string, piece: {
    code: string;
    label: string;
    filename?: string;
    sizeBytes?: number;
    contentType?: string;
    uploadedAtStep?: number;
  }): Promise<WorkflowProgress> {
    const { data } = await api.post<WorkflowProgress>(`/workflows/${ticketId}/pieces`, piece);
    return data;
  },

  /** P2 — Supprime une piece du registre (UX "Retirer"). */
  async unregisterPiece(ticketId: string, code: string): Promise<WorkflowProgress> {
    const { data } = await api.delete<WorkflowProgress>(
      `/workflows/${ticketId}/pieces/${encodeURIComponent(code)}`,
    );
    return data;
  },
};

export const aiService = {
  async extractCn(file: File): Promise<Record<string, unknown>> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await api.post<Record<string, unknown>>('/ai/extract-cn', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  async extractCin(file: File): Promise<Record<string, unknown>> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await api.post<Record<string, unknown>>('/ai/extract-cin', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  /**
   * P4 — Extraction générique 2 étages (OCR + LLM) pour n'importe quel type de document
   * supporté par le registre backend (voir `ai-service/docs/LLM_EXTRACTION.md`).
   *
   * Types V1 : `CIN`, `CERTIFICAT_NEGATIF`, `STATUTS_SARL`, `RC_IMMATRICULATION`,
   * `IF_DECLARATION`, `JUSTIFICATIF_SIEGE`.
   *
   * Réponse : { type, fields, confidence, source, provider, model, degraded, extractionMode, warnings }.
   * Si `degraded=true`, l'UI doit basculer en saisie 100% manuelle (le composant
   * `OcrSuggestionsPanel` existant est compatible — il est non destructif).
   */
  async extract(file: File, docType: string): Promise<Record<string, unknown>> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await api.post<Record<string, unknown>>(
      `/ai/extract?type=${encodeURIComponent(docType)}`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },
};
