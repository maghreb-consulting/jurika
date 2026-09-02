import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

/**
 * Sprint 12.5/12.6 — Theme store.
 *
 * Source de verite unique pour le mode light/dark :
 * - Default = 'light' (Strategie A confirmee user 2026-06-01, clone marketing-site).
 * - Persiste en localStorage (cle `jurika-theme`) pour reload instantane.
 * - Sync DB cote serveur via POST /api/v1/users/me/theme (workspace.preferred_theme V22).
 * - applyDomTheme() pose l'attribut [data-theme] sur <html> -> CSS variables override.
 *
 * Inversion par rapport au T2 : 'light' est maintenant la baseline (pas d'attribut
 * data-theme), 'dark' pose data-theme="dark". V22 migration adapte par Cowork
 * (DEFAULT 'dark' -> DEFAULT 'light').
 */
export type ThemeMode = 'dark' | 'light';

interface ThemeState {
  mode: ThemeMode;
  /** True quand on est en cours de sync DB (évite double-clic). */
  isSyncing: boolean;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
  /** Applique l'attribut data-theme sur <html>. À appeler depuis App au mount. */
  applyDomTheme: () => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      mode: 'light',
      isSyncing: false,

      setMode: (mode) => {
        set({ mode });
        applyToDom(mode);
      },

      toggle: () => {
        const next: ThemeMode = get().mode === 'dark' ? 'light' : 'dark';
        set({ mode: next });
        applyToDom(next);
      },

      applyDomTheme: () => {
        applyToDom(get().mode);
      },
    }),
    {
      name: 'jurika-theme',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ mode: state.mode }),
    },
  ),
);

function applyToDom(mode: ThemeMode): void {
  if (typeof document === 'undefined') return;
  const html = document.documentElement;
  if (mode === 'dark') {
    html.setAttribute('data-theme', 'dark');
  } else {
    // Mode light = absence d'attribut (CSS @theme tokens sont desormais light).
    html.removeAttribute('data-theme');
  }
}
