import { create } from 'zustand';
import { api } from '../lib/api';

/**
 * Lot L6 (E6, CDC 15.4) : "Une aide deja vue peut etre masquee et reaffichee."
 *
 * Les aides masquees (encadres `TexteAide`, visite guidee "visite-<role>") sont
 * memorisees cote serveur, par utilisateur (auth V39, `/auth/me/aides`). Ce
 * magasin est HYDRATE UNE FOIS par session (`charger`, appele par la mise en page
 * a la connexion) : le rendu d'une aide ne fait jamais d'appel reseau, il lit le
 * magasin. Sans serveur (tests, page publique, erreur reseau), rien n'est masque
 * et tout s'affiche ; un masquage vaut alors pour la session d'ecran.
 *
 * Reprise unique : les aides masquees dans le navigateur avant le lot L6
 * (cles `jurika.aide.<cle>.masquee`) sont envoyees au serveur, puis effacees.
 */

const PREFIXE_LOCAL = 'jurika.aide.';
const SUFFIXE_LOCAL = '.masquee';

interface AidesState {
  /** Cles des aides masquees. */
  masquees: ReadonlySet<string>;
  /** Utilisateur dont les aides sont chargees (null : aucune session). */
  utilisateur: string | null;
  /** Vrai quand la liste du serveur a ete lue pour `utilisateur`. */
  chargee: boolean;

  charger: (userId: string) => Promise<void>;
  vider: () => void;
  masquer: (cle: string) => void;
  reafficher: (cle: string) => void;
  reafficherTout: () => Promise<void>;
}

/** Cles masquees dans le navigateur avant le lot L6 (stockage indisponible : aucune). */
function clesLocales(): string[] {
  const cles: string[] = [];
  try {
    for (let i = 0; i < window.localStorage.length; i++) {
      const k = window.localStorage.key(i);
      if (k && k.startsWith(PREFIXE_LOCAL) && k.endsWith(SUFFIXE_LOCAL) && window.localStorage.getItem(k) === '1') {
        cles.push(k.slice(PREFIXE_LOCAL.length, k.length - SUFFIXE_LOCAL.length));
      }
    }
  } catch {
    return [];
  }
  return cles;
}

function effacerLocale(cle: string): void {
  try {
    window.localStorage.removeItem(`${PREFIXE_LOCAL}${cle}${SUFFIXE_LOCAL}`);
  } catch {
    // Stockage indisponible : rien a effacer.
  }
}

function chemin(cle: string): string {
  return `/auth/me/aides/${encodeURIComponent(cle)}`;
}

export const useAidesStore = create<AidesState>()((set, get) => ({
  masquees: new Set<string>(),
  utilisateur: null,
  chargee: false,

  charger: async (userId) => {
    if (get().utilisateur === userId && get().chargee) return;
    set({ utilisateur: userId, chargee: false, masquees: new Set<string>() });
    let serveur: string[];
    try {
      const { data } = await api.get<{ cles: string[] }>('/auth/me/aides');
      serveur = Array.isArray(data?.cles) ? data.cles : [];
    } catch {
      // Serveur injoignable : rien n'est masque, tout s'affiche (la visite attend).
      return;
    }
    if (get().utilisateur !== userId) return;
    const masquees = new Set<string>(serveur);
    for (const cle of clesLocales()) {
      if (!masquees.has(cle)) {
        try {
          await api.put(chemin(cle));
          masquees.add(cle);
        } catch {
          // Cle refusee par le serveur (format) ou erreur : elle n'est pas reprise.
        }
      }
      effacerLocale(cle);
    }
    if (get().utilisateur !== userId) return;
    set({ masquees, chargee: true });
  },

  vider: () => set({ masquees: new Set<string>(), utilisateur: null, chargee: false }),

  masquer: (cle) => {
    const masquees = new Set(get().masquees);
    masquees.add(cle);
    set({ masquees });
    if (get().utilisateur) {
      api.put(chemin(cle)).catch(() => {
        // Echec reseau : l'aide reste masquee pour la session d'ecran.
      });
    }
  },

  reafficher: (cle) => {
    const masquees = new Set(get().masquees);
    masquees.delete(cle);
    set({ masquees });
    if (get().utilisateur) {
      api.delete(chemin(cle)).catch(() => {
        // Echec reseau : l'aide est reaffichee pour la session d'ecran.
      });
    }
  },

  reafficherTout: async () => {
    set({ masquees: new Set<string>() });
    if (get().utilisateur) {
      await api.delete('/auth/me/aides');
    }
  },
}));

/** Vrai si l'aide `cle` est masquee (lecture du magasin, sans appel reseau). */
export function useAideMasquee(cle: string): boolean {
  return useAidesStore((s) => s.masquees.has(cle));
}
