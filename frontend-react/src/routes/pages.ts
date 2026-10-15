import { lazy, type ComponentType } from 'react';

/**
 * Lot L6b (E3) : ecrans charges a la demande (React.lazy).
 *
 * Chaque ecran est un morceau de code separe, telecharge a la premiere visite. Le chargeur
 * est expose (`chargeurs`) pour PRECHARGER l'ecran probable : survol ou focus d'une entree
 * du menu (layout/MenuLateral). Le module charge reste en cache : la navigation suivante
 * est immediate.
 */
type Chargeur = () => Promise<unknown>;

function ecran<M extends Record<string, unknown>>(charger: () => Promise<M>, nom: keyof M & string) {
  const Composant = lazy(async () => ({ default: (await charger())[nom] as ComponentType }));
  return { Composant, charger: charger as Chargeur };
}

const c = {
  landing: () => import('../pages/landing/LandingPage'),
  login: () => import('../pages/auth/LoginPage'),
  signup: () => import('../pages/auth/SignupPage'),
  essai: () => import('../pages/auth/EssaiGratuitPage'),
  essaiConfirmer: () => import('../pages/auth/EssaiConfirmerPage'),
  reset: () => import('../pages/auth/ResetPasswordPage'),
  recover: () => import('../pages/auth/RecoverWithCodePage'),
  setup2fa: () => import('../pages/auth/Setup2FAPage'),
  verifyEmail: () => import('../pages/auth/VerifyEmailPage'),
  confirmerChangement: () => import('../pages/auth/ConfirmerChangementComptePage'),
  forceChange: () => import('../pages/auth/ForceChangePasswordPage'),
  choose2fa: () => import('../pages/auth/Choose2faMethodPage'),
  profileSecurity: () => import('../pages/account/ProfileSecurityPage'),
  profile: () => import('../pages/account/ProfilePage'),
  forbidden: () => import('../pages/misc/ForbiddenPage'),
  cgu: () => import('../pages/legal/CguPage'),
  confidentialite: () => import('../pages/legal/ConfidentialitePage'),
  dashboard: () => import('../pages/dashboard/DashboardRouter'),
  tickets: () => import('../pages/tickets/TicketsPage'),
  ticket: () => import('../pages/tickets/TicketPage'),
  workflow: () => import('../pages/workflow/WorkflowPage'),
  dataroom: () => import('../pages/dataroom/DataroomPage'),
  demandes: () => import('../pages/demandes/DemandesKanban'),
  requetes: () => import('../pages/demandes/MesRequetes'),
  chat: () => import('../pages/chat/ChatPage'),
  chatbotSources: () => import('../pages/admin/ChatbotSourcesPage'),
  audit: () => import('../pages/admin/AuditLogPage'),
  workspaces: () => import('../pages/admin/WorkspacesPage'),
  essais: () => import('../pages/admin/EssaisPage'),
  workspaceDetail: () => import('../pages/admin/WorkspaceDetailPage'),
  users: () => import('../pages/admin/UsersPage'),
  referentiel: () => import('../pages/admin/ReferentielDemarchesPage'),
  delaisGreffe: () => import('../pages/referentiel/DelaisGreffePage'),
  billing: () => import('../pages/billing/BillingPage'),
  paiementRetour: () => import('../pages/billing/PaiementRetourPage'),
  archive: () => import('../pages/billing/ArchivePage'),
  paiementsAdmin: () => import('../pages/admin/PaiementsAdminPage'),
  abonnementsAdmin: () => import('../pages/admin/AbonnementsAdminPage'),
  team: () => import('../pages/team/TeamPage'),
  tracabilite: () => import('../pages/tracabilite/TracabilitePage'),
  dossiersAVerifier: () => import('../pages/dossiers/DossiersAVerifierPage'),
  calendar: () => import('../pages/calendar/CalendarPage'),
  settings: () => import('../pages/settings/SettingsPage'),
  changePlan: () => import('../pages/billing/ChangePlanPage'),
  payment: () => import('../pages/billing/PaymentPage'),
  contrat: () => import('../pages/contrat/ContratPage'),
  contratsAdmin: () => import('../pages/admin/ContratsAdminPage'),
  editeur: () => import('../pages/admin/EditeurParametresPage'),
  design: () => import('../pages/design/DesignPage'),
};

