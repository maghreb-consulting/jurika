import { useContext } from 'react';
import { useAuthStore } from '../../store/authStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { ToastContext, type ToastApi } from '../ui/useToast';

/**
 * Lot L6b (E11) : etat d'affichage de JURI, lu sur le compte (preferences, `juriMasque`).
 *
 * - `affiche` : JURI est dessine. Seulement quand le choix du compte est connu (pas
 *   d'apparition puis disparition au chargement) ; si la lecture echoue, JURI s'affiche
 *   (la coquille signale l'erreur ; rien n'est suppose masque).
 * - `place` : sa place est reservee a droite de la zone de contenu, des l'ouverture de la
 *   session, sauf choix "masque" connu : aucun decalage du contenu quand JURI arrive.
 * - `masque` : choix connu "masque" (menu du compte, palette).
 */
export function useEtatJuri(): { affiche: boolean; place: boolean; masque: boolean } {
  const connecte = useAuthStore((s) => !!s.user);
  const preferences = usePreferencesStore((s) => s.preferences);
  const statut = usePreferencesStore((s) => s.statut);
  if (!connecte) return { affiche: false, place: false, masque: false };
  const masque = preferences?.juriMasque === true;
  const connu = preferences !== null || statut === 'erreur';
  return { affiche: connu && !masque, place: !masque, masque };
}

/** Toasts sans exiger le fournisseur (coquille rendue seule en test) : null s'il manque. */
export function useToastSiDisponible(): ToastApi | null {
  return useContext(ToastContext);
}
