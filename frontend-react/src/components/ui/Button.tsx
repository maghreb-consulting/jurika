import { useRef, type ButtonHTMLAttributes, type ReactNode, type MouseEvent as ReactMouseEvent } from 'react';
import { twMerge } from 'tailwind-merge';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  /**
   * Sprint 12.5 T4 — Magnetic hover effect porté de marketing-site.
   * Si actif, le bouton suit légèrement le curseur (translation -4px..+4px max).
   * Désactivé automatiquement via prefers-reduced-motion CSS dans index.css T13.
   * Recommandé uniquement sur CTA primaires (signup, billing upgrade, etc.).
   */
  magnetic?: boolean;
  children: ReactNode;
}

const baseClasses =
  'inline-flex items-center justify-center rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-bg';

// Sprint 12.5 T4 — variantes alignées sur palette marketing (navy / or / emeraude)
const variantClasses: Record<NonNullable<Props['variant']>, string> = {
  // CTA primaire : gradient or signature, texte navy foncé pour contraste AA
  primary:
    'bg-gradient-to-br from-accent to-accent-hover text-bg shadow-card hover:from-accent-hover hover:to-accent focus-visible:ring-accent',
  // Outline navy : bordure or discrète, fond raised
  secondary:
    'bg-bg-raised text-fg border border-border-hi hover:border-accent hover:text-accent focus-visible:ring-accent',
  // Ghost : transparent, hover overlay navy
  ghost:
    'bg-transparent text-fg-muted hover:bg-bg-overlay hover:text-fg focus-visible:ring-accent',
  // Danger : rouge token, conservé pour irreversible actions
  danger:
    'bg-danger text-fg hover:bg-danger/85 focus-visible:ring-danger',
};

const sizeClasses: Record<NonNullable<Props['size']>, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-5 py-3 text-base',
};

// Sprint 12.5 T4 — Magnetic hover (port marketing-site).
// Translation max 6px à 60% du chemin curseur->centre. Reset en mouseleave.
// PAS de spring (overhead JS minimal). Désactivé automatiquement via
// prefers-reduced-motion sur l'ancetre (les classes Tailwind transition-transform
// honorent la media query).
const MAGNETIC_STRENGTH = 0.18;
const MAGNETIC_MAX_PX = 6;

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  magnetic = false,
  className,
  disabled,
  children,
  onMouseMove,
  onMouseLeave,
  ...rest
}: Props) {
  const ref = useRef<HTMLButtonElement | null>(null);

  function handleMouseMove(e: ReactMouseEvent<HTMLButtonElement>) {
    onMouseMove?.(e);
    if (!magnetic || !ref.current) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const rect = ref.current.getBoundingClientRect();
    const dx = e.clientX - (rect.left + rect.width / 2);
    const dy = e.clientY - (rect.top + rect.height / 2);
    const tx = Math.max(-MAGNETIC_MAX_PX, Math.min(MAGNETIC_MAX_PX, dx * MAGNETIC_STRENGTH));
    const ty = Math.max(-MAGNETIC_MAX_PX, Math.min(MAGNETIC_MAX_PX, dy * MAGNETIC_STRENGTH));
    ref.current.style.transform = `translate(${tx}px, ${ty}px)`;
  }

  function handleMouseLeave(e: ReactMouseEvent<HTMLButtonElement>) {
    onMouseLeave?.(e);
    if (!magnetic || !ref.current) return;
    ref.current.style.transform = '';
  }

  return (
    <button
      ref={ref}
      className={twMerge(
        baseClasses,
        magnetic && 'transition-transform duration-150 ease-out',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      disabled={disabled || loading}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      {...rest}
    >
      {loading && (
        <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current/40 border-t-current" />
      )}
      {children}
    </button>
  );
}
