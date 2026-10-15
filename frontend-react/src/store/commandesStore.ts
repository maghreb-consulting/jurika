import { create } from 'zustand';

/**
 * Lot L6b (E11) : fenetres globales ouvertes par la palette, les raccourcis et JURI.
 *
 * - `palette` : palette de commandes (Ctrl+K) ;
 * - `raccourcis` : fenetre "Raccourcis clavier" (touche ?) ;
 * - `nouveauTicket` : tiroir de creation d'un ticket (touche n, palette) ;
 * - `juri` : panneau d'aide de JURI.
 *
 * Une seule fenetre a la fois : en ouvrir une ferme les autres (elles sont modales).
 */
export type FenetreGlobale = 'palette' | 'raccourcis' | 'nouveauTicket' | 'juri';

interface CommandesState {
  ouverte: FenetreGlobale | null;
  ouvrir: (f: FenetreGlobale) => void;
  fermer: (f?: FenetreGlobale) => void;
  basculer: (f: FenetreGlobale) => void;
}

export const useCommandesStore = create<CommandesState>((set, get) => ({
  ouverte: null,
  ouvrir: (f) => set({ ouverte: f }),
  fermer: (f) => {
    if (!f || get().ouverte === f) set({ ouverte: null });
  },
  basculer: (f) => set({ ouverte: get().ouverte === f ? null : f }),
}));
