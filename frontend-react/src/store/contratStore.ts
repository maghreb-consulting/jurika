import { create } from 'zustand';
import { contratService, estEtatComplet } from '../services/contrat.service';
import type { EtatContrat, EtatContratEmploye, ObjetAcceptation } from '../types/contrat';

/**
 * Lot L7 (E6, RG-CTR-03) : etat du contrat d'abonnement du cabinet, lu par la garde de l'ecran
 * (AppShell). Le serveur reste juge (la passerelle refuse les autres routes) ; l'ecran ne fait
 * que rediriger vers le contrat et dire quoi faire.
 *
 * - `bloque` : conditions particulieres en attente d'acceptation : seul l'ecran du contrat.
 * - `nouvelleVersion` : une nouvelle version des conditions generales est a accepter (non bloquant).
 */
interface ContratState {
  etat: EtatContrat | EtatContratEmploye | null;
  /** Utilisateur dont l'etat a ete lu (un autre compte dans le meme onglet relit). */
  utilisateur: string | null;
  /** Vrai apres la premiere lecture (reussie ou non) pour `utilisateur`. */
  lu: boolean;
  bloque: boolean;
  objet: ObjetAcceptation | null;
  charger: (userId: string) => Promise<void>;
  vider: () => void;
}

export const useContratStore = create<ContratState>()((set, get) => ({
  etat: null,
  utilisateur: null,
  lu: false,
  bloque: false,
  objet: null,
  charger: async (userId: string) => {
    if (get().utilisateur !== userId) set({ etat: null, lu: false, bloque: false, objet: null, utilisateur: userId });
    try {
      const etat = await contratService.etat();
      const objet = estEtatComplet(etat)
        ? etat.objetAAccepter
        : etat.acceptationRequise ? 'CONDITIONS_PARTICULIERES' : null;
      set({ etat, lu: true, objet, bloque: objet === 'CONDITIONS_PARTICULIERES' });
    } catch {
      // Etat illisible : l'ecran ne bloque pas ; la passerelle, elle, refuse ce qui doit l'etre.
      set({ lu: true });
    }
  },
  vider: () => set({ etat: null, utilisateur: null, lu: false, bloque: false, objet: null }),
}));
