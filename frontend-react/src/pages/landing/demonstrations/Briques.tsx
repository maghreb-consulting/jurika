import { useEffect, useState, type ReactNode } from 'react';
import { Info } from 'lucide-react';
import { TextField, type TextFieldProps } from '../../../components/ui/TextField';
import { cn } from '../../../lib/utils';

/**
 * Lot L6c (E1) : briques communes des scenes animees de la vitrine.
 *
 * - `Curseur` : pointeur simule ; il glisse d'un point a l'autre (transform, transition CSS)
 *   et marque chaque clic d'un cercle qui s'efface (`clic` : compteur, chaque valeur nouvelle
 *   rejoue le cercle).
 * - `ChampFrappe` : le vrai champ de saisie (TextField) dont la valeur s'ecrit lettre a
 *   lettre tant que la scene joue ; monte seulement pendant l'etape de saisie.
 * - `EncadreAide` : meme dessin que l'aide integree (TexteAide), sans son magasin : la
 *   vitrine ne lit aucun etat de l'application.
 * Coordonnees en pixels de la taille de dessin de la scene (scenes.ts, DESSIN_SCENE).
 */

export function Curseur({ x, y, clic = 0, visible = true }: { x: number; y: number; clic?: number; visible?: boolean }) {
  return (
    <span
      className="v-curseur"
      data-curseur=""
      style={{ transform: `translate(${x}px, ${y}px)`, opacity: visible ? 1 : 0 }}
    >
      {clic > 0 && <span key={clic} className="v-curseur-clic" />}
      <svg viewBox="0 0 16 22" width="18" height="24" className="v-curseur-fleche" focusable="false">
        <path
          d="M1 1 L1 17.5 L5.4 13.4 L8.4 20.4 L11.2 19.2 L8.2 12.3 L14.2 12.3 Z"
          style={{ fill: 'var(--jeton-heading)', stroke: 'var(--jeton-bg-raised)' }}
          strokeWidth="1.4"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  );
}

export function ChampFrappe({
  texte,
  joue,
  vitesse = 65,
  ...props
}: Omit<TextFieldProps, 'value'> & { texte: string; joue: boolean; vitesse?: number }) {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!joue || n >= texte.length) return undefined;
    const t = window.setTimeout(() => setN((v) => v + 1), vitesse);
    return () => window.clearTimeout(t);
  }, [joue, n, texte, vitesse]);
  return <TextField {...props} value={texte.slice(0, n)} readOnly tabIndex={-1} />;
}

/** Champ fixe (valeur posee) : meme composant, lecture seule, hors du parcours du clavier. */
export function ChampFixe(props: TextFieldProps) {
  return <TextField {...props} readOnly tabIndex={-1} />;
}

export function EncadreAide({ titre, children, className }: { titre: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn('rounded-lg border border-border border-l-4 border-l-info bg-info-soft py-3 pl-4 pr-4 text-sm leading-relaxed text-fg', className)}>
      <p className="mb-1 flex items-center gap-1.5 font-semibold text-fg">
        <Info className="h-4 w-4 text-info" aria-hidden="true" />
        {titre}
      </p>
      {children}
    </div>
  );
}

/** Coquille de l'application : barre laterale marine et zone de contenu. */
export function Coquille({ actif, children }: { actif: string; children: ReactNode }) {
  const menu = ['Tableau de bord', 'Tickets', 'Data Rooms', 'Clients', 'Échéances'];
  return (
    <div className="flex h-full w-full bg-bg text-fg">
      <div className="flex w-[148px] shrink-0 flex-col gap-1 bg-nav-bg px-3 py-4">
        <span className="mb-4 px-2 text-sm font-bold tracking-[0.2em] text-nav-active">JURIKA</span>
        {menu.map((m) => (
          <span
            key={m}
            className={cn(
              'rounded-md px-2 py-1.5 text-xs font-medium',
              m === actif ? 'bg-nav-bg-hover text-nav-active' : 'text-nav-fg',
            )}
          >
            {m}
          </span>
        ))}
      </div>
      <div className="relative min-w-0 flex-1 overflow-hidden">{children}</div>
    </div>
  );
}
