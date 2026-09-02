import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  Bot,
  Building2,
  ChevronDown,
  History,
  Home,
  Inbox,
  Lock,
  LogOut,
  MessageSquare,
  ScrollText,
  Settings,
  Shield,
  Ticket,
  Users,
  Zap,
} from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { BrandLogo } from '../ui/BrandLogo';
import { useChatUnreadStore, useChatUnreadSync } from '../../store/chatUnreadStore';
import type { Role } from '../../types/auth';
import { DeadlineCountWidget } from './DeadlineCountWidget';
import { NotificationBell } from './NotificationBell';
import { ThemeToggle } from './ThemeToggle';

interface NavItem {
  label: string;
  to: string;
  icon: typeof Home;
  roles: Role[];
  badge?: number;
}

// Matrice nav par role (cf docs/v2/Plan_Refonte_Roles.md)
// RG-U07 : SUPER_ADMIN n'accede pas aux tickets/dossiers/documents
// RG-U02-04 : SUPERVISEUR consulte tout en lecture seule
// RG-U08 : EMPLOYE ne voit que ses propres tickets/Data Rooms
// RG-U09 : CLIENT n'accede qu'a son Data Room + Chat avec son employe
// RG-U10 : Chatbot reserve SUPERVISEUR + EMPLOYE
const NAV_ITEMS: NavItem[] = [
  { label: 'Tableau de bord', to: '/dashboard', icon: Home, roles: ['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE', 'CLIENT'] },
  // SUPERVISEUR -> "Tous les Tickets", EMPLOYE -> "Mes Tickets" (label dynamique)
  { label: 'Mes Tickets', to: '/tickets', icon: Ticket, roles: ['EMPLOYE'] },
  { label: 'Tous les Tickets', to: '/tickets', icon: Ticket, roles: ['SUPERVISEUR'] },
  { label: 'Mes Data Rooms', to: '/data-rooms', icon: Lock, roles: ['EMPLOYE'] },
  { label: 'Tous les Data Rooms', to: '/data-rooms', icon: Lock, roles: ['SUPERVISEUR'] },
  { label: 'Mon Espace', to: '/data-rooms', icon: Lock, roles: ['CLIENT'] },
  // Page Kanban dediee CLIENT : suivi de ses demandes (Non traitee / En cours / Traitee).
  { label: 'Mes demandes', to: '/mes-demandes', icon: MessageSquare, roles: ['CLIENT'] },
  // Lot AG — requetes de son conseiller (employe -> client).
  { label: 'Demandes de mon conseiller', to: '/mes-requetes', icon: Inbox, roles: ['CLIENT'] },
  // Lot M (2026-06-30) — Chat retire au CLIENT (experience reduite : Dashboard + Data Room).
  { label: 'Chat', to: '/chat', icon: MessageSquare, roles: ['SUPERVISEUR', 'EMPLOYE'] },
  { label: 'ChatBot IA', to: '/chatbot', icon: Bot, roles: ['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE'] },
  // E2 (2026-06-25) — Tracabilite : SUPERVISEUR
  { label: 'Tracabilite', to: '/tracabilite', icon: History, roles: ['SUPERVISEUR'] },
  // SUPER_ADMIN : gestion plateforme uniquement (RG-U07). ChatBot IA reste
  // autorise (admin des sources fiables du RAG, cf. decision 2026-06-26).
  { label: 'Workspaces', to: '/admin/workspaces', icon: Building2, roles: ['SUPER_ADMIN'] },
  { label: 'Utilisateurs', to: '/admin/users', icon: Users, roles: ['SUPER_ADMIN'] },
  { label: "Journal d'audit", to: '/admin/audit', icon: ScrollText, roles: ['SUPER_ADMIN'] },
];

/**
 * Sprint 12.5 T3 — AppTopNav repeinte sur tokens marketing.
 *
 * - bg navy-800 (bg-raised) au lieu de slate-900 hardcoded
 * - bordures border-border-hi au lieu de #1E293B
 * - foreground passe sur tokens text-fg / text-fg-muted / text-fg-subtle
 * - logo : embleme JURIKA (BrandLogo) au lieu de l'ancien placeholder Sparkles
 * - texte "JURIKA" passe en font-heading (Playfair Display signature)
 * - active link : bandeau gold avec border-l-2 accent (pattern Linear/Stripe)
 * - CTA Upgrade : bg-accent (or signature) au lieu de amber-500
 * - badges role/notifications conservés rose pour danger affordance
 */