export const Ecrans = {
  LandingPage: ecran(c.landing, 'LandingPage'),
  LoginPage: ecran(c.login, 'LoginPage'),
  SignupPage: ecran(c.signup, 'SignupPage'),
  EssaiGratuitPage: ecran(c.essai, 'EssaiGratuitPage'),
  EssaiConfirmerPage: ecran(c.essaiConfirmer, 'EssaiConfirmerPage'),
  ResetPasswordPage: ecran(c.reset, 'ResetPasswordPage'),
  RecoverWithCodePage: ecran(c.recover, 'RecoverWithCodePage'),
  Setup2FAPage: ecran(c.setup2fa, 'Setup2FAPage'),
  VerifyEmailPage: ecran(c.verifyEmail, 'VerifyEmailPage'),
  ConfirmerChangementComptePage: ecran(c.confirmerChangement, 'ConfirmerChangementComptePage'),
  ForceChangePasswordPage: ecran(c.forceChange, 'ForceChangePasswordPage'),
  Choose2faMethodPage: ecran(c.choose2fa, 'Choose2faMethodPage'),
  ProfileSecurityPage: ecran(c.profileSecurity, 'ProfileSecurityPage'),
  ProfilePage: ecran(c.profile, 'ProfilePage'),
  ForbiddenPage: ecran(c.forbidden, 'ForbiddenPage'),
  CguPage: ecran(c.cgu, 'CguPage'),
  ConfidentialitePage: ecran(c.confidentialite, 'ConfidentialitePage'),
  DashboardRouter: ecran(c.dashboard, 'DashboardRouter'),
  TicketsPage: ecran(c.tickets, 'TicketsPage'),
  TicketPage: ecran(c.ticket, 'TicketPage'),
  WorkflowPage: ecran(c.workflow, 'WorkflowPage'),
  DataroomPage: ecran(c.dataroom, 'DataroomPage'),
  DemandesKanban: ecran(c.demandes, 'DemandesKanban'),
  MesRequetes: ecran(c.requetes, 'MesRequetes'),
  ChatPage: ecran(c.chat, 'ChatPage'),
  ChatbotSourcesPage: ecran(c.chatbotSources, 'ChatbotSourcesPage'),
  AuditLogPage: ecran(c.audit, 'default'),
  WorkspacesPage: ecran(c.workspaces, 'WorkspacesPage'),
  EssaisPage: ecran(c.essais, 'EssaisPage'),
  WorkspaceDetailPage: ecran(c.workspaceDetail, 'WorkspaceDetailPage'),
  UsersPage: ecran(c.users, 'UsersPage'),
  ReferentielDemarchesPage: ecran(c.referentiel, 'ReferentielDemarchesPage'),
  DelaisGreffePage: ecran(c.delaisGreffe, 'DelaisGreffePage'),
  BillingPage: ecran(c.billing, 'BillingPage'),
  PaiementRetourPage: ecran(c.paiementRetour, 'PaiementRetourPage'),
  ArchivePage: ecran(c.archive, 'ArchivePage'),
  PaiementsAdminPage: ecran(c.paiementsAdmin, 'PaiementsAdminPage'),
  AbonnementsAdminPage: ecran(c.abonnementsAdmin, 'AbonnementsAdminPage'),
  TeamPage: ecran(c.team, 'TeamPage'),
  TracabilitePage: ecran(c.tracabilite, 'TracabilitePage'),
  DossiersAVerifierPage: ecran(c.dossiersAVerifier, 'DossiersAVerifierPage'),
  CalendarPage: ecran(c.calendar, 'CalendarPage'),
  SettingsPage: ecran(c.settings, 'SettingsPage'),
  ChangePlanPage: ecran(c.changePlan, 'ChangePlanPage'),
  PaymentPage: ecran(c.payment, 'PaymentPage'),
  ContratPage: ecran(c.contrat, 'ContratPage'),
  ContratsAdminPage: ecran(c.contratsAdmin, 'ContratsAdminPage'),
  EditeurParametresPage: ecran(c.editeur, 'EditeurParametresPage'),
  DesignPage: ecran(c.design, 'DesignPage'),
};

/** Chargeur du code de l'ecran servi a une adresse du menu (prechargement). */
const PAR_CHEMIN: Record<string, Chargeur> = {
  '/dashboard': c.dashboard,
  '/tickets': c.tickets,
  '/data-rooms': c.dataroom,
  '/mes-demandes': c.demandes,
  '/mes-requetes': c.requetes,
  '/chat': c.chat,
  '/calendar': c.calendar,
  '/delais-greffe': c.delaisGreffe,
  '/tracabilite': c.tracabilite,
  '/dossiers-a-verifier': c.dossiersAVerifier,
  '/app/team': c.team,
  '/admin/workspaces': c.workspaces,
  '/admin/abonnements': c.abonnementsAdmin,
  '/admin/paiements': c.paiementsAdmin,
  '/admin/users': c.users,
  '/admin/essais': c.essais,
  '/admin/audit': c.audit,
  '/admin/referentiel-demarches': c.referentiel,
  '/admin/chatbot-sources': c.chatbotSources,
  '/admin/contrats': c.contratsAdmin,
  '/admin/editeur': c.editeur,
  '/design': c.design,
  '/profile': c.profile,
  '/settings': c.settings,
  '/app/contrat': c.contrat,
};

const dejaPrecharges = new Set<string>();

/**
 * Precharge le code de l'ecran d'une adresse du menu (une fois). Un echec de prechargement
 * (reseau) n'a pas d'effet visible : la navigation retentera le chargement et affichera
 * l'erreur de la page si elle echoue encore.
 */
export function prechargerEcran(chemin: string): boolean {
  const charger = PAR_CHEMIN[chemin];
  if (!charger || dejaPrecharges.has(chemin)) return false;
  dejaPrecharges.add(chemin);
  charger().catch(() => dejaPrecharges.delete(chemin));
  return true;
}
