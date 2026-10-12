import { create } from 'zustand';

/**
 * Lot L5 E8 (RG-COM-01) : etat d'ouverture du panneau lateral du chatbot, partage entre la
 * bulle flottante (montee dans la mise en page commune) et l'ancienne adresse /chatbot,
 * qui ouvre le panneau au lieu d'afficher une page pleine.
 */
interface AssistantState {
  ouvert: boolean;
  ouvrir: () => void;
  fermer: () => void;
  basculer: () => void;
}

export const useAssistantStore = create<AssistantState>((set) => ({
  ouvert: false,
  ouvrir: () => set({ ouvert: true }),
  fermer: () => set({ ouvert: false }),
  basculer: () => set((s) => ({ ouvert: !s.ouvert })),
}));
