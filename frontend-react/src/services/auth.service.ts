import { api } from '../lib/api';
import type { ProfessionalType } from '../types/professional';
import type {
  AuthTokens,
  DossierClient,
  InviteEmployeeResponse,
  LoginResponse,
  RecoveryCodesResponse,
  RegisterResponse,
  SetUserStatusResponse,
  Setup2faResponse,
  SmsOtpSendResponse,
  TwofaMethod,
  VerifyEmailResponse,
  WorkspaceCheckResponse,
  WorkspaceUser,
  WorkspaceDirectoryUser,
} from '../types/auth';

export interface RegisterPayload {
  workspaceName: string;
  contactEmail: string;
  subscriptionId: string;
  firstName: string;
  lastName: string;
  phone?: string;
  email: string;
  password: string;
}

/**
 * Payload du endpoint public POST /api/v1/public/signup/cabinet.
 *
 * Simplification inscription (2026-07-13) : formulaire unique, sans distinction
 * personne physique / morale. Champs retires : IF, RC et le double email
 * (contactEmail cabinet). Un seul email pro est saisi (`email`) ; l'ICE est
 * optionnel (completable ensuite dans les parametres du cabinet). Ville gardee.
 */
export interface SignupCabinetPayload {
  workspaceName: string;
  /** ICE optionnel (15 chiffres si renseigne). Omis / vide toleres. */
  ice?: string;
  city: string;
  firstName: string;
  lastName: string;
  /** Email professionnel unique (devient le contact du cabinet et du titulaire). */
  email: string;
  phone: string;
  selectedPlan: string; // 'essentiel' | 'business' | 'entreprise' (spec 2026-06-02)
  /** Onboarding 2026-06-24 — type de profil (8 valeurs ProfessionalType backend). */
  professionalType?: ProfessionalType;
  cguAccepted: boolean;
  /**
   * BUG 14 (2026-06-07) — si true, le backend ne PAS envoie le welcome
   * email immediatement : l'envoi est differe jusqu'a la validation du
   * paiement (workflow signup -> plan -> recap -> paiement -> activation
   * + identifiants). Defaut false pour compat retro.
   */
  deferCredentials?: boolean;
}

export interface SignupCabinetResponse {
  workspaceCode: string;
  workspaceId: string;
  adminUserId: string;
  adminEmail: string;
  /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere par le backend. */
  loginEmail?: string;
  /** BUG 7 (2026-06-08) — email perso fourni par l'utilisateur. */
  contactEmail?: string;
  trialEndsAt: string;
  verifyEmailSent: boolean;
  message: string;
  /**
   * BUG 14 (2026-06-07) — tokens transitoires renvoyes UNIQUEMENT quand
   * {@code deferCredentials: true}. Permet au wizard signup d'appeler
   * /api/v1/billing/prepare-payment depuis l'etape Paiement sans passer
   * par un login intermediaire (le user n'a pas encore son MDP final).
   */
  accessToken?: string;
  refreshToken?: string;
}

export type SmsOtpPurpose = 'PHONE_VERIFICATION' | '2FA_SETUP' | '2FA_LOGIN' | 'PASSWORD_RESET';

