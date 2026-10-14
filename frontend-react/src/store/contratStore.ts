import { create } from 'zustand';
import { contratService, estEtatComplet } from '../services/contrat.service';
import type { EtatContrat, EtatContratEmploye, ObjetAcceptation } from '../types/contrat';

/**
 * Lot L7 (E6, E9, RG-CTR-03 et RG-CTR-07) : etat du contrat d'abonnement du cabinet, lu par la
 * garde de l'ecran (AppShell). Le serveur reste juge (la passerelle refuse les autres routes) ;
 * l'ecran ne fait que rediriger vers le contrat et dire quoi faire.
 *
 * - `bloque` : conditions particulieres en attente d'acceptation : seul l'ecran du contrat.
 * - `nouvelleVersion` : une nouvelle version des conditions generales attend la reponse du
 *   superviseur (Accepter ou Refuser), sans blocage (article 16).
 * - `versionPresentee` : utilisateur a qui la demande de reponse a deja ete presentee pendant
 *   cette connexion ; remis a zero a la sortie de l'application (deconnexion) et au
 *   rechargement, pour que la demande revienne a chaque connexion tant qu'il n'a pas repondu.
 */
interface ContratState {
  etat: EtatContrat | EtatContratEmploye | null;
  /** Utilisateur dont l'etat a ete lu (un autre compte dans le meme onglet relit). */
  utilisateur: string | null;
  /** Vrai apres la premiere lecture (reussie ou non) pour `utilisateur`. */
  lu: boolean;
  bloque: boolean;
  objet: ObjetAcceptation | null;
  versionPresentee: string | null;
  charger: (userId: string) => Promise<void>;
  marquerVersionPresentee: (userId: string | null) => void;
  vider: () => void;
}

export const useContratStore = create<ContratState>()((set, get) => ({
  etat: null,
  utilisateur: null,
  lu: false,
  bloque: false,
  objet: null,
  versionPresentee: null,
  charger: async (userId: string) => {
    if (get().utilisateur !== userId) {
      set({ etat: null, lu: false, bloque: false, objet: null, utilisateur: userId, versionPresentee: null });
    }
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
  marquerVersionPresentee: (userId) => set({ versionPresentee: userId }),
  vider: () => set({ etat: null, utilisateur: null, lu: false, bloque: false, objet: null, versionPresentee: null }),
}));
