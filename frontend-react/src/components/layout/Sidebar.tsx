import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  Bell,
  Bot,
  Building2,
  Calendar,
  ChevronLeft,
  ChevronRight,
  History,
  Home,
  Inbox,
  Lock,
  LogOut,
  MessageSquare,
  ScrollText,
  Settings,
  Ticket,
  Users,
  Zap,
} from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { BrandLogo } from '../ui/BrandLogo';
import { useChatUnreadStore, useChatUnreadSync } from '../../store/chatUnreadStore';
import type { Role } from '../../types/auth';

interface SidebarItem {
  label: string;
  to: string;
  icon: typeof Home;
  roles: Role[];
  badge?: number;
}

const ITEMS: SidebarItem[] = [
  { label: 'Mon Dashboard', to: '/dashboard', icon: Home, roles: ['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE', 'CLIENT'] },
  // RG-U07 — le SUPER_ADMIN n'accede pas aux tickets/dossiers/chat metier
  // (ces routes le renverraient vers /forbidden) : items reserves SUPERVISEUR/EMPLOYE.
  { label: 'Mes Tickets', to: '/tickets', icon: Ticket, roles: ['SUPERVISEUR', 'EMPLOYE'] },
  // 2026-06-25 — Calendrier des echeances de tickets (par priorite). Scope own-vs-all
  // assure par GET /api/v1/tickets cote serveur (EMPLOYE = ses tickets, SUPERVISEUR = tous).
  { label: 'Calendrier', to: '/calendar', icon: Calendar, roles: ['SUPERVISEUR', 'EMPLOYE'] },
  { label: 'Data Room', to: '/data-rooms', icon: Lock, roles: ['SUPERVISEUR', 'EMPLOYE', 'CLIENT'] },
  // Page Kanban dediee CLIENT : suivi de ses demandes (Non traitee / En cours / Traitee).
  { label: 'Mes demandes', to: '/mes-demandes', icon: MessageSquare, roles: ['CLIENT'] },
  // Lot AG — requetes de son conseiller (employe -> client), pilotees par le client.
  { label: 'Demandes de mon conseiller', to: '/mes-requetes', icon: Inbox, roles: ['CLIENT'] },
  // Lot M (2026-06-30) — Chat + ChatBot IA retires au CLIENT (experience reduite : Dashboard + Data Room).
  { label: 'Chat', to: '/chat', icon: MessageSquare, roles: ['SUPERVISEUR', 'EMPLOYE'] },
  // ChatBot IA reste ouvert au SUPER_ADMIN (admin des sources fiables du RAG, decision 2026-06-26).
  { label: 'ChatBot IA', to: '/chatbot', icon: Bot, roles: ['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE'] },
  // BUG 6 (2026-06-07) — Equipe : SUPERVISEUR uniquement (en attendant Parametres S5).
  { label: 'Equipe', to: '/app/team', icon: Users, roles: ['SUPERVISEUR'] },
  // E2 (2026-06-25) — Tracabilite : SUPERVISEUR
  { label: 'Tracabilite', to: '/tracabilite', icon: History, roles: ['SUPERVISEUR'] },
  // SUPER_ADMIN — gestion plateforme uniquement.
  { label: 'Workspaces', to: '/admin/workspaces', icon: Building2, roles: ['SUPER_ADMIN'] },
  { label: 'Utilisateurs', to: '/admin/users', icon: Users, roles: ['SUPER_ADMIN'] },
  { label: "Journal d'audit", to: '/admin/audit', icon: ScrollText, roles: ['SUPER_ADMIN'] },
];

interface SidebarProps {
  /** Collapsed-by-default option; user can still toggle */
  defaultCollapsed?: boolean;
}

/**
 * Sprint 12.5 T3 — Sidebar repeinte sur tokens marketing.
 * Vertical navy sidebar (maquette variant) — 64px collapsed / 240px expanded.
 * Active item : bandeau or accent (cohérence avec AppTopNav).
 */
