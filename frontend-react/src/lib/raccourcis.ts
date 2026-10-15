import type { Role } from '../types/auth';

/**
 * Lot L6b (E11) : raccourcis clavier principaux de l'application.
 *
 * Source unique : la liste ci-dessous alimente a la fois le lecteur de touches (coquille) et
 * la fenetre "Raccourcis clavier" (touche ?), qui ne peut donc pas annoncer un raccourci
 * qui n'existe pas (ni l'inverse).
 *
 * Regles :
 * - aucun raccourci n'est actif pendant la saisie dans un champ (input, textarea, select,
 *   zone editable, liste a saisie) : la frappe reste a l'utilisateur ;
 * - aucun raccourci n'est actif quand une fenetre modale est ouverte (elle a ses propres
 *   touches ; Echap la ferme) ;
 * - une touche avec Alt, ou une lettre avec Ctrl / Cmd, n'est jamais interpretee (raccourcis
 *   du navigateur et du systeme) ; seule exception : Ctrl+K / Cmd+K ;
 * - "g puis t" : la seconde touche doit suivre la premiere de moins de 1,5 s ;
 * - un raccourci n'est offert qu'aux roles qui ont acces a l'ecran vise.
 */

export type ActionRaccourci =
  | 'palette'
  | 'raccourcis'
  | 'nouveau-ticket'
  | 'aller-accueil'
  | 'aller-tickets'
  | 'aller-dossiers'
  | 'aller-calendrier';

export interface Raccourci {
  action: ActionRaccourci;
  /** Touches affichees, dans l'ordre (sequence ou combinaison selon `combinaison`). */
  touches: string[];
  /** Vrai : touches enfoncees ensemble (Ctrl + K) ; faux : l'une apres l'autre (g puis t). */
  combinaison?: boolean;
  description: string;
  /** Roles concernes (absent : tous). */
  roles?: Role[];
}

const CABINET: Role[] = ['EMPLOYE', 'SUPERVISEUR'];
const AVEC_DOSSIERS: Role[] = ['EMPLOYE', 'SUPERVISEUR', 'CLIENT'];

export const RACCOURCIS: Raccourci[] = [
  {
    action: 'palette',
    touches: ['Ctrl', 'K'],
    combinaison: true,
    description: 'Ouvrir la palette de commandes : rechercher un dossier, un ticket, une société ou lancer une action (Cmd + K sur Mac).',
  },
  { action: 'raccourcis', touches: ['?'], description: 'Afficher la liste des raccourcis clavier.' },
  { action: 'nouveau-ticket', touches: ['n'], description: 'Créer un nouveau ticket.', roles: CABINET },
  { action: 'aller-accueil', touches: ['g', 'a'], description: 'Aller au tableau de bord (accueil).' },
  { action: 'aller-tickets', touches: ['g', 't'], description: 'Aller aux tickets.', roles: CABINET },
  { action: 'aller-dossiers', touches: ['g', 'd'], description: 'Aller aux dossiers (Data Rooms).', roles: AVEC_DOSSIERS },
  { action: 'aller-calendrier', touches: ['g', 'c'], description: 'Aller au calendrier des échéances.', roles: CABINET },
];

/** Touche Echap : affichee dans la liste, geree par chaque fenetre (pas par le lecteur). */
export const RACCOURCI_ECHAP = {
  touches: ['Échap'],
  description: 'Fermer la fenêtre, le panneau, le menu ou la palette ouverts ; le focus revient là où il était.',
};

/** Adresse des actions de navigation. */
export const CIBLES: Partial<Record<ActionRaccourci, string>> = {
  'aller-accueil': '/dashboard',
  'aller-tickets': '/tickets',
  'aller-dossiers': '/data-rooms',
  'aller-calendrier': '/calendar',
};

export function raccourcisDuRole(role: Role | undefined): Raccourci[] {
  if (!role) return [];
  return RACCOURCIS.filter((r) => !r.roles || r.roles.includes(role));
}

