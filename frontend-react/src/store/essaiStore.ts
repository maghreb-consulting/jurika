import { useEffect } from 'react';
import { create } from 'zustand';
import { essaiService } from '../services/essai.service';
import { useAuthStore } from './authStore';

/**
 * Decision du 2026-10-14 (RG-ESS-05) : l'ecran sait si le cabinet est en essai gratuit (en cours
 * ou en lecture seule), pour ne proposer aucun telechargement, enregistrement ni impression, et
 * passer par l'apercu en pages filigranees. Le serveur reste seul juge (P6) : il refuse tout
 * fichier pendant l'essai, quoi que l'ecran affiche.
 *
 * Etat lu une fois par utilisateur connecte. Etat illisible pour un compte du cabinet : traite
 * comme un essai (aucun bouton de telechargement ; l'apercu en pages fonctionne pour tous).
 * Super-admin : jamais en essai.
 */
type Etat = 'inconnu' | 'essai' | 'hors-essai';

interface EssaiState {
  utilisateur: string | null;
  etat: Etat;
  enCours: Promise<boolean> | null;
  charger: (utilisateur: string, cabinet: boolean) => Promise<boolean>;
  reinitialiser: () => void;
}

export const useEssaiStore = create<EssaiState>()((set, get) => ({
  utilisateur: null,
  etat: 'inconnu',
  enCours: null,
  charger: (utilisateur, cabinet) => {
    const s = get();
    if (s.utilisateur === utilisateur) {
      if (s.etat !== 'inconnu') return Promise.resolve(s.etat === 'essai');
      if (s.enCours) return s.enCours;
    }
    if (!cabinet) {
      set({ utilisateur, etat: 'hors-essai', enCours: null });
      return Promise.resolve(false);
    }
    const p = essaiService
      .etat()
      .then((e) => e.enEssai)
      .catch(() => true)
      .then((enEssai) => {
        if (get().utilisateur === utilisateur) set({ etat: enEssai ? 'essai' : 'hors-essai', enCours: null });
        return enEssai;
      });
    set({ utilisateur, etat: 'inconnu', enCours: p });
    return p;
  },
  reinitialiser: () => set({ utilisateur: null, etat: 'inconnu', enCours: null }),
}));

function compte(): { id: string; cabinet: boolean } | null {
  const u = useAuthStore.getState().user;
  if (!u) return null;
  const cabinet = u.role === 'SUPERVISEUR' || u.role === 'EMPLOYE' || u.role === 'CLIENT';
  return { id: `${u.userId}-${u.workspaceId}-${u.role}`, cabinet };
}

/** Pour la logique (generation, depot) : vrai si le cabinet est en essai. */
export function estEnEssai(): Promise<boolean> {
  const c = compte();
  if (!c) return Promise.resolve(false);
  return useEssaiStore.getState().charger(c.id, c.cabinet);
}

/**
 * Pour l'affichage : `true` en essai, `false` hors essai, `null` tant que l'etat n'est pas lu
 * (les ecrans n'affichent alors aucun bouton de telechargement).
 */
export function useEnEssai(): boolean | null {
  const user = useAuthStore((s) => s.user);
  const etat = useEssaiStore((s) => s.etat);
  const utilisateur = useEssaiStore((s) => s.utilisateur);
  const cle = user ? `${user.userId}-${user.workspaceId}-${user.role}` : null;
  useEffect(() => {
    if (cle) void estEnEssai();
  }, [cle]);
  if (!user) return false;
  if (utilisateur !== cle || etat === 'inconnu') return null;
  return etat === 'essai';
}
