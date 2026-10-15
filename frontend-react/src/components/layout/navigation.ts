import {
  Banknote,
  Bot,
  Building,
  Building2,
  CalendarDays,
  FileSignature,
  History,
  Home,
  Hourglass,
  Inbox,
  ListChecks,
  Lock,
  MessageSquare,
  Palette,
  Receipt,
  ScrollText,
  Ticket,
  UserCheck,
  Users,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';
import type { Role } from '../../types/auth';
import type { ElementFil } from '../ui/FilAriane';

/**
 * Lot L6b (E3) : menu lateral par role (source unique : menu, fil d'Ariane par defaut,
 * prechargement des ecrans).
 *
 * Regles reprises de l'ancien en-tete (docs/v2/Plan_Refonte_Roles.md) :
 * - RG-U07 : le super-admin n'accede ni aux tickets, ni aux dossiers, ni aux documents ;
 * - RG-U08 : l'employe ne voit que ses tickets et ses Data Rooms ;
 * - RG-U09 / lot M : le client a son espace, ses demandes et les requetes du cabinet ;
 *   ni messagerie, ni facturation ;
 * - lot L5 E8 : le chatbot s'ouvre par la bulle ; le super-admin gere ses sources.
 *
 * Ordre voulu (demande du lot L6b) : l'employe atteint ses tickets et ses dossiers en un
 * clic ; le superviseur arrive sur son tableau de bord ; le client a un espace simple.
 */
export interface EntreeMenu {
  libelle: string;
  to: string;
  icone: LucideIcon;
  /** Compteur affiche (messages non lus). */
  compteur?: 'chat';
}

export interface GroupeMenu {
  titre: string;
  entrees: EntreeMenu[];
}

const TABLEAU_DE_BORD: EntreeMenu = { libelle: 'Tableau de bord', to: '/dashboard', icone: Home };
const MESSAGERIE: EntreeMenu = { libelle: 'Messagerie', to: '/chat', icone: MessageSquare, compteur: 'chat' };
const DELAIS: EntreeMenu = { libelle: 'Délais de greffe', to: '/delais-greffe', icone: ListChecks };
const CALENDRIER: EntreeMenu = { libelle: 'Calendrier', to: '/calendar', icone: CalendarDays };

const MENUS: Record<Role, GroupeMenu[]> = {
  EMPLOYE: [
    {
      titre: 'Mon travail',
      entrees: [
        TABLEAU_DE_BORD,
        { libelle: 'Mes tickets', to: '/tickets', icone: Ticket },
        { libelle: 'Mes Data Rooms', to: '/data-rooms', icone: Lock },
        CALENDRIER,
      ],
    },
    { titre: 'Échanges et repères', entrees: [MESSAGERIE, DELAIS] },
  ],
  SUPERVISEUR: [
    {
      titre: 'Pilotage',
      entrees: [
        TABLEAU_DE_BORD,
        { libelle: 'Tous les tickets', to: '/tickets', icone: Ticket },
        { libelle: 'Dossiers à vérifier', to: '/dossiers-a-verifier', icone: UserCheck },
        CALENDRIER,
      ],
    },
    {
      titre: 'Dossiers',
      entrees: [{ libelle: 'Toutes les Data Rooms', to: '/data-rooms', icone: Lock }],
    },
    {
      titre: 'Cabinet',
      entrees: [
        { libelle: 'Équipe', to: '/app/team', icone: UsersRound },
        { libelle: 'Traçabilité', to: '/tracabilite', icone: History },
        MESSAGERIE,
        DELAIS,
      ],
    },
  ],
  CLIENT: [
    {
      titre: 'Espace client',
      entrees: [
        { libelle: 'Accueil', to: '/dashboard', icone: Home },
        { libelle: 'Mon espace', to: '/data-rooms', icone: Lock },
        { libelle: 'Mes demandes', to: '/mes-demandes', icone: MessageSquare },
        { libelle: 'Requêtes du cabinet', to: '/mes-requetes', icone: Inbox },
      ],
    },
  ],
  SUPER_ADMIN: [
    {
      titre: 'Plateforme',
      entrees: [
        TABLEAU_DE_BORD,
        { libelle: 'Cabinets', to: '/admin/workspaces', icone: Building2 },
        { libelle: 'Abonnements', to: '/admin/abonnements', icone: Receipt },
        { libelle: 'Paiements', to: '/admin/paiements', icone: Banknote },
        { libelle: 'Essais gratuits', to: '/admin/essais', icone: Hourglass },
        { libelle: 'Contrats', to: '/admin/contrats', icone: FileSignature },
      ],
    },
    {
      titre: 'Administration',
      entrees: [
        { libelle: 'Utilisateurs', to: '/admin/users', icone: Users },
        { libelle: 'Journal d’audit', to: '/admin/audit', icone: ScrollText },
        { libelle: 'Référentiel des démarches', to: '/admin/referentiel-demarches', icone: ListChecks },
        { libelle: 'Sources du chatbot', to: '/admin/chatbot-sources', icone: Bot },
        { libelle: 'Éditeur', to: '/admin/editeur', icone: Building },
        { libelle: 'Système de design', to: '/design', icone: Palette },
      ],
    },
  ],
};

export function menuDuRole(role: Role): GroupeMenu[] {
  return MENUS[role] ?? [];
}

/** Entree du menu qui correspond a une adresse (prefixe le plus long). */
export function entreeActive(role: Role, pathname: string): EntreeMenu | undefined {
  let meilleure: EntreeMenu | undefined;
  for (const g of menuDuRole(role)) {
    for (const e of g.entrees) {
      const ok = pathname === e.to || pathname.startsWith(`${e.to}/`);
      if (ok && (!meilleure || e.to.length > meilleure.to.length)) meilleure = e;
    }
  }
  return meilleure;
}

/**
 * Fil d'Ariane par defaut d'une page : Accueil > entree du menu > titre de la page
 * (le titre n'est ajoute que s'il differe de l'entree).
 */
export function filParDefaut(role: Role, pathname: string, titre: string): ElementFil[] {
  const accueil: ElementFil = { libelle: 'Accueil', to: '/dashboard' };
  if (pathname === '/dashboard') return [{ libelle: 'Accueil' }];
  const e = entreeActive(role, pathname);
  const fil: ElementFil[] = [accueil];
  if (e && e.to !== '/dashboard') {
    if (e.libelle === titre && pathname === e.to) return [...fil, { libelle: e.libelle }];
    fil.push({ libelle: e.libelle, to: e.to });
  }
  fil.push({ libelle: titre });
  return fil;
}
