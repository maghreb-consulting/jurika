export type Role = 'SUPER_ADMIN' | 'SUPERVISEUR' | 'EMPLOYE' | 'CLIENT';
export type TwofaMethod = 'TOTP' | 'SMS';

export interface User {
  userId: string;
  workspaceId: string;
  /** 2026-06-22 — Vrai code workspace saisi au login (ex. JUR-XXXX), affiche
   *  dans le menu. Le JWT ne contient que workspaceId (UUID) ; sans ce champ
   *  AppTopNav fabriquait un faux code depuis l'UUID -> incoherence visible. */
  workspaceCode?: string;
  email: string;
  /** BUG 7 (2026-06-08) — identifiant @jurika.ma (lecture seule). */
  loginEmail?: string;
  /** BUG 7 (2026-06-08) — email perso destinataire des notifs (modifiable). */
  contactEmail?: string;
  role: Role;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
  userId: string;
  workspaceId: string;
}

export interface LoginResponse {
  requires2fa: boolean;
  requires2faSetup: boolean;
  mustChangePassword: boolean;
  twofaMethod: TwofaMethod | null;
  userId: string;
  workspaceId: string;
  accessToken: string | null;
  refreshToken: string | null;
  accessExpiresAt: string | null;
  refreshExpiresAt: string | null;
}

export interface WorkspaceCheckResponse {
  workspaceId: string;
  name: string;
}

export interface RegisterResponse {
  workspaceId: string;
  workspaceCode: string;
  userId: string;
  emailDelivered?: boolean;
  /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere a l'inscription. */
  loginEmail?: string;
  /** BUG 7 (2026-06-08) — email perso fourni par l'utilisateur. */
  contactEmail?: string;
}

export interface Setup2faResponse {
  secret: string;
  otpAuthUri: string;
  qrCodePngBase64: string;
}

export interface SmsOtpSendResponse {
  otpId: string;
  maskedPhone: string;
  expiresAt: string;
}

export interface VerifyEmailResponse {
  workspaceId: string;
  userId: string;
  email: string;
  message: string;
}

export interface RecoveryCodesResponse {
  codes: string[];
  warning: string;
}

/**
 * BUG 6 (2026-06-07) — Statut d'un compte (cf {@code users.status} en DB
 * via migration V27). PENDING = invite jamais connecte, ACTIVE = normal,
 * INACTIVE = desactive par le superviseur.
 */
export type UserStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE';

/**
 * BUG 6 (2026-06-07) — Une ligne de la vue Equipe (GET /api/v1/auth/users).
 * Pour CLIENT, voir le flux dataroom dedie.
 */
export interface WorkspaceUser {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: 'EMPLOYE' | 'SUPERVISEUR';
  status: UserStatus;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

/**
 * Traçabilité (2026-07-15) — une entree de l'annuaire COMPLET du workspace
 * (GET /auth/users/workspace-directory) : internes ET clients, tous statuts
 * (y compris INACTIVE). Sert a resoudre par leur NOM tous les acteurs d'un
 * event audit et a alimenter le filtre "acteur" groupe (Employes / Clients).
 * Contrairement a {@link WorkspaceUser}, le role peut valoir 'CLIENT'.
 */
export interface WorkspaceDirectoryUser {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: 'EMPLOYE' | 'SUPERVISEUR' | 'CLIENT';
  status: UserStatus;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

/**
 * 2026-07-01 — Identite du CLIENT lie a un dossier (GET /auth/dossiers/{id}/client).
 * {@code email} = email de contact (destinataire de l'invitation).
 */
export interface DossierClient {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  status: string;
}

export interface InviteEmployeeResponse {
  userId: string;
  created: boolean;
  emailDelivered: boolean;
  tempPassword: string | null;
  /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere pour le nouveau membre. */
  loginEmail?: string;
  /** BUG 7 (2026-06-08) — email perso fourni dans le formulaire. */
  contactEmail?: string;
  message: string;
}

export interface SetUserStatusResponse {
  userId: string;
  previousStatus: UserStatus;
  status: UserStatus;
  changed: boolean;
  message: string;
}
