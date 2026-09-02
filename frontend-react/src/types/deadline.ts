export type DeadlineSeverity = "INFO" | "WARNING" | "CRITICAL";
export type DeadlineStatut = "OUVERTE" | "TERMINEE" | "IGNOREE";
export type DeadlineSource = "AUTO" | "MANUAL";
export type DeadlineRuleKey =
  | "CN_EXPIRY_90D"
  | "RC_DEPOT_3M"
  | "CNSS_DECL_30D"
  | "CLOTURE_COMPTABLE_6M"
  | "LIQUIDATION_PUBLI_16J"
  | "STEP_STALE_7D";

export interface Deadline {
  id: string;
  ticketId: string | null;
  dossierId: string | null;
  title: string;
  description: string | null;
  dueAt: string;
  severity: DeadlineSeverity;
  source: DeadlineSource;
  ruleKey: DeadlineRuleKey | null;
  statut: DeadlineStatut;
  assigneId: string | null;
  creeParId: string | null;
  termineeAt: string | null;
  ignoreeAt: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface CreateDeadlinePayload {
  ticketId?: string;
  dossierId?: string;
  title: string;
  description?: string;
  dueAt: string;
  severity?: DeadlineSeverity;
  assigneId?: string;
}

export interface DeadlineListResponse {
  items: Deadline[];
  total: number;
}
