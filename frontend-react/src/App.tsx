import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { LandingPage } from './pages/landing/LandingPage';
import { LoginPage } from './pages/auth/LoginPage';
import { SignupPage } from './pages/auth/SignupPage';
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage';
import { RecoverWithCodePage } from './pages/auth/RecoverWithCodePage';
import { Setup2FAPage } from './pages/auth/Setup2FAPage';
import { VerifyEmailPage } from './pages/auth/VerifyEmailPage';
import { ConfirmerChangementComptePage } from './pages/auth/ConfirmerChangementComptePage';
import { ForceChangePasswordPage } from './pages/auth/ForceChangePasswordPage';
import { Choose2faMethodPage } from './pages/auth/Choose2faMethodPage';
import { ProfileSecurityPage } from './pages/account/ProfileSecurityPage';
import { ProfilePage } from './pages/account/ProfilePage';
import { ForbiddenPage } from './pages/misc/ForbiddenPage';
import { CguPage } from './pages/legal/CguPage';
import { ConfidentialitePage } from './pages/legal/ConfidentialitePage';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { AppShell } from './components/layout/AppShell';
import { DashboardRouter } from './pages/dashboard/DashboardRouter';
import { TicketsPage } from './pages/tickets/TicketsPage';
import { WorkflowPage } from './pages/workflow/WorkflowPage';
import { DataroomPage } from './pages/dataroom/DataroomPage';
import { DemandesKanban } from './pages/demandes/DemandesKanban';
import { MesRequetes } from './pages/demandes/MesRequetes';
import { ChatPage } from './pages/chat/ChatPage';
import { RedirectionChatbot } from './components/chatbot/RedirectionChatbot';
import { ChatbotSourcesPage } from './pages/admin/ChatbotSourcesPage';
import AuditLogPage from './pages/admin/AuditLogPage';
import { WorkspacesPage } from './pages/admin/WorkspacesPage';
import { WorkspaceDetailPage } from './pages/admin/WorkspaceDetailPage';
import { UsersPage } from './pages/admin/UsersPage';
import { ReferentielDemarchesPage } from './pages/admin/ReferentielDemarchesPage';
import { DelaisGreffePage } from './pages/referentiel/DelaisGreffePage';
import { TicketPage } from './pages/tickets/TicketPage';
import { BillingPage } from './pages/billing/BillingPage';
import { PaiementRetourPage } from './pages/billing/PaiementRetourPage';
import { ArchivePage } from './pages/billing/ArchivePage';
import { PaiementsAdminPage } from './pages/admin/PaiementsAdminPage';
import { AbonnementsAdminPage } from './pages/admin/AbonnementsAdminPage';
import { TeamPage } from './pages/team/TeamPage';
import { TracabilitePage } from './pages/tracabilite/TracabilitePage';
import { DossiersAVerifierPage } from './pages/dossiers/DossiersAVerifierPage';
import { CalendarPage } from './pages/calendar/CalendarPage';
import { SettingsPage } from './pages/settings/SettingsPage';
import { ChangePlanPage } from './pages/billing/ChangePlanPage';
import { PaymentPage } from './pages/billing/PaymentPage';
import { ContratPage } from './pages/contrat/ContratPage';
import { ContratsAdminPage } from './pages/admin/ContratsAdminPage';
import { EditeurParametresPage } from './pages/admin/EditeurParametresPage';
import { useAuthStore } from './store/authStore';
import { useThemeStore } from './store/themeStore';
import { GrainOverlay } from './components/layout/GrainOverlay';
import { ToastProvider } from './components/ui/Toast';
import { useLenisScroll } from './lib/useLenisScroll';

