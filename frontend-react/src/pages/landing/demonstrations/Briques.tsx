import { useEffect, useState, type ReactNode } from 'react';
import { Info, Monitor, Search } from 'lucide-react';
import { TextField, type TextFieldProps } from '../../../components/ui/TextField';
import { menuDuRole } from '../../../components/layout/navigation';
import { cn } from '../../../lib/utils';
import embleme from '../../../assets/brand/moucharabieh/embleme.svg';

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

/**
 * Coquille de l'application, comme celle de l'employe : en-tete (embleme, recherche, choix du
 * theme, compte) et menu lateral marine. L6c E3 : le menu est LU dans la configuration reelle
 * (components/layout/navigation.ts, menuDuRole('EMPLOYE')) : memes groupes, memes libelles,
 * meme ordre, memes icones ; `actif` est le libelle de l'entree de la page montree.
 */
const MENU_EMPLOYE = menuDuRole('EMPLOYE');

export function Coquille({ actif, theme, children }: { actif: string; theme?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex h-full w-full flex-col bg-bg text-fg">
      <div className="relative z-30 flex h-10 shrink-0 items-center gap-3 border-b border-border bg-bg-raised px-3">
        <img src={embleme} alt="" width={22} height={22} className="h-[22px] w-[22px]" draggable={false} />
        <span className="text-xs font-bold tracking-[0.2em] text-heading">JURIKA</span>
        <span className="ml-6 flex h-7 w-[230px] items-center gap-1.5 rounded-md border border-border-input px-2 text-[11px] text-fg-subtle">
          <Search className="h-3.5 w-3.5" aria-hidden="true" />
          Rechercher un dossier, un ticket…
        </span>
        <span className="ml-auto flex items-center gap-2">
          {theme ?? (
            <span className="inline-flex h-7 w-7 items-center justify-center text-fg-muted">
              <Monitor className="h-4 w-4" aria-hidden="true" />
            </span>
          )}
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-accent-soft text-[10px] font-semibold text-accent">N</span>
          <span className="leading-tight">
            <span className="block text-[10px] font-medium text-fg">Nadia Exemple</span>
            <span className="block text-[9px] text-fg-muted">Employé</span>
          </span>
        </span>
      </div>
      <div className="flex min-h-0 flex-1">
        <div className="flex w-[148px] shrink-0 flex-col gap-3 bg-nav-bg px-2 py-3" data-menu-demo="">
          {MENU_EMPLOYE.map((g) => (
            <div key={g.titre} data-groupe-demo={g.titre}>
              <p className="mb-1 px-2 text-[9px] font-semibold uppercase tracking-wide text-nav-fg">{g.titre}</p>
              {g.entrees.map((e) => {
                const Icone = e.icone;
                const est = e.libelle === actif;
                return (
                  <span
                    key={e.libelle}
                    className={cn(
                      'flex items-center gap-2 rounded-md border-l-2 px-2 py-1.5 text-[11px] font-medium',
                      est ? 'border-nav-active bg-nav-bg-hover text-nav-active' : 'border-transparent text-nav-fg-hi',
                    )}
                  >
                    <Icone className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    <span data-entree-demo="" data-actif={est ? '' : undefined}>
                      {e.libelle}
                    </span>
                  </span>
                );
              })}
            </div>
          ))}
        </div>
        <div className="relative min-w-0 flex-1 overflow-hidden">{children}</div>
      </div>
    </div>
  );
}