export function Sidebar({ defaultCollapsed = false }: SidebarProps) {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  // Badge « Chat » = vrai nombre de messages non lus (remplace l'ancien 3 figé).
  useChatUnreadSync();
  const chatUnread = useChatUnreadStore((s) => s.total);

  if (!user) return null;

  const visible = ITEMS.filter((it) => it.roles.includes(user.role));
  const initials = (user.email[0] ?? 'U').toUpperCase();
  // 2026-06-22 — VRAI code workspace (saisi au login). Fallback derive-UUID si absent.
  const wsCode = user.workspaceCode ?? `JUR-${user.workspaceId.slice(0, 5).toUpperCase()}`;

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside
      className={`flex min-h-screen flex-col bg-bg-raised transition-all duration-200 ${
        collapsed ? 'w-16' : 'w-60'
      }`}
    >
      {/* Logo + workspace */}
      <div className="border-b border-border-hi p-4">
        <Link to="/dashboard" className="flex items-center gap-3">
          <BrandLogo size={36} className="flex-shrink-0" />
          {!collapsed && (
            <div className="overflow-hidden">
              <p className="truncate font-heading text-sm font-bold text-fg">JURIKA</p>
              <p className="truncate font-mono text-[10px] text-fg-subtle">{wsCode}</p>
            </div>
          )}
        </Link>
      </div>

      {/* Collapse toggle */}
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="mx-3 mt-3 flex h-7 items-center justify-center rounded-md text-fg-subtle transition hover:bg-bg-overlay hover:text-fg"
        aria-label={collapsed ? 'Etendre' : 'Reduire'}
      >
        {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
      </button>

      {/* Nav */}
      <nav className="mt-2 flex-1 px-3">
        {visible.map((item) => {
          const Icon = item.icon;
          const badgeCount = item.to === '/chat' ? chatUnread : item.badge ?? 0;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              title={collapsed ? item.label : undefined}
              className={({ isActive }) =>
                `mb-1 flex h-11 items-center gap-3 rounded-md px-3 transition ${
                  isActive
                    ? 'bg-accent/15 text-accent shadow-[inset_2px_0_0_var(--color-accent)]'
                    : 'text-fg-muted hover:bg-bg-overlay hover:text-fg'
                }`
              }
            >
              <Icon className="h-5 w-5 flex-shrink-0" />
              {!collapsed && (
                <span className="flex-1 truncate text-left text-sm">{item.label}</span>
              )}
              {!collapsed && badgeCount > 0 && (
                <span className="min-w-[20px] rounded-full bg-danger px-1.5 py-0.5 text-center text-xs font-semibold text-fg">
                  {badgeCount}
                </span>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Bottom */}
      <div className="border-t border-border-hi p-3">
        {/* Lot M (2026-06-30) — Upgrade masque au CLIENT (pas de facturation) et au
            SUPER_ADMIN (pas de workspace facturable), cohérent avec AppTopNav. */}
        {!collapsed && user.role !== 'CLIENT' && user.role !== 'SUPER_ADMIN' && (
          <button
            type="button"
            className="mb-3 flex h-10 w-full items-center justify-center gap-2 rounded-md bg-accent px-4 text-sm font-semibold text-bg transition hover:bg-accent-hover"
          >
            <Zap className="h-4 w-4" />
            Upgrade
          </button>
        )}

        <div className="flex items-center gap-3 rounded-md p-2 hover:bg-bg-overlay">
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-accent text-sm font-bold text-bg">
            {initials}
          </div>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-fg">{user.email}</p>
              <p className="truncate text-xs text-fg-subtle">{roleLabel(user.role)}</p>
            </div>
          )}
        </div>

        {!collapsed && (
          <div className="mt-2 flex flex-col gap-1">
            {/* BUG 7 (chore 2026-06-08) — entree Profil pointe vers /profile */}
            <button
              type="button"
              onClick={() => navigate('/profile')}
              className="flex items-center gap-2 rounded px-2 py-1.5 text-xs text-fg-muted transition hover:bg-bg-overlay hover:text-fg"
            >
              <Settings className="h-4 w-4" /> Profil
            </button>
            <button
              type="button"
              onClick={handleLogout}
              className="flex items-center gap-2 rounded px-2 py-1.5 text-xs text-danger transition hover:bg-bg-overlay"
            >
              <LogOut className="h-4 w-4" /> Deconnexion
            </button>
          </div>
        )}
      </div>
    </aside>
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

/**
 * Sprint 12.5 T3 — Topbar pair de la Sidebar repeinte sur tokens.
 * Conserve la philosophie "header clair sur sidebar foncée" : en dark theme
 * cette topbar utilise bg-bg-overlay (navy-700 le plus clair), en light theme
 * elle suit le token et redevient vraiment claire.
 */
export function SidebarTopbar({ title = 'Dashboard' }: { title?: string }) {
  const today = new Date();
  const dateStr = today.toLocaleDateString('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return (
    <div className="flex h-16 items-center gap-6 border-b border-border-hi bg-bg-overlay px-6">
      <h1 className="font-heading text-xl font-semibold text-fg">{title}</h1>
      <div className="flex-1" />
      <button
        type="button"
        className="relative rounded-lg p-2 transition hover:bg-bg-raised"
        aria-label="Notifications"
      >
        <Bell className="h-5 w-5 text-fg-subtle" />
        <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-danger text-[10px] font-bold text-fg">
          5
        </span>
      </button>
      <div className="h-6 w-px bg-border-hi" />
      <div className="hidden text-sm capitalize text-fg-subtle md:block">{dateStr}</div>
    </div>
  );
}
