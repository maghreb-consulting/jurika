import { useMemo } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import {
  Bell,
  Building,
  CreditCard,
  Globe,
  ServerCog,
  Shield,
  User,
  Users,
} from 'lucide-react';
import { useCurrentUser } from '../../store/authStore';
import { ProfilePage } from '../account/ProfilePage';
import { ProfileSecurityPage } from '../account/ProfileSecurityPage';
import { TeamPage } from '../team/TeamPage';
import { BillingPage } from '../billing/BillingPage';
import { CabinetTab } from './CabinetTab';
import { NotificationPreferencesTab } from './NotificationPreferencesTab';
import { PreferencesTab } from './PreferencesTab';
import { PlatformSettingsTab } from './PlatformSettingsTab';
import type { Role } from '../../types/auth';
import { TexteAide } from '../../components/ui/Aide';
import { Page } from '../../components/layout/Page';
import { OngletsParametres } from './EcranOuOnglet';

/**
 * BUG 11 (2026-06-08) — Page Parametres SaaS a onglets lateraux.
 *
 *  - Reutilise les pages existantes pour Profil/Securite/Equipe/Facturation,
 *    de sorte qu'il existe UNE seule implementation par sujet (pas de fork
 *    d'UI). Les onglets s'affichent comme des nested routes sous /settings.
 *  - Onglet Equipe visible uniquement par le SUPERVISEUR (RG-U02 / S4 BUG6).
 *  - Onglet Facturation visible pour tout role authentifie (mais le contenu
 *    de BillingPage gere lui-meme le RBAC fin / l'absence d'abonnement).
 *  - Notifications & Preferences sont des composants legers dedies a cette
 *    page (opt-in/opt-out par type, langue, theme).
 */
interface Tab {
  to: string;
  label: string;
  icon: typeof User;
  roles?: Role[];
  testid: string;
}

// Chemins ABSOLUS pour eviter une boucle de redirect : un NavLink relatif
// resout par rapport a la location courante, pas par rapport au parent route
// (/settings/profile + to="team" -> /settings/profile/team -> path="*" ->
// Navigate to="profile" relatif -> /settings/profile/team/profile -> boucle).
const TABS: Tab[] = [
  { to: '/settings/profile', label: 'Profil', icon: User, testid: 'settings-tab-profile' },
  { to: '/settings/security', label: 'Sécurité', icon: Shield, testid: 'settings-tab-security' },
  // Simplification inscription (2026-07-13) — completion ICE / infos cabinet (SUPERVISEUR).
  { to: '/settings/cabinet', label: 'Cabinet', icon: Building, roles: ['SUPERVISEUR'], testid: 'settings-tab-cabinet' },
  { to: '/settings/team', label: 'Équipe', icon: Users, roles: ['SUPERVISEUR'], testid: 'settings-tab-team' },
  // Facturation = facturation du cabinet : masquee au CLIENT et au SUPER_ADMIN
  // (pas de workspace facturable cote plateforme).
  { to: '/settings/billing', label: 'Facturation', icon: CreditCard, roles: ['SUPERVISEUR', 'EMPLOYE'], testid: 'settings-tab-billing' },
  { to: '/settings/notifications', label: 'Notifications', icon: Bell, testid: 'settings-tab-notifications' },
  { to: '/settings/preferences', label: 'Préférences', icon: Globe, testid: 'settings-tab-preferences' },
  // Reglages plateforme (origines CORS…) — SUPER_ADMIN uniquement.
  { to: '/settings/platform', label: 'Plateforme', icon: ServerCog, roles: ['SUPER_ADMIN'], testid: 'settings-tab-platform' },
];

export function SettingsPage() {
  const user = useCurrentUser();

  const visibleTabs = useMemo(
    () => TABS.filter((t) => !t.roles || (user && t.roles.includes(user.role))),
    [user],
  );

  return (
    <Page
      titre="Paramètres"
      description="Votre compte, sa sécurité, vos notifications et vos préférences ; pour le responsable du cabinet, la fiche du cabinet, l’équipe et la facturation."
      aide={
        <TexteAide cle="parametres" titre="Comment utiliser les paramètres">
          <p>
            Choisissez un onglet dans la colonne de gauche : chaque onglet s’enregistre séparément. Les
            onglets affichés dépendent de votre rôle.
          </p>
        </TexteAide>
      }
    >
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[13.75rem_1fr]" data-testid="settings-page">
        {/* Onglets lateraux (defilement horizontal sur petit ecran) ; l'onglet actif porte l'or. */}
        <nav
          className="flex flex-row gap-1 overflow-x-auto rounded-xl border border-border bg-bg-raised p-2 md:flex-col md:self-start md:overflow-visible"
          aria-label="Onglets des paramètres"
        >
          {visibleTabs.map((t) => {
            const Icon = t.icon;
            return (
              <NavLink
                key={t.to}
                to={t.to}
                data-testid={t.testid}
                className={({ isActive }) =>
                  `flex shrink-0 items-center gap-2 whitespace-nowrap rounded-lg border-l-2 px-3 py-2 text-sm transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus ${
                    isActive
                      ? 'border-l-accent bg-accent-soft font-semibold text-fg'
                      : 'border-l-transparent text-fg-muted hover:bg-bg-overlay hover:text-fg'
                  }`
                }
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                {t.label}
              </NavLink>
            );
          })}
        </nav>

        {/* Panneau actif : chaque ecran s'y affiche en section (h2), sans second en-tete de page. */}
        <div className="min-w-0">
          <OngletsParametres>
            {/* Redirect cibles ABSOLUES (cf. note sur TABS plus haut). */}
            <Routes>
              <Route index element={<Navigate to="/settings/profile" replace />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="security" element={<ProfileSecurityPage />} />
              {user?.role === 'SUPERVISEUR' && <Route path="cabinet" element={<CabinetTab />} />}
              {user?.role === 'SUPERVISEUR' && <Route path="team" element={<TeamPage />} />}
              {/* Deep-link /settings/billing interdit au CLIENT et au SUPER_ADMIN. */}
              {user?.role !== 'CLIENT' && user?.role !== 'SUPER_ADMIN' && (
                <Route path="billing" element={<BillingPage />} />
              )}
              <Route path="notifications" element={<NotificationPreferencesTab />} />
              <Route path="preferences" element={<PreferencesTab />} />
              {user?.role === 'SUPER_ADMIN' && <Route path="platform" element={<PlatformSettingsTab />} />}
              <Route path="*" element={<Navigate to="/settings/profile" replace />} />
            </Routes>
          </OngletsParametres>
        </div>
      </div>
    </Page>
  );
}