export function AppTopNav() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  // Badge « Chat » = vrai nombre de messages non lus (remplace l'ancien 3 figé).
  useChatUnreadSync();
  const chatUnread = useChatUnreadStore((s) => s.total);

  if (!user) return null;

  const visible = NAV_ITEMS.filter((it) => it.roles.includes(user.role));
  const initials = (user.email[0] ?? 'U').toUpperCase();
  // 2026-06-22 — Affiche le VRAI code workspace (saisi au login, persiste dans le
  // store). Fallback sur l'ancien derive-de-l'UUID si absent (anciennes sessions).
  const wsCode = user.workspaceCode ?? `JUR-${user.workspaceId.slice(0, 5).toUpperCase()}`;
  const isSupervisor = user.role === 'SUPERVISEUR';

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="sticky top-0 z-40 flex h-16 items-center gap-4 bg-bg-raised px-6 shadow-card">
      {/* Logo + Workspace */}
      <Link to="/dashboard" className="flex items-center gap-3">
        <BrandLogo size={36} />
        <div className="border-l border-border-hi pl-3">
          <p className="font-heading text-base font-semibold leading-none text-fg">JURIKA</p>
          <p className="font-mono text-[10px] tracking-wide text-fg-subtle">{wsCode}</p>
        </div>
      </Link>

      {/* Nav Items */}
      <nav className="flex flex-1 items-center gap-0.5 overflow-x-auto">
        {visible.map((item) => {
          const Icon = item.icon;
          const badgeCount = item.to === '/chat' ? chatUnread : item.badge ?? 0;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `relative flex flex-shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-accent/15 text-accent shadow-[inset_2px_0_0_var(--color-accent)]'
                    : 'text-fg-muted hover:bg-bg-overlay hover:text-fg'
                }`
              }
            >
              <Icon className="h-4 w-4 flex-shrink-0" />
              {item.label}
              {badgeCount > 0 && (
                <span className="min-w-[18px] rounded-full bg-danger px-1.5 py-0.5 text-center text-[10px] font-bold leading-none text-fg">
                  {badgeCount}
                </span>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Right Actions */}
      <div className="flex flex-shrink-0 items-center gap-2">
        {isSupervisor && (
          <span className="rounded bg-accent/15 px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-accent">
            Superviseur
          </span>
        )}

        {/* BUG 8 (2026-06-07) — bouton Upgrade actif : route vers
            /app/billing/change-plan (PaymentPage si abonnement actif via
            "Payer par autre moyen", BillingPage sinon). SUPER_ADMIN n'a pas
            de workspace facturable -> bouton cache.
            Lot M (2026-06-30) — CLIENT exclu aussi (pas de facturation). */}
        {user.role !== 'SUPER_ADMIN' && user.role !== 'CLIENT' && (
          <Link
            to="/app/billing/change-plan"
            className="flex items-center gap-1.5 whitespace-nowrap rounded-md bg-accent px-3 py-1.5 text-xs font-bold text-bg transition hover:bg-accent-hover"
            data-testid="topnav-upgrade-cta"
          >
            <Zap className="h-3.5 w-3.5" />
            Upgrade
          </Link>
        )}

        <DeadlineCountWidget />

        <ThemeToggle />

        {/* BUG 10 (2026-06-08) — cloche dynamique : badge = unread-count
            realtime, drawer cliquable, navigation actionUrl. */}
        <NotificationBell />

        <div className="h-6 w-px bg-border-hi" />

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            className="flex items-center gap-2 rounded-md px-2 py-1.5 transition hover:bg-bg-overlay"
          >
            <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-accent text-xs font-bold text-bg">
              {initials}
            </div>
            <div className="hidden text-left sm:block">
              <p className="text-sm font-medium leading-none text-fg">{user.email}</p>
              <p className="text-[10px] text-fg-subtle">{roleLabel(user.role)}</p>
            </div>
            <ChevronDown className="h-4 w-4 text-fg-subtle" />
          </button>

          {menuOpen && (
            <div
              className="absolute right-0 top-full mt-1 w-56 overflow-hidden rounded-lg border border-border-hi bg-bg-overlay shadow-card-lifted"
              onMouseLeave={() => setMenuOpen(false)}
            >
              <div className="border-b border-border-hi px-4 py-3">
                <p className="text-[10px] uppercase tracking-wide text-fg-subtle">Workspace</p>
                <p className="mt-0.5 font-mono text-sm text-fg">{wsCode}</p>
              </div>
              <Link
                to="/settings/security"
                onClick={() => setMenuOpen(false)}
                className="flex w-full items-center gap-2 px-4 py-2.5 text-sm text-fg-muted transition hover:bg-bg-raised hover:text-fg"
              >
                <Shield className="h-4 w-4" /> Securite & 2FA
              </Link>
              {/* BUG 11 (2026-06-08) — bouton Parametres branche sur la
                  vraie page a onglets /settings (Profil, Securite, Equipe,
                  Facturation, Notifications, Preferences). */}
              <Link
                to="/settings"
                onClick={() => setMenuOpen(false)}
                className="flex w-full items-center gap-2 px-4 py-2.5 text-sm text-fg-muted transition hover:bg-bg-raised hover:text-fg"
              >
                <Settings className="h-4 w-4" /> Parametres
              </Link>
              <button
                type="button"
                onClick={handleLogout}
                className="flex w-full items-center gap-2 border-t border-border-hi px-4 py-2.5 text-left text-sm text-danger transition hover:bg-bg-raised"
              >
                <LogOut className="h-4 w-4" /> Deconnexion
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

function roleLabel(role: Role): string {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'Super Admin';
    case 'SUPERVISEUR':
      return 'Superviseur';
    case 'EMPLOYE':
      return 'Employe';
    case 'CLIENT':
      return 'Client';
  }
}
