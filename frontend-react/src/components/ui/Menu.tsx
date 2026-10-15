import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../../lib/utils';

/**
 * Menu deroulant (lot L6b, E1), motif ARIA "menu button".
 *
 * - Le panneau est un element `popover="manual"` (API Popover) : il s'affiche dans la
 *   couche superieure, jamais rogne par un conteneur `overflow` ni masque par un z-index.
 *   Sans l'API (jsdom, vieux navigateur), il s'affiche en place.
 * - Clavier : Entree / Espace / fleche bas ouvrent et placent le focus sur le premier
 *   element ; fleches haut / bas, Debut / Fin ; Echap ferme et rend le focus au bouton ;
 *   Tab ferme. Un clic hors du menu le ferme.
 *
 *   <Menu libelle="Menu du compte" bouton={<Avatar />} alignement="fin">
 *     <MenuElement to="/profile" icone={<User />}>Mon compte</MenuElement>
 *     <MenuSeparateur />
 *     <MenuElement onSelect={deconnecter} danger>Deconnexion</MenuElement>
 *   </Menu>
 */

interface ContexteMenu {
  fermer: (rendreFocus?: boolean) => void;
}
const Ctx = createContext<ContexteMenu | null>(null);

/** API Popover connue du navigateur (sinon, pas d'attribut : un [popover] resterait masque). */
const POPOVER = typeof HTMLElement !== 'undefined' && 'showPopover' in HTMLElement.prototype;

function elements(panneau: HTMLElement | null): HTMLElement[] {
  if (!panneau) return [];
  return Array.from(panneau.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"]), [role="menuitemradio"]'));
}

export function Menu({
  libelle,
  bouton,
  children,
  entete,
  alignement = 'debut',
  classeBouton,
  classePanneau,
  titre,
}: {
  /** Nom accessible du bouton (aria-label). */
  libelle: string;
  /** Contenu visible du bouton. */
  bouton: ReactNode;
  children: ReactNode;
  /** Bloc non interactif en tete du panneau (ex. nom du cabinet). */
  entete?: ReactNode;
  alignement?: 'debut' | 'fin';
  classeBouton?: string;
  classePanneau?: string;
  /** Infobulle du bouton. */
  titre?: string;
}) {
  const [ouvert, setOuvert] = useState(false);
  const [position, setPosition] = useState<CSSProperties>({});
  const refBouton = useRef<HTMLButtonElement | null>(null);
  const refPanneau = useRef<HTMLDivElement | null>(null);
  const focusPremier = useRef(false);
  const idPanneau = useId();

  const fermer = useCallback((rendreFocus = false) => {
    setOuvert(false);
    if (rendreFocus) refBouton.current?.focus();
  }, []);

  const placer = useCallback(() => {
    const r = refBouton.current?.getBoundingClientRect();
    if (!r) return;
    const haut = r.bottom + 4;
    setPosition(
      alignement === 'fin'
        ? { top: haut, right: Math.max(8, window.innerWidth - r.right) }
        : { top: haut, left: Math.max(8, r.left) },
    );
  }, [alignement]);

  useLayoutEffect(() => {
    const p = refPanneau.current;
    if (!ouvert || !p) return;
    placer();
    if (typeof p.showPopover === 'function' && !p.matches(':popover-open')) p.showPopover();
    if (focusPremier.current) {
      elements(p)[0]?.focus();
      focusPremier.current = false;
    }
    return () => {
      if (typeof p.hidePopover === 'function' && p.matches(':popover-open')) p.hidePopover();
    };
  }, [ouvert, placer]);

  useEffect(() => {
    if (!ouvert) return;
    const dehors = (e: PointerEvent | MouseEvent) => {
      const t = e.target as Node;
      if (refPanneau.current?.contains(t) || refBouton.current?.contains(t)) return;
      setOuvert(false);
    };
    window.addEventListener('pointerdown', dehors);
    window.addEventListener('resize', placer);
    window.addEventListener('scroll', placer, true);
    return () => {
      window.removeEventListener('pointerdown', dehors);
      window.removeEventListener('resize', placer);
      window.removeEventListener('scroll', placer, true);
    };
  }, [ouvert, placer]);

  function clavierBouton(e: KeyboardEvent<HTMLButtonElement>) {
    if (e.key === 'ArrowDown' || ((e.key === 'Enter' || e.key === ' ') && !ouvert)) {
      e.preventDefault();
      focusPremier.current = true;
      if (ouvert) elements(refPanneau.current)[0]?.focus();
      else setOuvert(true);
    }
  }

  function clavierPanneau(e: KeyboardEvent<HTMLDivElement>) {
    const liste = elements(refPanneau.current);
    const i = liste.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      fermer(true);
    } else if (e.key === 'Tab') {
      fermer(false);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      liste[(i + 1) % liste.length]?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      liste[(i - 1 + liste.length) % liste.length]?.focus();
    } else if (e.key === 'Home') {
      e.preventDefault();
      liste[0]?.focus();
    } else if (e.key === 'End') {
      e.preventDefault();
      liste[liste.length - 1]?.focus();
    }
  }

  return (
    <Ctx.Provider value={{ fermer }}>
      <button
        ref={refBouton}
        type="button"
        aria-haspopup="menu"
        aria-expanded={ouvert}
        aria-controls={ouvert ? idPanneau : undefined}
        aria-label={libelle}
        title={titre}
        onClick={() => setOuvert((v) => !v)}
        onKeyDown={clavierBouton}
        className={cn(
          'inline-flex items-center gap-2 rounded-lg transition-colors duration-150 hover:bg-bg-overlay aria-expanded:bg-bg-overlay',
          classeBouton,
        )}
      >
        {bouton}
      </button>
      {ouvert && (
        <div
          ref={refPanneau}
          id={idPanneau}
          popover={POPOVER ? 'manual' : undefined}
          role="menu"
          aria-label={libelle}
          onKeyDown={clavierPanneau}
          style={position}
          className={cn(
            POPOVER ? 'menu-flottant' : 'fixed',
            'z-50 min-w-56 max-w-[calc(100vw-1rem)] overflow-hidden rounded-lg border border-border bg-bg-raised py-1 text-fg shadow-card-lifted',
            classePanneau,
          )}
        >
          {entete && <div className="border-b border-border px-4 py-3">{entete}</div>}
          {children}
        </div>
      )}
    </Ctx.Provider>
  );
}

