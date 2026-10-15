import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { AppEnTete } from './AppEnTete';
import { MenuLateral } from './MenuLateral';
import { ErrorBoundary } from './ErrorBoundary';
import { ErreurPreferences } from './ErreurPreferences';
import { IceReminderBanner } from './IceReminderBanner';
import { BandeauEssai } from './BandeauEssai';
import { BandeauImpaye } from './BandeauImpaye';
import { AssistantJuridique } from '../chatbot/AssistantJuridique';
import { VisiteGuidee } from '../ui/VisiteGuidee';
import { SquelettePage } from '../ui/LoadingSkeleton';
import { useSessionKeepAlive } from '../../lib/useSessionKeepAlive';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { AttenteEmploye, EcranContratRestreint, VersContrat } from '../contrat/GardeContrat';
import { useGardeContrat } from '../contrat/useGardeContrat';

const CLE_MENU_REPLIE = 'jurika.menu.replie';

function lireMenuReplie(): boolean {
  try {
    return window.localStorage.getItem(CLE_MENU_REPLIE) === '1';
  } catch {
    return false;
  }
}

/**
 * Coquille commune des ecrans authentifies (lot L6b, E3).
 *
 *   +--------------------------------------------------------------+
 *   | en-tete : logo, (recherche), echeances, theme, cloche, compte |
 *   +-----------+--------------------------------------------------+
 *   | menu      | bandeaux (ICE, essai, impaye, preferences)       |
 *   | lateral   | <main> : fil d'Ariane, titre et action (Page),   |
 *   | par role  |          contenu de l'ecran                      |
 *   +-----------+--------------------------------------------------+
 *
 * DEFILEMENT : c'est la PAGE qui defile (molette partout, barre du navigateur). L'en-tete est
 * collant, le menu est collant sous l'en-tete et ne defile que s'il depasse l'ecran. Aucun
 * conteneur a hauteur fixe autour du contenu (la cause des molettes bloquees) ; le
 * defilement doux par script (Lenis) est retire.
 *
 * Ecrans charges a la demande : <Suspense> autour de la sortie, avec un squelette aux
 * dimensions d'une page (aucun decalage quand l'ecran arrive).
 *
 * Reprises : session renouvelee (keep-alive), aides masquees lues une fois par session,
 * ErrorBoundary reinitialisee a chaque navigation (key=pathname), garde du contrat
 * (lot L7 E6, RG-CTR-03), bandeaux d'essai et d'impaye, bulle du chatbot, visite guidee.
 */
export function AppShell() {
  useSessionKeepAlive();
  const userId = useAuthStore((s) => s.user?.userId);
  const role = useAuthStore((s) => s.user?.role);
  const chargerAides = useAidesStore((s) => s.charger);
  const chargerPreferences = usePreferencesStore((s) => s.charger);
  useEffect(() => {
    if (!userId) return;
    void chargerAides(userId);
    void chargerPreferences(userId);
  }, [userId, chargerAides, chargerPreferences]);

  const { pathname } = useLocation();
  const [replie, setReplie] = useState(lireMenuReplie);
  const [menuMobile, setMenuMobile] = useState(false);

  function basculerMenu() {
    setReplie((r) => {
      try {
        window.localStorage.setItem(CLE_MENU_REPLIE, r ? '0' : '1');
      } catch {
        // Stockage indisponible : le reglage vaut pour cet ecran.
      }
      return !r;
    });
  }

  const garde = useGardeContrat();
  if (garde === 'CHARGEMENT') {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-bg" role="status">
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
          <Suspense fallback={<SquelettePage />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </EcranContratRestreint>
    );
  }

  return (
    <div className="min-h-dvh bg-bg text-fg">
      <a
        href="#contenu"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[60] focus:rounded-lg focus:bg-bg-raised focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-fg focus:shadow-card-lifted"
      >
        Aller au contenu
      </a>
      <AppEnTete onOuvrirMenuMobile={() => setMenuMobile(true)} />
      <div className="flex">
        {role && (
          <aside
            className="sticky top-[var(--hauteur-entete)] hidden h-[calc(100dvh-var(--hauteur-entete))] shrink-0 md:block"
            data-testid="menu-lateral"
          >
            <MenuLateral role={role} replie={replie} onBasculer={basculerMenu} />
          </aside>
        )}
        <div className="@container/page min-w-0 flex-1">
          <IceReminderBanner />
          <BandeauEssai />
          <BandeauImpaye />
          <ErreurPreferences />
          <main id="contenu" tabIndex={-1} className="mx-auto w-full max-w-7xl px-4 py-6 outline-none md:px-8">
            <ErrorBoundary key={pathname}>
              <Suspense fallback={<SquelettePage />}>
                <Outlet />
              </Suspense>
            </ErrorBoundary>
          </main>
        </div>
      </div>
      {role && menuMobile && (
        <div
          className="fixed inset-0 z-50 flex md:hidden"
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
          onKeyDown={(e) => {
            if (e.key === 'Escape') setMenuMobile(false);
          }}
        >
          <div className="h-full shadow-card-lifted">
            <MenuLateral role={role} replie={false} onBasculer={basculerMenu} mobile onNaviguer={() => setMenuMobile(false)} />
          </div>
          <button
            type="button"
            className="flex-1 bg-scrim/50"
            aria-label="Fermer le menu"
            onClick={() => setMenuMobile(false)}
          />
        </div>
      )}
      {/* Lot L5 E8 (RG-COM-01) : bulle du chatbot ; lot L6 (E6) : cible "chatbot" de la visite. */}
      <div data-visite="chatbot">
        <AssistantJuridique />
      </div>
      {/* Lot L6 (E6, CDC 15.4) : premiere visite guidee du role. */}
      <VisiteGuidee />
    </div>
  );
}
