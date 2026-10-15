import { useCallback, useContext } from 'react';
import { ToastContext } from '../ui/useToast';

/**
 * Lot L6b (E9) : confirmation discrete « Enregistré » (toast de la bibliotheque) pour les ecrans
 * d'administration. Sans fournisseur de toasts (rendu isole d'un test), la confirmation est
 * simplement omise : l'action reste faite et son resultat visible a l'ecran.
 */
export function useEnregistre(): (message?: string) => void {
  const toasts = useContext(ToastContext);
  return useCallback(
    (message?: string) => {
      toasts?.enregistre(message);
    },
    [toasts],
  );
}
