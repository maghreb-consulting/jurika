import { Bot, Compass, Eye, EyeOff, HelpCircle, Keyboard, Monitor, Moon, Plus, Settings, Sun, User, type LucideIcon } from 'lucide-react';
import type { Role } from '../../types/auth';
import type { ChoixTheme } from '../../store/themeStore';
import type { FenetreGlobale } from '../../store/commandesStore';
import { menuDuRole } from '../layout/navigation';
import { raccourcisDuRole, CIBLES, type ActionRaccourci } from '../../lib/raccourcis';
import { peutUtiliserChatbot } from '../chatbot/rolesChatbot';

/**
 * Lot L6b (E11) : commandes de la palette (actions et ecrans), construites par role.
 *
 * - Ecrans : exactement les entrees du menu lateral du role (navigation.ts), plus "Mon
 *   compte" et "Parametres" (menu du compte) : la palette n'ouvre aucun ecran que le menu
 *   n'offre pas au role.
 * - Actions : nouveau ticket (cabinet), theme, raccourcis, JURI (aide, masquer, afficher),
 *   chatbot (roles du chatbot), visite guidee.
 * - Le raccourci affiche a cote d'une commande vient de lib/raccourcis (source unique).
 */
export type GroupeCommande = 'actions' | 'ecrans' | 'tickets' | 'dossiers' | 'societes';

/** Titres affiches des groupes, dans l'ordre d'affichage d'une recherche. */
export const LIBELLES_GROUPE: Record<GroupeCommande, string> = {
  tickets: 'Tickets',
  dossiers: 'Dossiers',
  societes: 'Sociétés',
  actions: 'Actions',
  ecrans: 'Écrans',
};

export interface Commande {
  id: string;
  groupe: GroupeCommande;
  libelle: string;
  detail?: string;
  icone: LucideIcon;
  /** Touches affichees (ex. ['g', 't']). */
  raccourci?: string[];
  /** Mots de recherche supplementaires (non affiches). */
  motsCles?: string;
  executer: () => void;
}

export interface ContexteCommandes {
  role: Role;
  juriMasque: boolean;
  naviguer: (to: string) => void;
  ouvrir: (f: FenetreGlobale) => void;
  choisirTheme: (c: ChoixTheme) => void;
  definirJuriMasque: (masque: boolean) => void;
  ouvrirChatbot: () => void;
  revoirVisite: () => void;
}

