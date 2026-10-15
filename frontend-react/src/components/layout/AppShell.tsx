import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { AppTopNav } from './AppTopNav';
import { ErrorBoundary } from './ErrorBoundary';
import { IceReminderBanner } from './IceReminderBanner';
import { BandeauEssai } from './BandeauEssai';
import { BandeauImpaye } from './BandeauImpaye';
import { AssistantJuridique } from '../chatbot/AssistantJuridique';
import { VisiteGuidee } from '../ui/VisiteGuidee';
import { useSessionKeepAlive } from '../../lib/useSessionKeepAlive';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { AttenteEmploye, EcranContratRestreint, VersContrat } from '../contrat/GardeContrat';
import { useGardeContrat } from '../contrat/useGardeContrat';

export function AppShell() {
  // 2026-07-26 — refresh proactif base activite : monte ici (uniquement pour les
  // routes authentifiees) pour que la session d'un utilisateur ACTIF soit
  // renouvelee avant l'expiration de l'access token, sans jamais le deconnecter
  // a tort pour "inactivite".
  useSessionKeepAlive();
  // Lot L6 (E6) : les aides masquees de l'utilisateur sont lues UNE fois par
  // session (le magasin ignore un second appel pour le meme utilisateur) ; le
  // rendu des aides ne fait ensuite aucun appel reseau.
  const userId = useAuthStore((s) => s.user?.userId);
  const chargerAides = useAidesStore((s) => s.charger);
  useEffect(() => {
    if (userId) void chargerAides(userId);
  }, [userId, chargerAides]);
  // 2026-06-30 — `key={pathname}` réinitialise l'ErrorBoundary à chaque
  // navigation : une erreur de rendu sur une route (ex. /chat) ne fige plus
  // les autres pages. La topnav reste HORS du boundary -> toujours cliquable.
  const { pathname } = useLocation();
  // Lot L7 (E6, RG-CTR-03) : contrat d'abonnement en attente d'acceptation : seul l'ecran du
  // contrat (superviseur) ou un message d'attente (employe), dans une mise en page reduite.
  const garde = useGardeContrat();
  if (garde === 'CHARGEMENT') {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-bg" role="status">
        <span className="sr-only">Vérification du contrat d’abonnement…</span>
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-border border-t-accent" aria-hidden="true" />
      </div>
    );
  }
  if (garde === 'REDIRIGER') return <VersContrat />;
  if (garde === 'ATTENTE_EMPLOYE') {
    return (
      <EcranContratRestreint>
        <AttenteEmploye />
      </EcranContratRestreint>
    );
  }
  if (garde === 'RESTREINT') {
    return (
      <EcranContratRestreint>
        <ErrorBoundary key={pathname}>
          <Outlet />
        </ErrorBoundary>
      </EcranContratRestreint>
    );
  }
  return (
    <div className="flex min-h-screen flex-col bg-bg text-fg">
      <AppTopNav />
      {/* Simplification inscription (2026-07-13) — rappel discret si ICE manquant. */}
      <IceReminderBanner />
      {/* Lot L7 (E4) : essai gratuit (rappels J-3 et J-1, lecture seule, date de suppression). */}
      <BandeauEssai />
      {/* Lot L7 (E8, article 4.4 du contrat) : échéance impayée, date de suspension, comment payer. */}
      <BandeauImpaye />
      <main className="flex-1">
        <div className="mx-auto max-w-7xl px-6 py-8">
          <ErrorBoundary key={pathname}>
            <Outlet />
          </ErrorBoundary>
        </div>
      </main>
      {/* Lot L5 E8 (RG-COM-01) : bulle du chatbot sur toutes les pages (roles du cabinet et super-admin).
          Lot L6 (E6) : cible "chatbot" de la visite guidee. */}
      <div data-visite="chatbot">
        <AssistantJuridique />
      </div>
      {/* Lot L6 (E6, CDC 15.4) : premiere visite guidee du role. */}
      <VisiteGuidee />
    </div>
  );
}
