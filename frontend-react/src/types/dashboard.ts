// Sprint 10 -- types miroir backend dashboard-service

export type DashboardScope = 'SUPER_ADMIN' | 'SUPERVISEUR' | 'EMPLOYE' | 'CLIENT';

export interface DailyCount {
  day: string; // ISO LocalDate
  count: number;
}

export interface CategoryCount {
  label: string;
  count: number;
}

export interface MonthlyCount {
  month: string; // "YYYY-MM"
  count: number;
}

export interface MonthlyFlow {
  month: string; // "YYYY-MM"
  crees: number;
  clotures: number;
}

// SUPER_ADMIN
export interface WorkspaceUsage {
  workspaceId: string;
  name: string;
  eventsLast7d: number;
}

export interface AuditEventSummary {
  id: string;
  action: string;
  userId: string | null;
  workspaceId: string | null;
  entityType: string | null;
  createdAt: string;
}

export interface StorageSummary {
  totalBytes: number;
  fileCount: number;
}

export interface SuperAdminDashboardDto {
  activeWorkspaces30d: number;
  totalWorkspaces: number;
  totalUsers: number;
  signups30d: DailyCount[];
  topWorkspacesByUsage: WorkspaceUsage[];
  criticalAuditEvents24h: AuditEventSummary[];
  servicesHealth: Record<string, string>;
  storage: StorageSummary;
  workspacesParForfait: CategoryCount[];
  workspacesParStatut: CategoryCount[];
  signupsMensuels: MonthlyCount[];
  generatedAt: string;
}

// SUPERVISEUR
export interface EcheanceItem {
  id: string;
  dossierId: string;
  typeEcheance: string;
  dateEcheance: string | null;
  statut: string;
}

export interface EcheanceBucket {
  label: string;
  items: EcheanceItem[];
}

export interface EmployeeLoad {
  userId: string;
  email: string;
  ticketsOuverts: number;
  echeancesAssignees: number;
}

export interface DossierRisk {
  dossierId: string;
  raisonSociale: string;
  motif: string;
}

export interface SuperviseurDashboardDto {
  ticketsOuverts: number;
  ticketsClos30d: number;
  tempsMoyenClotureHeures: number;
  evolutionTickets30j: DailyCount[];
  topWorkflows: CategoryCount[];
  chargeParEmploye: EmployeeLoad[];
  echeancesJ30J15J3: EcheanceBucket[];
  dossiersARisque: DossierRisk[];
  ticketsParStatut: CategoryCount[];
  evolutionMensuelle: MonthlyFlow[];
  demandesParStatut: CategoryCount[];
  generatedAt: string;
}

// EMPLOYE
export interface TicketLite {
  id: string;
  reference: string;
  titre: string;
  statut: string;
  priorite: string;
  createdAt: string;
}

export interface DayLoad {
  day: string;
  charge: number;
}

export interface OperationLite {
  type: string;
  label: string | null;
  at: string;
}

export interface EmployeDashboardDto {
  mesTicketsOuverts: number;
  derniersTickets: TicketLite[];
  maChargeSemaine: DayLoad[];
  mesEcheancesAssignees: EcheanceItem[];
  dernieresOperationsDataroom: OperationLite[];
  generatedAt: string;
}

// CLIENT
export interface DossierClientLite {
  id: string;
  raisonSociale: string;
  statut: string;
  lastEventAt: string | null;
  lastEventLabel: string | null;
}

export interface DocumentLite {
  id: string;
  title: string;
  filename: string;
  createdAt: string;
}

export interface ClientDashboardDto {
  mesDossiers: DossierClientLite[];
  mesTicketsEnCours: TicketLite[];
  mesDocumentsRecents: DocumentLite[];
  mesEcheancesAVenir: EcheanceItem[];
  generatedAt: string;
}
