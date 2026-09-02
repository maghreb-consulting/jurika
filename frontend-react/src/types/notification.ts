/**
 * BUG 10 (2026-06-08) — Notifications in-app (badge cloche topbar +
 * drawer dans la page Parametres). Persiste cote realtime-service
 * (table notifications + Socket.io emit `notification:received`).
 */
export type NotificationType =
  | 'TICKET_ASSIGNED'
  | 'DOSSIER_TRANSFER'
  | 'DEADLINE_DUE'
  | 'CHAT_MESSAGE'
  | 'PAYMENT_VALIDATED'
  | 'WORKFLOW_COMPLETED'
  | 'WORKSPACE_EVENT';

export interface Notification {
  id: string;
  workspaceId: string;
  userId: string;
  type: NotificationType | string;
  title: string;
  message: string;
  actionUrl: string | null;
  metadata: Record<string, unknown> | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationListResponse {
  items: Notification[];
  total: number;
}

export interface NotificationPreferences {
  preferences: Record<string, boolean>;
  knownTypes: string[];
}

/** BUG 11 — labels FR pour la page Parametres > Notifications. */
export const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  TICKET_ASSIGNED: 'Ticket assigne',
  DOSSIER_TRANSFER: 'Transfert de dossier',
  DEADLINE_DUE: 'Echeance proche',
  CHAT_MESSAGE: 'Message recu',
  PAYMENT_VALIDATED: 'Paiement valide',
  WORKFLOW_COMPLETED: 'Workflow termine',
  WORKSPACE_EVENT: 'Evenement workspace',
};

/**
 * Liste de repli des types connus, alignee sur `NOTIFICATION_TYPES` du
 * realtime-service (backend-node/src/index.js). Utilisee quand l'endpoint
 * `/notifications/preferences` ne renvoie pas (ou renvoie vide) `knownTypes`
 * — sinon l'onglet Notifications restait vide sans message.
 */
export const NOTIFICATION_TYPES: NotificationType[] = [
  'TICKET_ASSIGNED',
  'DOSSIER_TRANSFER',
  'DEADLINE_DUE',
  'CHAT_MESSAGE',
  'PAYMENT_VALIDATED',
  'WORKFLOW_COMPLETED',
  'WORKSPACE_EVENT',
];

/**
 * Types reserves au flux interne du cabinet : le CLIENT ne recoit pas de
 * notifications liees aux tickets / dossiers / workflows internes.
 */
export const STAFF_ONLY_NOTIFICATION_TYPES: NotificationType[] = [
  'TICKET_ASSIGNED',
  'DOSSIER_TRANSFER',
  'WORKFLOW_COMPLETED',
];

/**
 * Filtre une liste de types selon le role : retire les types internes pour
 * le CLIENT, laisse tout le reste inchange (y compris d'eventuels types
 * personnalises renvoyes par le backend).
 */
export function filterNotificationTypesForRole(
  types: string[],
  role: string | undefined,
): string[] {
  if (role === 'CLIENT') {
    return types.filter((t) => !STAFF_ONLY_NOTIFICATION_TYPES.includes(t as NotificationType));
  }
  return types;
}
