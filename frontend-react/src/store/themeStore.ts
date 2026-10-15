import { create } from 'zustand';
import { ecouterRequete, REQUETE_SOMBRE, systemeSombre } from '../lib/mouvement';

/**
 * Lot L6b (E3) : theme de l'interface.
 *
 * - Le CHOIX vaut `SYSTEME` (suit l'ordinateur, par defaut), `CLAIR` ou `SOMBRE`.
 * - Le MODE est le theme effectivement applique (`light` ou `dark`) : attribut
 *   `data-theme` de <html>, lu par les jetons de index.css.
 * - Le choix est memorise SUR LE COMPTE par store/preferencesStore (GET/PUT
 *   /users/me/preferences) ; ce magasin-ci ne fait aucun appel reseau.
 * - localStorage (`jurika.theme`) n'est qu'un cache : il permet au script de index.html
 *   de poser le bon theme avant le premier rendu (pas d'eclair).
 *
 * Remplace le magasin du sprint 12.5 (clair/sombre en localStorage et POST
 * /users/me/theme jamais relu : motif 9, backlog du lot L6).
 */
export type ChoixTheme = 'SYSTEME' | 'CLAIR' | 'SOMBRE';
export type ThemeMode = 'dark' | 'light';

/** Libelles affiches des choix. */
export const LIBELLES_THEME: Record<ChoixTheme, string> = {
  SYSTEME: 'Selon l’ordinateur',
  CLAIR: 'Clair',
  SOMBRE: 'Sombre',
};

export const CLE_CACHE_THEME = 'jurika.theme';
/** Cle de l'ancien magasin (zustand persist, { state: { mode } }) : reprise unique. */
const CLE_ANCIENNE = 'jurika-theme';

export function estChoixTheme(v: unknown): v is ChoixTheme {
  return v === 'SYSTEME' || v === 'CLAIR' || v === 'SOMBRE';
}

function lireCache(): ChoixTheme {
  try {
    const v = window.localStorage.getItem(CLE_CACHE_THEME);
    if (estChoixTheme(v)) return v;
    const ancien = window.localStorage.getItem(CLE_ANCIENNE);
    if (ancien) {
      const mode = (JSON.parse(ancien) as { state?: { mode?: string } })?.state?.mode;
      if (mode === 'dark') return 'SOMBRE';
      if (mode === 'light') return 'CLAIR';
    }
  } catch {
    // Stockage indisponible ou valeur illisible : le reglage de l'ordinateur s'applique.
  }
  return 'SYSTEME';
}

function ecrireCache(choix: ChoixTheme): void {
  try {
    window.localStorage.setItem(CLE_CACHE_THEME, choix);
    window.localStorage.removeItem(CLE_ANCIENNE);
  } catch {
    // Stockage indisponible : le theme vaut pour cet ecran ; le compte le garde.
  }
}

/** Theme effectif d'un choix, selon le reglage de l'ordinateur. */
export function modeDuChoix(choix: ChoixTheme): ThemeMode {
  if (choix === 'SOMBRE') return 'dark';
  if (choix === 'CLAIR') return 'light';
  return systemeSombre() ? 'dark' : 'light';
}

function appliquerAuDocument(mode: ThemeMode): void {
  if (typeof document === 'undefined') return;
  document.documentElement.setAttribute('data-theme', mode);
}

interface ThemeState {
  choix: ChoixTheme;
  mode: ThemeMode;
  /** Applique un choix (cache local compris), sans appel reseau. */
  appliquerChoix: (choix: ChoixTheme) => void;
  /** Recalcule le mode quand le reglage de l'ordinateur change (choix SYSTEME). */
  suivreSysteme: () => () => void;
  /** Pose l'attribut data-theme sur <html> (montage de l'application). */
  applyDomTheme: () => void;
}

const choixInitial = typeof window === 'undefined' ? 'SYSTEME' : lireCache();

export const useThemeStore = create<ThemeState>()((set, get) => ({
  choix: choixInitial,
  mode: modeDuChoix(choixInitial),

  appliquerChoix: (choix) => {
    const mode = modeDuChoix(choix);
    set({ choix, mode });
    appliquerAuDocument(mode);
    ecrireCache(choix);
  },

  suivreSysteme: () =>
    ecouterRequete(REQUETE_SOMBRE, () => {
      if (get().choix !== 'SYSTEME') return;
      const mode = modeDuChoix('SYSTEME');
      set({ mode });
      appliquerAuDocument(mode);
    }),

  applyDomTheme: () => {
    const mode = modeDuChoix(get().choix);
    set({ mode });
    appliquerAuDocument(mode);
  },
}));
