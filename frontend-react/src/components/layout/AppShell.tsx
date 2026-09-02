import { Outlet, useLocation } from 'react-router-dom';
import { AppTopNav } from './AppTopNav';
import { ErrorBoundary } from './ErrorBoundary';
import { IceReminderBanner } from './IceReminderBanner';
import { useSessionKeepAlive } from '../../lib/useSessionKeepAlive';

export function AppShell() {
  // 2026-07-26 — refresh proactif base activite : monte ici (uniquement pour les
  // routes authentifiees) pour que la session d'un utilisateur ACTIF soit
  // renouvelee avant l'expiration de l'access token, sans jamais le deconnecter
  // a tort pour "inactivite".
  useSessionKeepAlive();
  // 2026-06-30 — `key={pathname}` réinitialise l'ErrorBoundary à chaque
  // navigation : une erreur de rendu sur une route (ex. /chat) ne fige plus
  // les autres pages. La topnav reste HORS du boundary -> toujours cliquable.
  const { pathname } = useLocation();
  return (
    <div className="flex min-h-screen flex-col bg-bg text-fg">
      <AppTopNav />
      {/* Simplification inscription (2026-07-13) — rappel discret si ICE manquant. */}
      <IceReminderBanner />
      <main className="flex-1">
        <div className="mx-auto max-w-7xl px-6 py-8">
          <ErrorBoundary key={pathname}>
            <Outlet />
          </ErrorBoundary>
        </div>
      </main>
    </div>
  );
}
