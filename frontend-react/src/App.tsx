import { Suspense, useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { AppShell } from './components/layout/AppShell';
import { RedirectionChatbot } from './components/chatbot/RedirectionChatbot';
import { useAuthStore } from './store/authStore';
import { useThemeStore } from './store/themeStore';
import { ToastProvider } from './components/ui/Toast';
import { Ecrans } from './routes/pages';
import { useRenvoiBrouillons } from './pages/workflow/useRenvoiBrouillons';

const {
  AbonnementsAdminPage,
  ArchivePage,
  AuditLogPage,
  BillingPage,
  CalendarPage,
  CguPage,
  ChangePlanPage,
  ChatPage,
  ChatbotSourcesPage,
  Choose2faMethodPage,
  ConfidentialitePage,
  ConfirmerChangementComptePage,
  ContratPage,
  ContratsAdminPage,
  DashboardRouter,
  DataroomPage,
  DelaisGreffePage,
  DemandesKanban,
  DesignPage,
  DossiersAVerifierPage,
  EditeurParametresPage,
  EssaiConfirmerPage,
  EssaiGratuitPage,
  EssaisPage,
  ForbiddenPage,
  ForceChangePasswordPage,
  LandingPage,
  LoginPage,
  MentionsLegalesPage,
  MesRequetes,
  PaiementRetourPage,
  PaiementsAdminPage,
  PaymentPage,
  ProfilePage,
  ProfileSecurityPage,
  RecoverWithCodePage,
  ReferentielDemarchesPage,
  ResetPasswordPage,
  SettingsPage,
  Setup2FAPage,
  SignupPage,
  TeamPage,
  TicketPage,
  TicketsPage,
  TracabilitePage,
  UsersPage,
  VerifyEmailPage,
  WorkflowPage,
  WorkspaceDetailPage,
  WorkspacesPage,
} = Ecrans;

function App() {
  const hydrate = useAuthStore((s) => s.hydrate);
  const applyDomTheme = useThemeStore((s) => s.applyDomTheme);

  // Lot L7 (E12) : saisies de parcours gardees dans ce navigateur, renvoyees a la reconnexion.
  useRenvoiBrouillons();

  const suivreSysteme = useThemeStore((s) => s.suivreSysteme);

  useEffect(() => {
    // Le script de index.html a deja pose data-theme avant le premier rendu (pas d'eclair) ;
    // le magasin le confirme, puis suit le reglage de l'ordinateur (choix "Systeme").
    applyDomTheme();
    hydrate();
    return suivreSysteme();
  }, [applyDomTheme, hydrate, suivreSysteme]);

  return (
    <BrowserRouter>
      {/* Lot L6b (E3) : le defilement doux par script (Lenis) et la texture plein ecran
          sont retires : la molette fait defiler la page nativement, partout. */}
      {/* Toast à la charte, monté une seule fois pour toute l'app (retours
          transitoires succès/erreur). Enveloppe les routes pour que useToast()
          soit disponible partout. */}
      <ToastProvider>
      {/* Lot L6b (E3) : ecrans charges a la demande. Hors coquille (pages publiques), un fond
          vide le temps du chargement ; dans la coquille, AppShell montre un squelette. */}
      <Suspense fallback={<ChargementPublic />}>
      <Routes>
        {/* Routes publiques */}
        <Route path="/" element={<LandingPage.Composant />} />
        <Route path="/login" element={<LoginPage.Composant />} />
        <Route path="/signup" element={<SignupPage.Composant />} />
        {/* Lot L7 (E4) : inscription a l'essai gratuit (CDC 4.2), distincte de l'inscription payante. */}
        <Route path="/essai" element={<EssaiGratuitPage.Composant />} />
        <Route path="/essai/confirmer" element={<EssaiConfirmerPage.Composant />} />
        <Route path="/register" element={<Navigate to="/signup" replace />} />
        {/* Lot L7 (E5), RG-PAY-05 : retour du navigateur apres la page du CMI (aussi a l'inscription,
            avec le jeton transitoire) ; l'etat du paiement est lu sur le serveur, jamais deduit du retour. */}
        <Route path="/app/billing/paiement/retour" element={<PaiementRetourPage.Composant />} />
        <Route path="/billing/success" element={<Navigate to="/app/billing" replace />} />
        <Route path="/app/billing/success" element={<Navigate to="/app/billing" replace />} />
        <Route path="/reset-password" element={<ResetPasswordPage.Composant />} />
        <Route path="/auth/recover-with-code" element={<RecoverWithCodePage.Composant />} />
        <Route path="/verify-email" element={<VerifyEmailPage.Composant />} />
        {/* Lot L5 (E10) : lien de confirmation d'un changement d'adresse ou d'identifiant. */}
        <Route path="/confirmer-changement-compte" element={<ConfirmerChangementComptePage.Composant />} />
        <Route path="/forbidden" element={<ForbiddenPage.Composant />} />

        {/* Sprint Beta (pricing-deploy) -- pages legales publiques (TASK 6) */}
        <Route path="/cgu" element={<CguPage.Composant />} />
        <Route path="/conditions-generales" element={<CguPage.Composant />} />
        <Route path="/confidentialite" element={<ConfidentialitePage.Composant />} />
        <Route path="/privacy" element={<ConfidentialitePage.Composant />} />
        {/* Lot L6b (E10) : mentions legales de l'editeur, lues dans ses Parametres (route publique). */}
        <Route path="/mentions-legales" element={<MentionsLegalesPage.Composant />} />

        {/* Tous roles authentifies : compte + dashboard */}
        <Route element={<ProtectedRoute />}>
          <Route path="/account/2fa" element={<Setup2FAPage.Composant />} />
          <Route path="/account/change-password" element={<ForceChangePasswordPage.Composant />} />
          <Route path="/account/2fa-choose" element={<Choose2faMethodPage.Composant />} />
          <Route path="/account/security" element={<ProfileSecurityPage.Composant />} />
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<DashboardRouter.Composant />} />
            {/* BUG 7 (chore 2026-06-08) — page Profil : loginEmail readonly + contactEmail editable */}
            <Route path="/profile" element={<ProfilePage.Composant />} />
            {/* BUG 11 (2026-06-08) — Page Parametres SaaS a onglets. Le sous-onglet
                Equipe est conditionne au role SUPERVISEUR dans la page elle-meme.
                Le sous-onglet/route Facturation est gate au role dans la page (Lot M). */}
            <Route path="/settings/*" element={<SettingsPage.Composant />} />
          </Route>
        </Route>

        {/* Lot M (2026-06-30) — Facturation : SUPER_ADMIN + SUPERVISEUR + EMPLOYE.
            CLIENT exclu (experience reduite, pas d'upgrade/abonnement/facturation). */}
        <Route element={<ProtectedRoute allowed={['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            {/* Sprint 11 TASK 4 -- billing/trial upgrade page */}
            <Route path="/app/billing" element={<BillingPage.Composant />} />
            {/* BUG 8 (2026-06-07) -- changement de forfait (route depuis topnav Upgrade) */}
            <Route path="/app/billing/change-plan" element={<ChangePlanPage.Composant />} />
            {/* BUG 14 (2026-06-07) -- page paiement multi-moyens entre choix forfait et activation */}
            <Route path="/app/billing/payment" element={<PaymentPage.Composant />} />
            {/* Lot L7 (E5) : cabinet archive (RG-TAR-07 a RG-TAR-09). */}
            <Route path="/app/archive" element={<ArchivePage.Composant />} />
          </Route>
        </Route>

        {/* Tickets / Workflows : SUPERVISEUR + EMPLOYE uniquement (RG-U07 exclut SUPER_ADMIN, RG-U09 exclut CLIENT) */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            <Route path="/tickets" element={<TicketsPage.Composant />} />
            {/* Lot L4d (E9) : page du ticket, une seule pour tous les parcours. */}
            <Route path="/tickets/:ticketId" element={<TicketPage.Composant />} />
            <Route path="/workflows/:ticketId" element={<WorkflowPage.Composant />} />
            {/* 2026-06-25 — Calendrier repurpose vers les tickets (source A). Gating
                aligne sur /tickets : le scope own-vs-all vient du backend GET /tickets. */}
            <Route path="/calendar" element={<CalendarPage.Composant />} />
          </Route>
        </Route>

        {/* BUG 6 (2026-06-07) — Gestion equipe : SUPERVISEUR uniquement. */}
        {/* E2 (2026-06-25) — Tracabilite : SUPERVISEUR (page complete workspace). */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR']} />}>
          <Route element={<AppShell />}>
            <Route path="/app/team" element={<TeamPage.Composant />} />
            {/* Lot L7 (E6) : contrat d'abonnement du cabinet (CDC 4.8). */}
            <Route path="/app/contrat" element={<ContratPage.Composant />} />
            <Route path="/tracabilite" element={<TracabilitePage.Composant />} />
            {/* Lot L1 (D1) : dossiers dont le responsable a ete designe d'office. */}
            <Route path="/dossiers-a-verifier" element={<DossiersAVerifierPage.Composant />} />
          </Route>
        </Route>

        {/* Data Room : SUPERVISEUR + EMPLOYE + CLIENT (RG-U07 exclut SUPER_ADMIN).
            Lot M (2026-06-30) — le CLIENT garde sa Data Room. */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE', 'CLIENT']} />}>
          <Route element={<AppShell />}>
            <Route path="/data-rooms" element={<DataroomPage.Composant />} />
          </Route>
        </Route>

        {/* Mes demandes : page Kanban dediee au CLIENT (suivi de ses demandes).
            Le client soumet + suit ; il ne change pas le statut. Miroir de
            /data-rooms cote structure (AppShell + ProtectedRoute). */}
        <Route element={<ProtectedRoute allowed={['CLIENT']} />}>
          <Route element={<AppShell />}>
            <Route path="/mes-demandes" element={<DemandesKanban.Composant />} />
            {/* Lot AG — requetes de son conseiller (employe -> client). */}
            <Route path="/mes-requetes" element={<MesRequetes.Composant />} />
          </Route>
        </Route>

        {/* Chat : SUPERVISEUR + EMPLOYE uniquement (RG-U10).
            Lot M (2026-06-30) — separe de /data-rooms : le CLIENT n'a plus de chat. */}
        <Route element={<ProtectedRoute allowed={['SUPERVISEUR', 'EMPLOYE']} />}>
          <Route element={<AppShell />}>
            <Route path="/chat" element={<ChatPage.Composant />} />
            {/* Lot L4d (E7) : delais de depot au greffe par nature d'acte. */}
            <Route path="/delais-greffe" element={<DelaisGreffePage.Composant />} />
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
            <Route path="/admin/workspaces" element={<WorkspacesPage.Composant />} />
            <Route path="/admin/workspaces/:workspaceId" element={<WorkspaceDetailPage.Composant />} />
            {/* Lot L7 (E5) : abonnements des cabinets et paiements a enregistrer (RG-PAY-02). */}
            <Route path="/admin/abonnements" element={<AbonnementsAdminPage.Composant />} />
            <Route path="/admin/paiements" element={<PaiementsAdminPage.Composant />} />
            <Route path="/admin/users" element={<UsersPage.Composant />} />
            {/* Lot L7 (E4) : essais gratuits (RG-ESS-03, RG-ESS-11). */}
            <Route path="/admin/essais" element={<EssaisPage.Composant />} />
            <Route path="/admin/audit" element={<AuditLogPage.Composant />} />
            <Route path="/admin/referentiel-demarches" element={<ReferentielDemarchesPage.Composant />} />
            {/* Lot L5 E8 : sources fiables communes du chatbot (CDC 3.1, RG-ADM-03). */}
            <Route path="/admin/chatbot-sources" element={<ChatbotSourcesPage.Composant />} />
            {/* Lot L7 (E6) : contrats d'abonnement et parametres de l'editeur. */}
            <Route path="/admin/contrats" element={<ContratsAdminPage.Composant />} />
            <Route path="/admin/editeur" element={<EditeurParametresPage.Composant />} />
            {/* Lot L6b (E1) : systeme de design (composants, etats, themes). */}
            <Route path="/design" element={<DesignPage.Composant />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </Suspense>
      </ToastProvider>
    </BrowserRouter>
  );
}

/** Attente du code d'une page publique : fond de page, annonce pour les lecteurs d'ecran. */
function ChargementPublic() {
  return (
    <div className="min-h-dvh bg-bg" role="status">
      <span className="sr-only">Chargement…</span>
    </div>
  );
}

export default App;
