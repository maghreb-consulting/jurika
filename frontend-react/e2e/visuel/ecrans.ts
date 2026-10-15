import type { Page } from '@playwright/test';
import type { CleProfil } from './simulation/session';
import { IDS } from './simulation/donnees';

/**
 * Lot L6b (E4) : ecrans principaux verifies, par ROUTE et par ROLE (jamais par la structure
 * interne des ecrans, que d'autres etapes refont). Une action facultative ouvre une vue
 * secondaire (vue tableau, panneau des notifications) par un role et un nom accessibles.
 */
export interface Ecran {
  /** Nom stable de la capture : <id>-<profil>-<largeur>-<theme>.png */
  id: string;
  groupe: string;
  chemin: string;
  /** null : page publique, sans session. */
  profil: CleProfil | null;
  /** Chemin attendu apres chargement (defaut : chemin sans la requete). */
  cheminAttendu?: string | RegExp;
  /** Ecran prevu par la demande mais pas encore construit : absent => signale "absent", pas en echec. */
  siPresent?: boolean;
  /** L'ecran montre des indicateurs de chargement A DESSEIN (page /design) : seule la stabilite du DOM compte. */
  chargementMontre?: boolean;
  action?: (page: Page) => Promise<void>;
}

async function cliquer(page: Page, nom: RegExp, roles: Array<'button' | 'tab' | 'radio' | 'link'> = ['button', 'tab', 'radio']) {
  for (const role of roles) {
    const cible = page.getByRole(role, { name: nom }).first();
    if (await cible.count()) {
      await cible.click();
      return;
    }
  }
  throw new Error(`action impossible : aucun element (${roles.join(', ')}) nomme ${nom}`);
}