/** Raccourci d'une action pour un role (pour l'afficher a cote d'une commande). */
export function raccourciDe(action: ActionRaccourci, role: Role | undefined): Raccourci | undefined {
  return raccourcisDuRole(role).find((r) => r.action === action);
}

/** Vrai si l'element recoit de la saisie : aucun raccourci ne doit alors s'appliquer. */
export function estChampDeSaisie(el: EventTarget | null): boolean {
  if (!el || typeof (el as Element).closest !== 'function') return false;
  const e = el as HTMLElement;
  if (e.isContentEditable) return true;
  const tag = e.tagName;
  if (tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (tag === 'INPUT') {
    const type = ((e as HTMLInputElement).type || 'text').toLowerCase();
    return !['button', 'checkbox', 'radio', 'submit', 'reset', 'file', 'image', 'range', 'color'].includes(type);
  }
  return e.closest('[contenteditable=""], [contenteditable="true"], [role="textbox"], [role="combobox"], [role="searchbox"]') !== null;
}

/** Vrai si une fenetre modale est ouverte (dialog natif modal ou aria-modal). */
export function fenetreModaleOuverte(doc: Document | null = typeof document === 'undefined' ? null : document): boolean {
  if (!doc) return false;
  return doc.querySelector('dialog[open], [aria-modal="true"]') !== null;
}

export interface ToucheLue {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
  repeat?: boolean;
  isComposing?: boolean;
  defaultPrevented?: boolean;
  target: EventTarget | null;
}

export const DELAI_SEQUENCE_MS = 1500;

/**
 * Lecteur de touches : renvoie une fonction qui recoit chaque touche et rend l'action
 * reconnue (ou null). Il garde la premiere touche d'une sequence ("g") au plus
 * DELAI_SEQUENCE_MS. `maintenant` et `modaleOuverte` sont injectables (tests).
 */
export function creerLecteurRaccourcis(options: {
  role: () => Role | undefined;
  maintenant?: () => number;
  modaleOuverte?: () => boolean;
}): (t: ToucheLue) => ActionRaccourci | null {
  const maintenant = options.maintenant ?? (() => Date.now());
  const modaleOuverte = options.modaleOuverte ?? (() => fenetreModaleOuverte());
  let attente: { touche: string; depuis: number } | null = null;

  return (t) => {
    if (t.defaultPrevented || t.isComposing || t.repeat) return null;
    if (estChampDeSaisie(t.target)) {
      attente = null;
      return null;
    }
    if (modaleOuverte()) {
      attente = null;
      return null;
    }
    const role = options.role();
    const offerts = raccourcisDuRole(role);
    const touche = t.key.length === 1 ? t.key.toLowerCase() : t.key;

    // Combinaison Ctrl+K / Cmd+K (seule combinaison avec modificateur).
    if ((t.ctrlKey || t.metaKey) && !t.altKey) {
      attente = null;
      if (touche === 'k' && offerts.some((r) => r.action === 'palette')) return 'palette';
      return null;
    }
    if (t.altKey || t.ctrlKey || t.metaKey) {
      attente = null;
      return null;
    }

    const precedente = attente && maintenant() - attente.depuis <= DELAI_SEQUENCE_MS ? attente.touche : null;
    attente = null;

    if (precedente) {
      const trouve = offerts.find(
        (r) => !r.combinaison && r.touches.length === 2 && r.touches[0] === precedente && r.touches[1] === touche,
      );
      if (trouve) return trouve.action;
      // Seconde touche inconnue : la sequence est abandonnee, la touche est relue seule.
    }

    const simple = offerts.find((r) => !r.combinaison && r.touches.length === 1 && r.touches[0] === t.key);
    if (simple) return simple.action;

    const debutSequence = offerts.some((r) => !r.combinaison && r.touches.length === 2 && r.touches[0] === touche);
    if (debutSequence) attente = { touche, depuis: maintenant() };
    return null;
  };
}

/** Vrai sur un poste Apple (pour afficher Cmd au lieu de Ctrl). */
export function posteApple(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent || '');
}
