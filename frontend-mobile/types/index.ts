export type Role = "SUPER_ADMIN" | "SUPERVISEUR" | "EMPLOYE" | "CLIENT";

export interface AuthTokens {
  accessToken: string;
  refreshToken?: string;
}

export interface AuthUser {
  id: string;
  email: string;
  role: Role;
  workspaceId: string;
  workspaceCode: string;
  firstName?: string;
  lastName?: string;
  twoFactorEnabled?: boolean;
}

export interface LoginResponse {
  requires2FA?: boolean;
  userId?: string;
  workspaceId?: string;
  tokens?: AuthTokens;
  user?: AuthUser;
  accessToken?: string;
  refreshToken?: string;
}

export interface WorkspaceCheckResponse {
  exists: boolean;
  workspaceId?: string;
  workspaceName?: string;
}

export type TicketStatus = "NOUVEAU" | "EN_COURS" | "CLOTURE" | "ANNULE";

export interface Ticket {
  id: string;
  reference?: string;
  titre?: string;
  title?: string;
  description?: string;
  status: TicketStatus;
  statut?: TicketStatus;
  workflowType?: string;
  type?: string;
  createdAt?: string;
  updatedAt?: string;
  assigneeName?: string;
  clientName?: string;
}

export interface TicketsListResponse {
  items: Ticket[];
  total: number;
}

export interface DossierBrief {
  id: string;
  raisonSociale: string;
  formeJuridique?: string;
  rc?: string;
  ice?: string;
  status?: string;
  updatedAt?: string;
}

export interface DossierDocument {
  id: string;
  name: string;
  category?: string;
  version?: number;
  createdAt?: string;
  url?: string;
}

export interface DossierJuridique {
  dossier: DossierBrief;
  documents: DossierDocument[];
  history?: Array<{
    ticketId: string;
    type: string;
    closedAt: string;
    documents?: DossierDocument[];
  }>;
}

export interface ChatbotMessage {
  id: string;
  role: "user" | "assistant";
  text: string;
  createdAt: number;
}

export interface KpisResponse {
  ticketsOpen?: number;
  ticketsToday?: number;
  workflowsActive?: number;
  dossiersTotal?: number;
  [key: string]: number | string | undefined;
}
