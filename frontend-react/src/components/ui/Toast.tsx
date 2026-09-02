import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { twMerge } from 'tailwind-merge';

/**
 * Système de toast maison (aucune dépendance externe : ni sonner ni
 * react-toastify). Cohérent avec les primitives `ui/` (palette navy/or/slate,
 * coins arrondis, icône lucide, `role="status"` + `aria-live`).
 *
 * Usage :
 *   const toast = useToast();
 *   toast.success('Source ajoutée');
 *   toast.error('Échec de l’enregistrement');
 *   toast.info('Traitement en cours…');
 *
 * Monté UNE SEULE FOIS à la racine (`App.tsx`) via <ToastProvider>.
 *
 * Le toast COMPLÈTE les retours in-app transitoires (succès/erreur ponctuelle
 * d'action) ; il ne REMPLACE PAS la validation de formulaire inline (prop
 * `error` de TextField) ni les `ErrorState` de page.
 */

export type ToastVariant = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  variant: ToastVariant;
  message: string;
  /** ms avant auto-dismiss ; 0 = persistant (fermeture manuelle uniquement). */
  duration: number;
}

interface ToastApi {
  /** Ajoute un toast générique et renvoie son id (pour un dismiss manuel éventuel). */
  show: (message: string, variant?: ToastVariant, duration?: number) => number;
  success: (message: string, duration?: number) => number;
  error: (message: string, duration?: number) => number;
  info: (message: string, duration?: number) => number;
  dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const DEFAULT_DURATION = 4000;

const VARIANT_META: Record<
  ToastVariant,
  { icon: typeof CheckCircle2; accent: string; iconClass: string }
> = {
  success: {
    icon: CheckCircle2,
    accent: 'border-l-success',
    iconClass: 'text-success',
  },
  error: {
    icon: AlertCircle,
    accent: 'border-l-danger',
    iconClass: 'text-danger',
  },
  info: {
    icon: Info,
    accent: 'border-l-accent',
    iconClass: 'text-accent',
  },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const idRef = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const show = useCallback(
    (message: string, variant: ToastVariant = 'info', duration = DEFAULT_DURATION) => {
      const id = ++idRef.current;
      setToasts((list) => [...list, { id, variant, message, duration }]);
      if (duration > 0) {
        const timer = setTimeout(() => dismiss(id), duration);
        timers.current.set(id, timer);
      }
      return id;
    },
    [dismiss],
  );

  // Capture la Map de timers au montage : le cleanup lit une référence stable
  // (règle exhaustive-deps) plutôt que `timers.current` au démontage.
  useEffect(() => {
    const store = timers.current;
    return () => {
      store.forEach((t) => clearTimeout(t));
      store.clear();
    };
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      success: (message, duration) => show(message, 'success', duration),
      error: (message, duration) => show(message, 'error', duration),
      info: (message, duration) => show(message, 'info', duration),
      dismiss,
    }),
    [show, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <ToastViewport toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

function ToastViewport({
  toasts,
  onDismiss,
}: {
  toasts: ToastItem[];
  onDismiss: (id: number) => void;
}) {
  if (typeof document === 'undefined') return null;
  return createPortal(
    <div
      className="pointer-events-none fixed inset-x-0 bottom-0 z-[100] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end"
      // aria-live sur le conteneur : chaque toast ajouté est annoncé.
      role="region"
      aria-label="Notifications"
    >
      {toasts.map((t) => (
        <ToastCard key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </div>,
    document.body,
  );
}

function ToastCard({ toast, onDismiss }: { toast: ToastItem; onDismiss: (id: number) => void }) {
  const meta = VARIANT_META[toast.variant];
  const Icon = meta.icon;
  return (
    <div
      role="status"
      aria-live={toast.variant === 'error' ? 'assertive' : 'polite'}
      className={twMerge(
        'pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border border-border-hi border-l-4 bg-bg-overlay px-4 py-3 text-sm text-fg shadow-card-lifted',
        meta.accent,
      )}
    >
      <Icon className={twMerge('mt-0.5 h-5 w-5 shrink-0', meta.iconClass)} aria-hidden="true" />
      <p className="min-w-0 flex-1 break-words">{toast.message}</p>
      <button
        type="button"
        onClick={() => onDismiss(toast.id)}
        aria-label="Fermer la notification"
        className="-mr-1 -mt-0.5 shrink-0 rounded p-1 text-fg-subtle transition-colors hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

/**
 * Hook d'accès à l'API toast. À utiliser dans les composants sous
 * <ToastProvider>. Lance si le provider est absent (aide au diagnostic).
 */
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast doit être utilisé à l’intérieur de <ToastProvider>.');
  }
  return ctx;
}