/** Minuscules sans accents ni espaces superflus (comparaison de recherche). */
export function normaliser(s: string | null | undefined): string {
  return (s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/** Vrai si chaque mot de la recherche figure dans le texte. */
export function correspond(texte: string, recherche: string): boolean {
  const t = normaliser(texte);
  const mots = normaliser(recherche).split(' ').filter(Boolean);
  return mots.every((m) => t.includes(m));
}

function touches(role: Role, action: ActionRaccourci): string[] | undefined {
  return raccourcisDuRole(role).find((r) => r.action === action)?.touches;
}

const ACTION_DE_CIBLE: Record<string, ActionRaccourci> = Object.fromEntries(
  Object.entries(CIBLES).map(([action, to]) => [to as string, action as ActionRaccourci]),
);

export function commandesDuRole(ctx: ContexteCommandes): Commande[] {
  const { role } = ctx;
  const cabinet = role === 'EMPLOYE' || role === 'SUPERVISEUR';
  const actions: Commande[] = [];

  if (cabinet) {
    actions.push({
      id: 'nouveau-ticket',
      groupe: 'actions',
      libelle: 'Nouveau ticket',
      detail: 'Ouvrir une demande du client et son parcours',
      icone: Plus,
      raccourci: touches(role, 'nouveau-ticket'),
      motsCles: 'creer ajouter dossier demande',
      executer: () => ctx.ouvrir('nouveauTicket'),
    });
  }
  if (peutUtiliserChatbot(role)) {
    actions.push({
      id: 'chatbot',
      groupe: 'actions',
      libelle: 'Poser une question au chatbot juridique',
      detail: 'Réponses tirées des sources déposées par JURIKA, toujours citées',
      icone: Bot,
      motsCles: 'assistant question droit',
      executer: ctx.ouvrirChatbot,
    });
  }
  actions.push({
    id: 'raccourcis',
    groupe: 'actions',
    libelle: 'Afficher les raccourcis clavier',
    icone: Keyboard,
    raccourci: touches(role, 'raccourcis'),
    motsCles: 'clavier touches aide',
    executer: () => ctx.ouvrir('raccourcis'),
  });
  if (ctx.juriMasque) {
    actions.push({
      id: 'juri-afficher',
      groupe: 'actions',
      libelle: 'Afficher JURI',
      detail: 'L’assistant revient sur le bord droit de l’écran',
      icone: Eye,
      motsCles: 'assistant aide robot reafficher',
      executer: () => ctx.definirJuriMasque(false),
    });
  } else {
    actions.push({
      id: 'juri-aide',
      groupe: 'actions',
      libelle: 'Ouvrir l’aide de cet écran (JURI)',
      icone: HelpCircle,
      motsCles: 'assistant aide robot',
      executer: () => ctx.ouvrir('juri'),
    });
    actions.push({
      id: 'juri-masquer',
      groupe: 'actions',
      libelle: 'Masquer JURI',
      detail: 'Le menu du compte le réaffiche',
      icone: EyeOff,
      motsCles: 'assistant robot cacher',
      executer: () => ctx.definirJuriMasque(true),
    });
  }
  actions.push({
    id: 'visite',
    groupe: 'actions',
    libelle: 'Revoir la visite guidée',
    icone: Compass,
    motsCles: 'aide decouverte',
    executer: ctx.revoirVisite,
  });
  const themes: { choix: ChoixTheme; libelle: string; icone: LucideIcon }[] = [
    { choix: 'CLAIR', libelle: 'Thème clair', icone: Sun },
    { choix: 'SOMBRE', libelle: 'Thème sombre', icone: Moon },
    { choix: 'SYSTEME', libelle: 'Thème selon l’ordinateur', icone: Monitor },
  ];
  for (const t of themes) {
    actions.push({
      id: `theme-${t.choix}`,
      groupe: 'actions',
      libelle: t.libelle,
      detail: 'Choix enregistré sur votre compte',
      icone: t.icone,
      motsCles: 'theme couleur affichage apparence',
      executer: () => ctx.choisirTheme(t.choix),
    });
  }

  const ecrans: Commande[] = [];
  for (const g of menuDuRole(role)) {
    for (const e of g.entrees) {
      const action = ACTION_DE_CIBLE[e.to];
      ecrans.push({
        id: `ecran-${e.to}`,
        groupe: 'ecrans',
        libelle: e.libelle,
        detail: g.titre,
        icone: e.icone,
        raccourci: action ? touches(role, action) : undefined,
        motsCles: 'aller ouvrir ecran page',
        executer: () => ctx.naviguer(e.to),
      });
    }
  }
  ecrans.push(
    { id: 'ecran-/profile', groupe: 'ecrans', libelle: 'Mon compte', icone: User, motsCles: 'profil aller', executer: () => ctx.naviguer('/profile') },
    { id: 'ecran-/settings', groupe: 'ecrans', libelle: 'Paramètres', icone: Settings, motsCles: 'reglages aller', executer: () => ctx.naviguer('/settings') },
  );

  return [...actions, ...ecrans];
}

/** Commandes qui correspondent a la recherche (toutes si elle est vide). */
export function filtrerCommandes(commandes: Commande[], recherche: string): Commande[] {
  if (!normaliser(recherche)) return commandes;
  return commandes.filter((c) => correspond(`${c.libelle} ${c.detail ?? ''} ${c.motsCles ?? ''}`, recherche));
}