export const ECRANS: Ecran[] = [
  // ---- Vitrine (publique) ----
  { id: 'accueil', groupe: 'vitrine', chemin: '/', profil: null },
  { id: 'cgu', groupe: 'vitrine', chemin: '/cgu', profil: null },
  { id: 'confidentialite', groupe: 'vitrine', chemin: '/confidentialite', profil: null },
  { id: 'mentions-legales', groupe: 'vitrine', chemin: '/mentions-legales', profil: null, siPresent: true },
  { id: 'essai', groupe: 'vitrine', chemin: '/essai', profil: null },
  { id: 'connexion', groupe: 'vitrine', chemin: '/login', profil: null },
  { id: 'inscription', groupe: 'vitrine', chemin: '/signup', profil: null },

  // ---- Tableaux de bord ----
  { id: 'tableau-de-bord', groupe: 'tableaux de bord', chemin: '/dashboard', profil: 'super-admin' },
  { id: 'tableau-de-bord', groupe: 'tableaux de bord', chemin: '/dashboard', profil: 'superviseur' },
  { id: 'tableau-de-bord', groupe: 'tableaux de bord', chemin: '/dashboard', profil: 'employe' },
  { id: 'tableau-de-bord', groupe: 'tableaux de bord', chemin: '/dashboard', profil: 'client' },
  { id: 'tableau-de-bord', groupe: 'tableaux de bord', chemin: '/dashboard', profil: 'superviseur-essai' },

  // ---- Tickets ----
  { id: 'tickets-kanban', groupe: 'tickets', chemin: '/tickets', profil: 'superviseur' },
  { id: 'tickets-kanban', groupe: 'tickets', chemin: '/tickets', profil: 'employe' },
  {
    id: 'tickets-tableau',
    groupe: 'tickets',
    chemin: '/tickets',
    profil: 'employe',
    action: (page) => cliquer(page, /tableau|liste/i),
  },
  { id: 'ticket', groupe: 'tickets', chemin: `/tickets/${IDS.ticketCreation}`, profil: 'employe' },
  { id: 'ticket', groupe: 'tickets', chemin: `/tickets/${IDS.ticketCreation}`, profil: 'superviseur' },

  // ---- Dossiers et Data Rooms ----
  { id: 'data-rooms', groupe: 'dossiers', chemin: '/data-rooms', profil: 'superviseur' },
  { id: 'data-rooms', groupe: 'dossiers', chemin: '/data-rooms', profil: 'employe' },
  {
    id: 'data-room',
    groupe: 'dossiers',
    chemin: `/data-rooms?dossier=${IDS.dossierAtlas}`,
    profil: 'employe',
  },
  { id: 'data-room-client', groupe: 'dossiers', chemin: '/data-rooms', profil: 'client' },
  { id: 'dossiers-a-verifier', groupe: 'dossiers', chemin: '/dossiers-a-verifier', profil: 'superviseur' },
  { id: 'mes-demandes', groupe: 'dossiers', chemin: '/mes-demandes', profil: 'client' },
  { id: 'mes-requetes', groupe: 'dossiers', chemin: '/mes-requetes', profil: 'client' },

  // ---- Parcours ----
  { id: 'parcours-creation', groupe: 'parcours', chemin: `/workflows/${IDS.ticketCreation}`, profil: 'employe' },

  // ---- Calendrier, messagerie, notifications ----
  { id: 'calendrier', groupe: 'calendrier', chemin: '/calendar', profil: 'employe' },
  { id: 'calendrier', groupe: 'calendrier', chemin: '/calendar', profil: 'superviseur' },
  { id: 'messagerie', groupe: 'messagerie', chemin: '/chat', profil: 'employe' },
  {
    id: 'messagerie-conversation',
    groupe: 'messagerie',
    chemin: '/chat',
    profil: 'employe',
    action: (page) => cliquer(page, /Karim Alaoui/, ['button', 'link', 'tab']),
  },
  {
    id: 'notifications-panneau',
    groupe: 'notifications',
    chemin: '/dashboard',
    profil: 'employe',
    action: (page) => cliquer(page, /^notifications/i, ['button']),
  },
  { id: 'notifications-reglages', groupe: 'notifications', chemin: '/settings/notifications', profil: 'employe' },
  { id: 'delais-greffe', groupe: 'repere', chemin: '/delais-greffe', profil: 'employe' },

  // ---- Compte et parametres ----
  { id: 'profil', groupe: 'compte', chemin: '/profile', profil: 'employe' },
  { id: 'parametres-preferences', groupe: 'compte', chemin: '/settings/preferences', profil: 'employe' },
  { id: 'parametres-securite', groupe: 'compte', chemin: '/settings/security', profil: 'employe' },
  { id: 'parametres-cabinet', groupe: 'compte', chemin: '/settings/cabinet', profil: 'superviseur' },
  { id: 'equipe', groupe: 'compte', chemin: '/app/team', profil: 'superviseur' },
  { id: 'tracabilite', groupe: 'compte', chemin: '/tracabilite', profil: 'superviseur' },

  // ---- Facturation et contrat ----
  { id: 'facturation', groupe: 'facturation', chemin: '/app/billing', profil: 'superviseur' },
  { id: 'facturation', groupe: 'facturation', chemin: '/app/billing', profil: 'superviseur-essai' },
  { id: 'changer-forfait', groupe: 'facturation', chemin: '/app/billing/change-plan', profil: 'superviseur' },
  { id: 'contrat', groupe: 'contrat', chemin: '/app/contrat', profil: 'superviseur' },

  // ---- Super-administrateur ----
  { id: 'admin-cabinets', groupe: 'super-admin', chemin: '/admin/workspaces', profil: 'super-admin' },
  { id: 'admin-cabinet', groupe: 'super-admin', chemin: `/admin/workspaces/${IDS.wsDemo}`, profil: 'super-admin' },
  { id: 'admin-abonnements', groupe: 'super-admin', chemin: '/admin/abonnements', profil: 'super-admin' },
  { id: 'admin-paiements', groupe: 'super-admin', chemin: '/admin/paiements', profil: 'super-admin' },
  { id: 'admin-essais', groupe: 'super-admin', chemin: '/admin/essais', profil: 'super-admin' },
  { id: 'admin-contrats', groupe: 'super-admin', chemin: '/admin/contrats', profil: 'super-admin' },
  { id: 'admin-editeur', groupe: 'super-admin', chemin: '/admin/editeur', profil: 'super-admin' },
  { id: 'admin-journal', groupe: 'super-admin', chemin: '/admin/audit', profil: 'super-admin' },
  { id: 'admin-utilisateurs', groupe: 'super-admin', chemin: '/admin/users', profil: 'super-admin' },
  { id: 'admin-referentiel', groupe: 'super-admin', chemin: '/admin/referentiel-demarches', profil: 'super-admin' },
  { id: 'admin-sources-chatbot', groupe: 'super-admin', chemin: '/admin/chatbot-sources', profil: 'super-admin' },
  { id: 'design', groupe: 'super-admin', chemin: '/design', profil: 'super-admin', chargementMontre: true },
];