export const authService = {
  async checkWorkspace(workspaceCode: string): Promise<WorkspaceCheckResponse> {
    const { data } = await api.post<WorkspaceCheckResponse>('/auth/workspace-check', { workspaceCode });
    return data;
  },

  async register(payload: RegisterPayload): Promise<RegisterResponse> {
    const { data } = await api.post<RegisterResponse>('/auth/register', payload);
    return data;
  },

  /**
   * Sprint 11 TASK 2 — Endpoint public self-service signup wizard cabinet
   * (whitelist gateway /api/v1/public/**, pas de JWT requis).
   */
  async signupCabinet(payload: SignupCabinetPayload): Promise<SignupCabinetResponse> {
    const { data } = await api.post<SignupCabinetResponse>('/public/signup/cabinet', payload);
    return data;
  },

  async verifyEmail(token: string): Promise<VerifyEmailResponse> {
    const { data } = await api.post<VerifyEmailResponse>('/auth/verify-email', { token });
    return data;
  },

  async resendVerification(workspaceCode: string, email: string): Promise<void> {
    await api.post('/auth/resend-verification', { workspaceCode, email });
  },

  async login(workspaceCode: string, email: string, password: string): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>('/auth/login', { workspaceCode, email, password });
    return data;
  },

  /**
   * Le code part en CHAINE, jamais en nombre.
   *
   * `Verify2faRequest.code` est un `String` cote serveur, contraint par
   * `@Size(min = 6, max = 8)`. Serialise en nombre, un code commencant par zero
   * perd son premier chiffre (`012345` -> `12345`) et le serveur repond
   * « Donnees invalides » : environ une authentification TOTP sur dix echouait,
   * sans que l'utilisateur puisse comprendre pourquoi. Le commentaire du DTO
   * (CRIT-3) demandait deja explicitement une chaine serialisee telle quelle.
   */
  async verify2fa(userId: string, workspaceId: string, code: string): Promise<AuthTokens> {
    const { data } = await api.post<AuthTokens>('/auth/verify-2fa', { userId, workspaceId, code });
    return data;
  },

  async setup2fa(): Promise<Setup2faResponse> {
    const { data } = await api.post<Setup2faResponse>('/auth/setup-2fa');
    return data;
  },

  /**
   * Renvoie les methodes 2FA disponibles pour cet utilisateur. TOTP est
   * toujours dispo ; SMS uniquement si un numero de telephone a ete renseigne
   * a l'inscription. Utilise par Choose2faMethodPage pour masquer le bouton
   * SMS quand non applicable (RG-AU30).
   */
  async getSetup2faOptions(): Promise<{
    totpAvailable: boolean;
    smsAvailable: boolean;
    maskedPhone: string | null;
  }> {
    const { data } = await api.get('/auth/2fa/setup-options');
    return data;
  },

  /**
   * Hotfix 2026-06-04 : renvoie desormais un NOUVEAU couple de tokens
   * (r2s=false dans le claim). L'appelant DOIT le passer a
   * {@link tokenStorage#setTokens} AVANT toute requete suivante, sinon le
   * filtre Setup2faRequiredEnforcer relit l'ancien token (r2s=true) et
   * bloque tout sur 403 SETUP_2FA_REQUIRED.
   */
  async confirm2fa(code: number): Promise<AuthTokens> {
    const { data } = await api.post<AuthTokens>('/auth/setup-2fa/confirm', { code });
    return data;
  },

  async choose2faMethod(method: TwofaMethod): Promise<void> {
    await api.post('/auth/2fa/choose-method', { method });
  },

  async sendSmsOtp(purpose: SmsOtpPurpose): Promise<SmsOtpSendResponse> {
    const { data } = await api.post<SmsOtpSendResponse>('/auth/2fa/sms/send', { purpose });
    return data;
  },

  /**
   * Hotfix 2026-06-04 : pour {@code purpose='2FA_SETUP'} le backend renvoie
   * 200 OK + un nouveau couple AuthTokens (r2s=false). Pour les autres purposes
   * il renvoie 204 No Content (data === ''). L'appelant en mode setup DOIT
   * persister les tokens via {@link tokenStorage#setTokens} — voir
   * {@link confirm2fa} pour la rationale.
   */
  async verifySmsOtp(purpose: SmsOtpPurpose, code: string): Promise<AuthTokens | null> {
    const { data } = await api.post<AuthTokens | ''>('/auth/2fa/sms/verify', { purpose, code });
    return data && typeof data === 'object' ? data : null;
  },

  async generateRecoveryCodes(): Promise<RecoveryCodesResponse> {
    const { data } = await api.post<RecoveryCodesResponse>('/auth/2fa/recovery-codes/generate');
    return data;
  },

  /**
   * Sprint 14 bis / B4 — court-circuit le 2FA en consommant un code de
   * recuperation single-use (RG-AU41) + rate limit 5/15min (RG-AU42).
   */
  async verifyRecoveryCode(
    workspaceCode: string,
    email: string,
    code: string,
  ): Promise<AuthTokens> {
    const { data } = await api.post<AuthTokens>('/auth/verify-recovery-code', {
      workspaceCode,
      email,
      code,
    });
    return data;
  },

  /**
   * Hotfix 2026-06-04 : renvoie desormais un NOUVEAU couple de tokens
   * (mcp=false dans le claim). L'appelant DOIT le passer a
   * {@link tokenStorage#setTokens} AVANT toute requete suivante, sinon le
   * filtre ChangePasswordEnforcer relit l'ancien token (mcp=true) et
   * bloque tout sur 403 PASSWORD_CHANGE_REQUIRED.
   */
  async changePassword(
    oldPassword: string,
    newPassword: string,
    confirmPassword: string,
  ): Promise<AuthTokens> {
    const { data } = await api.post<AuthTokens>('/auth/change-password', {
      oldPassword,
      newPassword,
      confirmPassword,
    });
    return data;
  },

  totpQrCodeUrl(): string {
    return '/api/v1/auth/2fa/totp/qr-code';
  },

  async refresh(refreshToken: string): Promise<AuthTokens> {
    const { data } = await api.post<AuthTokens>('/auth/refresh', { refreshToken });
    return data;
  },

  async logout(refreshToken?: string): Promise<void> {
    await api.post('/auth/logout', refreshToken ? { refreshToken } : {});
  },

  async requestPasswordReset(workspaceCode: string, email: string): Promise<void> {
    await api.post('/auth/password-reset/request', { workspaceCode, email });
  },

  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await api.post('/auth/password-reset/confirm', { token, newPassword });
  },

  async inviteClient(payload: {
    dossierId: string;
    email: string;
    firstName: string;
    lastName: string;
    phone?: string;
  }): Promise<{
    userId: string;
    userCreated: boolean;
    temporaryPassword: string | null;
    /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere. */
    loginEmail?: string;
    /** BUG 7 (2026-06-08) — email perso fourni. */
    contactEmail?: string;
    message: string;
  }> {
    const { data } = await api.post('/auth/invite-client', payload);
    return data;
  },

  /**
   * Fix 2026-06-07 (BUG 6) — Retire DEFINITIVEMENT l'acces d'un client
   * a un dossier. Difference avec la suspension :
   *  - suspension = reversible, garde la liaison user<->dossier
   *  - retrait    = entreprise_dossiers.client_id = NULL
   *
   * Idempotent : si le dossier n'avait pas de client lie, le back
   * retourne {alreadyDetached:true} (200 OK, pas une erreur).
   *
   * RBAC : ROLE_EMPLOYE / ROLE_SUPERVISEUR.
   */
  /**
   * 2026-07-01 — Renvoie l'identite du CLIENT lie a un dossier (ou null si
   * aucun). Sert a afficher "Client : Prenom Nom (email)" et a rendre le
   * retrait d'acces nominatif. RBAC : EMPLOYE / SUPERVISEUR.
   */
  async getDossierClient(dossierId: string): Promise<DossierClient | null> {
    const { data } = await api.get<{ client: DossierClient | null }>(
      `/auth/dossiers/${dossierId}/client`,
    );
    return data.client;
  },

  async removeClientAccess(dossierId: string): Promise<{
    dossierId: string;
    previousClientId: string;
    alreadyDetached: boolean;
    message: string;
  }> {
    const { data } = await api.delete(`/auth/dossiers/${dossierId}/client`);
    return data;
  },

  /**
   * BUG 6 (2026-06-07) — Invite un EMPLOYE (ou SUPERVISEUR) dans le workspace.
   * SUPERVISEUR only. Backend enforce le quota plan (402 si depasse). Le user
   * cree est PENDING jusqu'a son 1er login reussi.
   */
  async inviteEmployee(payload: {
    email: string;
    firstName: string;
    lastName: string;
    phone?: string;
    role: 'EMPLOYE' | 'SUPERVISEUR';
  }): Promise<InviteEmployeeResponse> {
    const { data } = await api.post<{
      userId: string;
      created: boolean;
      emailDelivered: boolean;
      tempPassword: string | null;
      loginEmail?: string;
      contactEmail?: string;
      message: string;
    }>('/auth/invite-employee', payload);
    return {
      userId: data.userId,
      created: data.created,
      emailDelivered: data.emailDelivered,
      tempPassword: data.tempPassword,
      loginEmail: data.loginEmail,
      contactEmail: data.contactEmail,
      message: data.message,
    };
  },

  /**
   * BUG 6 (2026-06-07) — Liste les membres internes (EMPLOYE + SUPERVISEUR)
   * du workspace courant. SUPERVISEUR only.
   */
  async listWorkspaceUsers(): Promise<WorkspaceUser[]> {
    const { data } = await api.get<WorkspaceUser[]>('/auth/users');
    return data;
  },

  /**
   * Traçabilité (2026-07-15) — annuaire COMPLET du workspace (internes ET
   * clients, tous statuts y compris INACTIVE). Reserve SUPERVISEUR / SUPER_ADMIN.
   * Utilise par la page Traçabilité pour resoudre par leur NOM tous les acteurs
   * (y compris clients ou comptes desactives) et alimenter le filtre groupe.
   */
  async listWorkspaceDirectory(): Promise<WorkspaceDirectoryUser[]> {
    const { data } = await api.get<WorkspaceDirectoryUser[]>('/auth/users/workspace-directory');
    return data;
  },

  /**
   * BUG 12 (2026-06-08) — Liste des contacts internes pour le chat. Memes
   * regles que {@link listWorkspaceUsers} mais accessible aux EMPLOYE et :
   *  - filtre ACTIVE uniquement (les PENDING / INACTIVE n'apparaissent pas),
   *  - exclut le caller lui-meme,
   *  - exclut les CLIENT (le chat client-cabinet passe par un autre flux,
   *    ils n'apparaissent pas dans cette liste).
   */
  async listChatContacts(): Promise<WorkspaceUser[]> {
    const { data } = await api.get<WorkspaceUser[]>('/auth/users/contacts');
    return data;
  },

  /**
   * BUG 7 (chore 2026-06-08) — Met a jour le contact_email du user courant.
   * Le login_email reste immuable cote API. Retourne {changed:false} si
   * l'email fourni est deja celui en place. Erreur 409 si on essaie de mettre
   * contact_email = login_email (interdit, UX confus).
   */
  async updateContactEmail(contactEmail: string): Promise<{
    userId: string;
    loginEmail: string;
    contactEmail: string;
    previousContactEmail: string;
    changed: boolean;
    message: string;
  }> {
    const { data } = await api.patch('/auth/me/contact-email', { contactEmail });
    return data;
  },

  /**
   * BUG 7 (chore 2026-06-08) — Profil utilisateur courant. Renvoie loginEmail
   * (read-only) + contactEmail (editable) + autres champs (firstName, lastName,
   * phone, role, status).
   */
  async getMe(): Promise<{
    userId: string;
    workspaceId: string;
    email: string;
    loginEmail: string;
    contactEmail: string;
    firstName?: string;
    lastName?: string;
    phone?: string;
    role: string;
    status?: string;
  }> {
    const { data } = await api.get('/auth/me');
    return data;
  },

  /**
   * BUG 6 (2026-06-07) — Active ou desactive un compte membre. SUPERVISEUR
   * only. Reactiver un EMPLOYE re-applique le quota plan ; 402 si depasse.
   * 403 si on essaie de se desactiver soi-meme.
   */
  async setUserActive(userId: string, active: boolean): Promise<SetUserStatusResponse> {
    const { data } = await api.patch<SetUserStatusResponse>(
      `/auth/users/${userId}/status`,
      { active },
    );
    return data;
  },

  /** Lot L1 (CDC 3.2, RG-DR-06) : employes du cabinet qui ont le droit de suppression en Data Room. */
  async listDroitsSuppressionDataroom(): Promise<string[]> {
    const { data } = await api.get<{ employesAvecLeDroit: string[] }>(
      '/auth/users/droit-suppression-dataroom',
    );
    return data.employesAvecLeDroit;
  },

  /** Lot L1 : le superviseur accorde ou retire le droit de suppression a un employe (trace). */
  async setDroitSuppressionDataroom(userId: string, accorde: boolean): Promise<void> {
    await api.put(`/auth/users/${userId}/droit-suppression-dataroom`, { accorde });
  },
};
