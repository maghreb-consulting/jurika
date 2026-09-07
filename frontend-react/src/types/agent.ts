/**
 * Lot IA-1 — Types miroir de l'agent copilote (backend
 * {@code AgentDtos.java} dans dashboard-service). L'agent PROPOSE, l'humain VALIDE :
 * ces objets ne portent que des suggestions (aucune action irreversible).
 */

export interface EcheanceSignal {
  /** DEADLINE = echeance calculee du ticket ; DELAI_LEGAL = delai du guide porte par une demarche. */
  source: 'DEADLINE' | 'DELAI_LEGAL' | string;
  refId: string;
  intitule: string;
  date: string | null; // ISO date (yyyy-MM-dd)
  severite: 'INFO' | 'WARNING' | 'CRITICAL' | string;
  depassee: boolean;
  joursRestants: number; // negatif si depassee
  dossierId: string;
  dossierNom: string;
  lien: string;
}

export interface TacheSignal {
  type: 'TICKET' | 'DEMANDE';
  refId: string;
  reference: string | null;
  sujet: string;
  statut: string;
  ancienneteJours: number;
  dossierId: string;
  dossierNom: string;
  lien: string;
}

export interface AgentCounts {
  echeances: number;
  echeancesDepassees: number;
  tickets: number;
  demandes: number;
}

export interface AgentSignals {
  echeances: EcheanceSignal[];
  resteAFaire: TacheSignal[];
  counts: AgentCounts;
}

export type Urgence = 'CRITIQUE' | 'HAUTE' | 'MOYENNE' | 'BASSE';

export interface PlanItem {
  ordre: number;
  urgence: Urgence | string;
  action: string;
  justification: string;
  type: 'TICKET' | 'DEMANDE' | 'ECHEANCE' | string;
  targetId: string;
  dossierId: string;
  lien: string;
}

export interface AgentBriefing {
  id: string;
  workspaceId: string;
  employeeId: string;
  summary: string;
  texteLlm: string | null; // null => briefing a base de regles (LLM indisponible)
  genereParIa: boolean;
  planDuJour: PlanItem[];
  signaux: AgentSignals;
  createdAt: string;
  seenAt: string | null;
}