function App() {
  const hydrate = useAuthStore((s) => s.hydrate);
  const applyDomTheme = useThemeStore((s) => s.applyDomTheme);

  useEffect(() => {
    // Sprint 12.5 T2 — applique data-theme dès le mount (avant tout rendu visible)
    // pour eviter un flash de mauvais theme (FOUC theme).
    applyDomTheme();
    hydrate();
  }, [applyDomTheme, hydrate]);

  return (
    <BrowserRouter>
      {/* Sprint 12.5 T13 — Lenis smooth scroll. Monté DANS le Router pour se
          réévaluer à chaque navigation (fix 2026-06-26 : désactivé sur les
          routes à layout 100vh /chat /chatbot qui gelaient la navigation). */}
      <LenisController />
      {/* Sprint 12.5 T13 — grain texture 3% over all app (pointer-events:none). */}
      <GrainOverlay />
      {/* Toast à la charte, monté une seule fois pour toute l'app (retours
          transitoires succès/erreur). Enveloppe les routes pour que useToast()
          soit disponible partout. */}
      <ToastProvider>
      <Routes>
        {/* Routes publiques */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/register" element={<Navigate to="/signup" replace />} />
        {/* Lot L7 (E5), RG-PAY-05 : retour du navigateur apres la page du CMI (aussi a l'inscription,
            avec le jeton transitoire) ; l'etat du paiement est lu sur le serveur, jamais deduit du retour. */}
        <Route path="/app/billing/paiement/retour" element={<PaiementRetourPage />} />
        <Route path="/billing/success" element={<Navigate to="/app/billing" replace />} />
        <Route path="/app/billing/success" element={<Navigate to="/app/billing" replace />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/auth/recover-with-code" element={<RecoverWithCodePage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        {/* Lot L5 (E10) : lien de confirmation d'un changement d'adresse ou d'identifiant. */}
        <Route path="/confirmer-changement-compte" element={<ConfirmerChangementComptePage />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />

        {/* Sprint Beta (pricing-deploy) -- pages legales publiques (TASK 6) */}
        <Route path="/cgu" element={<CguPage />} />
        <Route path="/conditions-generales" element={<CguPage />} />
        <Route path="/confidentialite" element={<ConfidentialitePage />} />
        <Route path="/privacy" element={<ConfidentialitePage />} />

        {/* Tous roles authentifies : compte + dashboard */}
        <Route element={<ProtectedRoute />}>
          <Route path="/account/2fa" element={<Setup2FAPage />} />
          <Route path="/account/change-password" element={<ForceChangePasswordPage />} />
          <Route path="/account/2fa-choose" element={<Choose2faMethodPage />} />
          <Route path="/account/security" element={<ProfileSecurityPage />} />
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<DashboardRouter />} />
            {/* BUG 7 (chore 2026-06-08) — page Profil : loginEmail readonly + contactEmail editable */}
            <Route path="/profile" element={<ProfilePage />} />
            {/* BUG 11 (2026-06-08) — Page Parametres SaaS a onglets. Le sous-onglet
                Equipe est conditionne au role SUPERVISEUR dans la page elle-meme.
                Le sous-onglet/route Facturation est gate au role dans la page (Lot M). */}
            <Route path="/settings/*" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Lot M (2026-06-30) — Facturation : SUPER_ADMIN + SUPERVISEUR + EMPLOYE.
            CLIENT exclu (experience reduite, pas d'upgrade/abonnement/facturation). */}
        <Route element={<ProtectedRoute allowed={['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            {/* Sprint 11 TASK 4 -- billing/trial upgrade page */}
            <Route path="/app/billing" element={<BillingPage />} />
            {/* BUG 8 (2026-06-07) -- changement de forfait (route depuis topnav Upgrade) */}
            <Route path="/app/billing/change-plan" element={<ChangePlanPage />} />
            {/* BUG 14 (2026-06-07) -- page paiement multi-moyens entre choix forfait et activation */}
            <Route path="/app/billing/payment" element={<PaymentPage />} />
            {/* Lot L7 (E5) : cabinet archive (RG-TAR-07 a RG-TAR-09). */}
            <Route path="/app/archive" element={<ArchivePage />} />
          </Route>
        </Route>

        {/* Tickets / Workflows : SUPERVISEUR + EMPLOYE uniquement (RG-U07 exclut SUPER_ADMIN, RG-U09 exclut CLIENT) */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            <Route path="/tickets" element={<TicketsPage />} />
            {/* Lot L4d (E9) : page du ticket, une seule pour tous les parcours. */}
            <Route path="/tickets/:ticketId" element={<TicketPage />} />
            <Route path="/workflows/:ticketId" element={<WorkflowPage />} />
            {/* 2026-06-25 — Calendrier repurpose vers les tickets (source A). Gating
                aligne sur /tickets : le scope own-vs-all vient du backend GET /tickets. */}
            <Route path="/calendar" element={<CalendarPage />} />
          </Route>
        </Route>

        {/* BUG 6 (2026-06-07) — Gestion equipe : SUPERVISEUR uniquement. */}
        {/* E2 (2026-06-25) — Tracabilite : SUPERVISEUR (page complete workspace). */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR']} />}>
          <Route element={<AppShell />}>
            <Route path="/app/team" element={<TeamPage />} />
            {/* Lot L7 (E6) : contrat d'abonnement du cabinet (CDC 4.8). */}
            <Route path="/app/contrat" element={<ContratPage />} />
            <Route path="/tracabilite" element={<TracabilitePage />} />
            {/* Lot L1 (D1) : dossiers dont le responsable a ete designe d'office. */}
            <Route path="/dossiers-a-verifier" element={<DossiersAVerifierPage />} />
          </Route>
        </Route>

        {/* Data Room : SUPERVISEUR + EMPLOYE + CLIENT (RG-U07 exclut SUPER_ADMIN).
            Lot M (2026-06-30) — le CLIENT garde sa Data Room. */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE', 'CLIENT']} />}>
          <Route element={<AppShell />}>
            <Route path="/data-rooms" element={<DataroomPage />} />
          </Route>
        </Route>

        {/* Mes demandes : page Kanban dediee au CLIENT (suivi de ses demandes).
            Le client soumet + suit ; il ne change pas le statut. Miroir de
            /data-rooms cote structure (AppShell + ProtectedRoute). */}
        <Route element={<ProtectedRoute allowed={['CLIENT']} />}>
          <Route element={<AppShell />}>
            <Route path="/mes-demandes" element={<DemandesKanban />} />
            {/* Lot AG — requetes de son conseiller (employe -> client). */}
            <Route path="/mes-requetes" element={<MesRequetes />} />
          </Route>
        </Route>

        {/* Chat : SUPERVISEUR + EMPLOYE uniquement (RG-U10).
            Lot M (2026-06-30) — separe de /data-rooms : le CLIENT n'a plus de chat. */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            <Route path="/chat" element={<ChatPage />} />
            {/* Lot L4d (E7) : delais de depot au greffe par nature d'acte. */}
            <Route path="/delais-greffe" element={<DelaisGreffePage />} />
          </Route>
        </Route>

        {/* Lot L5 E8 (RG-COM-01) : le chatbot est un panneau lateral ouvert par la bulle de la
            mise en page commune. L'ancienne page pleine est supprimee ; /chatbot ouvre le panneau
            (cabinet) ou mene aux sources communes (super-admin). */}
        <Route element={<ProtectedRoute allowed={['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE']} />}>
          <Route path="/chatbot" element={<RedirectionChatbot />} />
        </Route>

        {/* SUPER_ADMIN : gestion plateforme uniquement */}
        <Route element={<ProtectedRoute allowed={['SUPER_ADMIN']} />}>
          <Route element={<AppShell />}>
            <Route path="/admin/workspaces" element={<WorkspacesPage />} />
            <Route path="/admin/workspaces/:workspaceId" element={<WorkspaceDetailPage />} />
            {/* Lot L7 (E5) : abonnements des cabinets et paiements a enregistrer (RG-PAY-02). */}
            <Route path="/admin/abonnements" element={<AbonnementsAdminPage />} />
            <Route path="/admin/paiements" element={<PaiementsAdminPage />} />
            <Route path="/admin/users" element={<UsersPage />} />
            <Route path="/admin/audit" element={<AuditLogPage />} />
            <Route path="/admin/referentiel-demarches" element={<ReferentielDemarchesPage />} />
            {/* Lot L5 E8 : sources fiables communes du chatbot (CDC 3.1, RG-ADM-03). */}
            <Route path="/admin/chatbot-sources" element={<ChatbotSourcesPage />} />
            {/* Lot L7 (E6) : contrats d'abonnement et parametres de l'editeur. */}
            <Route path="/admin/contrats" element={<ContratsAdminPage />} />
            <Route path="/admin/editeur" element={<EditeurParametresPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </ToastProvider>
    </BrowserRouter>
  );
}

/**
 * Pilote l'instance Lenis depuis l'intérieur du Router (useLenisScroll dépend
 * de useLocation). Ne rend rien.
 */
function LenisController() {
  useLenisScroll();
  return null;
}

export default App;
