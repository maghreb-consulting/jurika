import type { TicketType } from './ticket';

export type WorkflowType =
  | 'CREATION'
  | 'IMPORT'
  | 'MODIFICATION'
  | 'DISSOLUTION'
  | 'LIQUIDATION'
  | 'SUCCURSALE_MA'
  | 'SUCCURSALE_ETR'
  | 'FERMETURE_SUCCURSALE'
  | 'PV_AGO';

export type WorkflowStatut = 'EN_COURS' | 'TERMINE' | 'ABANDONNE';

export interface WorkflowProgress {
  id: string;
  workspaceId: string;
  ticketId: string;
  type: WorkflowType;
  currentStep: number;
  totalSteps: number;
  data: Record<string, unknown>;
  statut: WorkflowStatut;
  startedById: string;
  completedAt: string | null;
  updatedAt: string;
}

export interface ExecuteStepResult {
  progress: WorkflowProgress;
  stepData: Record<string, unknown>;
  advanced: boolean;
}

export interface WorkflowCatalog {
  types: Partial<Record<WorkflowType, number>>;
  modificationTypes: string[];
}

export const STEP_LABELS_CREATION: Record<number, string> = {
  1: 'Denomination',
  2: 'Siege social',
  3: 'Capital',
  4: 'Activite',
  5: 'Dirigeants',
  6: 'Associes',
  7: 'Generation IA',
  8: 'Pieces jointes',
  9: 'Synthese',
};

export const TICKET_TO_WORKFLOW: Record<TicketType, WorkflowType> = {
  CREATION: 'CREATION',
  IMPORT: 'IMPORT',
  MODIFICATION: 'MODIFICATION',
  DISSOLUTION: 'DISSOLUTION',
  LIQUIDATION: 'LIQUIDATION',
  SUCCURSALE_MA: 'SUCCURSALE_MA',
  SUCCURSALE_ETR: 'SUCCURSALE_ETR',
  FERMETURE_SUCCURSALE: 'FERMETURE_SUCCURSALE',
  PV_AGO: 'PV_AGO',
};
