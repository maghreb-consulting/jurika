// Espace SUPER_ADMIN — types miroir des endpoints /api/v1/admin (auth-service).

/** Ligne de la liste des workspaces (GET /admin/workspaces). */
export interface AdminWorkspaceRow {
  id: string;
  code: string;
  name: string;
  contactEmail: string | null;
  /** ESSAI / ACTIVE / SUSPENDED / DEACTIVATED / PENDING_VERIFICATION. */
  statut: string;
  forfait: string | null;
  employes: number;
  clients: number;
  /** null si le stockage n'est pas disponible (affiche "—"). */
  storageBytes: number | null;
  storageFiles: number | null;
  createdAt: string;
}

export type WorkspaceStatusAction = 'activate' | 'suspend' | 'deactivate';

/** Ligne de la liste des utilisateurs (GET /admin/users). */
export interface AdminUserRow {
  id: string;
  firstName: string;
  lastName: string;
  loginEmail: string;
  contactEmail: string;
  role: string;
  /** ACTIVE / INACTIVE / PENDING. */
  status: string;
  totpEnabled: boolean;
  workspaceId: string;
  workspaceCode: string | null;
  workspaceName: string | null;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AdminUsersPage {
  items: AdminUserRow[];
  total: number;
  offset: number;
  limit: number;
}

export interface CategoryCount {
  label: string;
  count: number;
}

export interface DayCount {
  day: string; // ISO date
  count: number;
}

export interface AuditEventLite {
  id: number;
  action: string;
  userId: string | null;
  entityType: string | null;
  createdAt: string;
}

/** Vue détaillée d'un workspace (GET /admin/workspaces/{id}). */
export interface WorkspaceDetail {
  id: string;
  code: string;
  name: string;
  statut: string;
  forfait: string | null;
  contactEmail: string | null;
  city: string | null;
  ice: string | null;
  ifFiscal: string | null;
  rcNumber: string | null;
  adresse: string | null;
  telephone: string | null;
  siteWeb: string | null;
  createdAt: string;
  trialStartedAt: string | null;
  trialEndsAt: string | null;
  trialStatus: string | null;
  subscriptionStatus: string | null;
  employes: number;
  clients: number;
  ticketsTotal: number;
  dossiers: number;
  storageBytes: number | null;
  storageFiles: number | null;
  usersByRole: CategoryCount[];
  docsByType: CategoryCount[];
  ticketsByStatut: CategoryCount[];
  eventsByAction: CategoryCount[];
  activity30d: DayCount[];
  recentEvents: AuditEventLite[];
  generatedAt: string;
}
