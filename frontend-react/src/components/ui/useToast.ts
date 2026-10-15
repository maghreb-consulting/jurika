import { createContext, useContext } from 'react';

/**
 * Contexte et crochet des toasts (le fournisseur et l'affichage vivent dans
 * Toast.tsx ; separation imposee par react-refresh/only-export-components).
 */

export type ToastVariant = 'success' | 'error' | 'info';

export interface ToastApi {
  /** Ajoute un toast generique et renvoie son id (pour un dismiss manuel eventuel). */
  show: (message: string, variant?: ToastVariant, duration?: number) => number;
  success: (message: string, duration?: number) => number;
  error: (message: string, duration?: number) => number;
  info: (message: string, duration?: number) => number;
  /** Confirmation discrete d'un enregistrement (defaut : "Enregistre"), 2,5 s. */
  enregistre: (message?: string) => number;
  dismiss: (id: number) => void;
}

export const ToastContext = createContext<ToastApi | null>(null);

/**
 * Hook d'acces a l'API toast. A utiliser dans les composants sous
 * <ToastProvider>. Lance si le provider est absent (aide au diagnostic).
 */
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast doit être utilisé à l’intérieur de <ToastProvider>.');
  }
  return ctx;
}
