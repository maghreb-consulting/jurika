// V9 — Transfert de dossier entre employes (demande/acceptation + direct superviseur).

export type TransfertStatut = 'EN_ATTENTE' | 'ACCEPTE' | 'REFUSE' | 'ANNULE';

export interface DossierTransfer {
  id: string;
  dossierId: string;
  raisonSociale: string | null;
  fromUserId: string;
  toUserId: string;
  statut: TransfertStatut;
  direct: boolean;
  motif: string | null;
  createdAt: string;
  decidedAt: string | null;
}