const CLASSES_ELEMENT =
  'flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm outline-none transition-colors duration-150 ' +
  'hover:bg-bg-overlay focus-visible:bg-bg-overlay focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus [&>svg]:h-4 [&>svg]:w-4 [&>svg]:shrink-0';

export function MenuElement({
  children,
  icone,
  to,
  onSelect,
  danger = false,
  disabled = false,
}: {
  children: ReactNode;
  icone?: ReactNode;
  /** Lien interne : l'element est un <Link>. */
  to?: string;
  onSelect?: () => void;
  danger?: boolean;
  disabled?: boolean;
}) {
  const ctx = useContext(Ctx);
  const classes = cn(CLASSES_ELEMENT, danger ? 'text-danger' : 'text-fg', disabled && 'cursor-not-allowed opacity-55');
  const contenu = (
    <>
      {icone && <span aria-hidden="true" className="inline-flex text-fg-subtle [&>svg]:h-4 [&>svg]:w-4">{icone}</span>}
      <span className="min-w-0 flex-1">{children}</span>
    </>
  );
  if (to && !disabled) {
    return (
      <Link role="menuitem" tabIndex={-1} to={to} className={classes} onClick={() => ctx?.fermer(false)}>
        {contenu}
      </Link>
    );
  }
  return (
    <button
      type="button"
      role="menuitem"
      tabIndex={-1}
      aria-disabled={disabled || undefined}
      className={classes}
      onClick={() => {
        if (disabled) return;
        ctx?.fermer(false);
        onSelect?.();
      }}
    >
      {contenu}
    </button>
  );
}

export function MenuSeparateur() {
  return <div role="separator" className="my-1 border-t border-border" />;
}

/** Groupe de choix exclusifs dans un menu (ex. theme) : role menuitemradio. */
export function MenuChoix({
  children,
  icone,
  coche,
  onSelect,
}: {
  children: ReactNode;
  icone?: ReactNode;
  coche: boolean;
  onSelect: () => void;
}) {
  const ctx = useContext(Ctx);
  return (
    <button
      type="button"
      role="menuitemradio"
      aria-checked={coche}
      tabIndex={-1}
      className={cn(CLASSES_ELEMENT, 'text-fg', coche && 'font-semibold')}
      onClick={() => {
        ctx?.fermer(true);
        onSelect();
      }}
    >
      {icone && <span aria-hidden="true" className="inline-flex text-fg-subtle [&>svg]:h-4 [&>svg]:w-4">{icone}</span>}
      <span className="min-w-0 flex-1">{children}</span>
      {coche && <span aria-hidden="true" className="h-2 w-2 rounded-full bg-accent" />}
    </button>
  );
}
