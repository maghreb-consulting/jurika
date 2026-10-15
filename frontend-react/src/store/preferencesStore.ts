import { create } from 'zustand';
import { api } from '../lib/api';
import { estChoixTheme, useThemeStore, type ChoixTheme } from './themeStore';

/**
 * Lot L6b (E3) : preferences d'affichage memorisees SUR LE COMPTE.
 *
 * Contrat d'API (auth-service, lot L6b) :
 *   GET /api/v1/users/me/preferences -> { theme: 'SYSTEME'|'CLAIR'|'SOMBRE', juriMasque: boolean }
 *   PUT /api/v1/users/me/preferences <- PARTIEL ({ theme } ou { juriMasque }) ; 400 si une cle
 *   est inconnue ou une valeur invalide.
 *   403 pendant le changement de mot de passe force et la configuration 2FA forcee : le front
 *   applique alors SYSTEME et JURI visible, sans message d'erreur (etat attendu, pas une panne).
 *
 * - `charger` : lu UNE fois par session (mise en page commune, a la connexion) ; le theme
 *   lu est applique.
 * - `choisirTheme` / `definirJuriMasque` : applique tout de suite, puis envoie la SEULE cle
 *   modifiee (aucune autre valeur n'est supposee ni ecrasee).
 * - Erreurs VISIBLES (`erreur`, affichee par la coquille) : jamais de repli silencieux.
 */
export interface Preferences {
  theme: ChoixTheme;
  juriMasque: boolean;
}

export const CHEMIN_PREFERENCES = '/users/me/preferences';

export const MESSAGE_LECTURE =
  'Vos préférences d’affichage n’ont pas pu être lues sur votre compte. Le thème de cet ordinateur est appliqué en attendant.';
export const MESSAGE_ENREGISTREMENT =
  'Votre choix est appliqué sur cet écran, mais il n’a pas pu être enregistré sur votre compte.';

type Statut = 'inactif' | 'chargement' | 'pret' | 'erreur';

interface PreferencesState {
  preferences: Preferences | null;
  utilisateur: string | null;
  statut: Statut;
  erreur: string | null;
  /** Derniere modification refusee (pour "Reessayer"). */
  enAttente: Partial<Preferences> | null;

  charger: (userId: string) => Promise<void>;
  choisirTheme: (choix: ChoixTheme) => Promise<boolean>;
  definirJuriMasque: (masque: boolean) => Promise<boolean>;
  reessayer: () => Promise<boolean>;
  /** Masque le message (la modification refusee reste en attente pour "Reessayer"). */
  effacerErreur: () => void;
  vider: () => void;
}

function lirePreferences(data: unknown): Preferences | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  if (!estChoixTheme(d.theme) || typeof d.juriMasque !== 'boolean') return null;
  return { theme: d.theme, juriMasque: d.juriMasque };
}

/** Valeurs appliquees quand le compte ne peut pas encore avoir de preferences (403). */
export const PREFERENCES_PAR_DEFAUT: Preferences = { theme: 'SYSTEME', juriMasque: false };

/** 403 : changement de mot de passe ou 2FA forces en cours ; route fermee, etat attendu. */
function estRefusAttendu(e: unknown): boolean {
  return (e as { response?: { status?: number } } | null)?.response?.status === 403;
}

async function lireServeur(): Promise<Preferences | null> {
  const { data } = await api.get<unknown>(CHEMIN_PREFERENCES);
  return lirePreferences(data);
}

export const usePreferencesStore = create<PreferencesState>()((set, get) => {
  async function enregistrer(modif: Partial<Preferences>): Promise<boolean> {
    if (!get().utilisateur) return true; // Hors session (pages publiques) : choix local seulement.
    const base = get().preferences;
    if (base) set({ preferences: { ...base, ...modif } });
    try {
      await api.put(CHEMIN_PREFERENCES, modif);
      set({ statut: 'pret', erreur: null, enAttente: null });
      return true;
    } catch (e) {
      if (estRefusAttendu(e)) {
        set({ enAttente: null });
        return false;
      }
      set({ statut: 'erreur', erreur: MESSAGE_ENREGISTREMENT, enAttente: modif });
      return false;
    }
  }

  return {
    preferences: null,
    utilisateur: null,
    statut: 'inactif',
    erreur: null,
    enAttente: null,

    charger: async (userId) => {
      const deja = get().utilisateur === userId && (get().statut === 'pret' || get().statut === 'chargement');
      if (deja) return;
      set({ utilisateur: userId, statut: 'chargement', erreur: null });
      let lues: Preferences | null;
      try {
        lues = await lireServeur();
      } catch (e) {
        if (estRefusAttendu(e)) {
          if (get().utilisateur !== userId) return;
          set({ preferences: { ...PREFERENCES_PAR_DEFAUT }, statut: 'pret', erreur: null });
          useThemeStore.getState().appliquerChoix(PREFERENCES_PAR_DEFAUT.theme);
          return;
        }
        lues = null;
      }
      if (get().utilisateur !== userId) return;
      if (!lues) {
        set({ statut: 'erreur', erreur: MESSAGE_LECTURE });
        return;
      }
      set({ preferences: lues, statut: 'pret', erreur: null });
      useThemeStore.getState().appliquerChoix(lues.theme);
    },

    choisirTheme: (choix) => {
      useThemeStore.getState().appliquerChoix(choix);
      return enregistrer({ theme: choix });
    },

    definirJuriMasque: (masque) => {
      const p = get().preferences;
      if (p) set({ preferences: { ...p, juriMasque: masque } });
      return enregistrer({ juriMasque: masque });
    },

    reessayer: async () => {
      const modif = get().enAttente;
      if (modif) return enregistrer(modif);
      const userId = get().utilisateur;
      if (userId) {
        set({ statut: 'inactif' });
        await get().charger(userId);
        return get().statut === 'pret';
      }
      return false;
    },

    effacerErreur: () => set({ erreur: null }),

    vider: () => set({ preferences: null, utilisateur: null, statut: 'inactif', erreur: null, enAttente: null }),
  };
});
