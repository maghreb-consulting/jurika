import { useEffect, useState, type ReactNode } from 'react';
import { Bell, Calendar, ChevronDown, Info, Monitor, PanelLeftClose, Search, X } from 'lucide-react';
import { TextField, ZoneTexte, type TextFieldProps, type ZoneTexteProps } from '../../../components/ui/TextField';
import { FilAriane } from '../../../components/ui/FilAriane';
import { Touches } from '../../../components/palette/Touches';
import { JuriRobot } from '../../../components/assistant/JuriRobot';
import { menuDuRole } from '../../../components/layout/navigation';
import { cn } from '../../../lib/utils';
import logoClair from '../../../assets/brand/moucharabieh/logo-horizontal-clair.svg';
import logoSombre from '../../../assets/brand/moucharabieh/logo-horizontal-sombre-sans-fond.svg';

/**
 * Lot L6c : briques communes des scenes animees de la vitrine.
 *
 * L6c E4 (retour de l'utilisateur du 2026-10-17) : chaque scene est un VRAI ecran de
 * l'application rendu a 1280 x 800 pixels (la taille des anciennes videos), puis reduit d'un
 * bloc dans son cadre. La coquille reprend donc les dimensions et les classes de l'application
 * a cette largeur : en-tete de 56 px (AppEnTete), menu lateral de 240 px (MenuLateral), page
 * (AppShell : reserve de JURI a droite, contenu max-w-7xl, marges de 32 px), en-tete de page
 * (PageEnTete). Les classes a point de rupture (md:, lg:) de l'application sont ecrites a leur
 * valeur a 1280 px : la scene ne depend pas de la largeur de la fenetre du visiteur.
 *
 * - `Curseur` : pointeur simule ; il glisse d'un point a l'autre (transform, transition CSS)
 *   et marque chaque clic d'un cercle qui s'efface (`clic` : chaque valeur nouvelle rejoue).
 * - `ChampFrappe` : le vrai champ de saisie (TextField), valeur ecrite lettre a lettre.
 * - `EncadreAide` : meme dessin que l'aide integree (TexteAide), sans son magasin.
 * Coordonnees en pixels de la taille de dessin (scenes.ts, DESSIN_SCENE : 1280 x 800).
 */

export function Curseur({ x, y, clic = 0, visible = true }: { x: number; y: number; clic?: number; visible?: boolean }) {
  return (
    <span className="v-curseur" data-curseur="" style={{ transform: `translate(${x}px, ${y}px)`, opacity: visible ? 1 : 0 }}>
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

/** Texte ecrit lettre a lettre tant que `joue` est vrai (une lettre toutes les `vitesse` ms). */
function useFrappe(texte: string, joue: boolean, vitesse: number): string {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!joue || n >= texte.length) return undefined;
    const t = window.setTimeout(() => setN((v) => v + 1), vitesse);
    return () => window.clearTimeout(t);
  }, [joue, n, texte, vitesse]);
  return texte.slice(0, n);
}

export function ChampFrappe({
  texte,
  joue,
  vitesse = 65,
  ...props
}: Omit<TextFieldProps, 'value'> & { texte: string; joue: boolean; vitesse?: number }) {
  const valeur = useFrappe(texte, joue, vitesse);
  return <TextField {...props} value={valeur} readOnly tabIndex={-1} />;
}

/** Zone de texte (ZoneTexte) ecrite lettre a lettre. */
export function ZoneFrappe({
  texte,
  joue,
  vitesse = 35,
  ...props
}: Omit<ZoneTexteProps, 'value'> & { texte: string; joue: boolean; vitesse?: number }) {
  const valeur = useFrappe(texte, joue, vitesse);
  return <ZoneTexte {...props} value={valeur} readOnly tabIndex={-1} />;
}

/** Champ fixe (valeur posee) : meme composant, lecture seule, hors du parcours du clavier. */
export function ChampFixe(props: TextFieldProps) {
  return <TextField {...props} readOnly tabIndex={-1} />;
}

/** Aide integree : meme dessin que TexteAide (components/ui/Aide.tsx), bouton de masquage compris. */
export function EncadreAide({ titre, children, className }: { titre: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn('relative rounded-lg border border-border border-l-4 border-l-info bg-info-soft py-3 pl-4 pr-10 text-sm leading-relaxed text-fg', className)}>
      <p className="mb-1 flex items-center gap-1.5 font-semibold text-fg">
        <Info className="h-4 w-4 text-info" aria-hidden="true" />
        {titre}
      </p>
      {children}
      <span className="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-md text-fg-muted">
        <X className="h-4 w-4" aria-hidden="true" />
      </span>
    </div>
  );
}

/** En-tete de page, comme PageEnTete (components/layout/Page.tsx) : fil, titre, etat, actions. */
export function PageEnTeteDemo({
  fil,
  titre,
  etat,
  description,
  actions,
}: {
  fil: string[];
  titre: string;
  etat?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="@container mb-6 border-b border-border pb-4">
      <div className="min-h-[1.125rem]">
        <FilAriane elements={fil.map((libelle) => ({ libelle }))} />
      </div>
      <div className="mt-1.5 flex flex-row flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-[1_1_22rem]">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-heading">{titre}</h1>
            {etat}
          </div>
          {description && <div className="mt-1 max-w-3xl text-sm text-fg-muted">{description}</div>}
        </div>
        {actions && <div className="flex flex-[0_1_auto] flex-wrap items-center justify-end gap-2">{actions}</div>}
      </div>
    </header>
  );
}

/** Choix du theme de l'en-tete (ThemeToggle) ; `theme` remplace le bouton par defaut. */
function BoutonEntete({ children, pastille }: { children: ReactNode; pastille?: { n: number; ton: 'warning' | 'danger' } }) {
  return (
    <span className="relative inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted">
      {children}
      {pastille && (
        <span
          className={cn(
            'absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-bold',
            pastille.ton === 'warning' ? 'bg-warning text-warning-fg' : 'bg-danger text-danger-fg',
          )}
        >
          {pastille.n}
        </span>
      )}
    </span>
  );
}

const MENU_EMPLOYE = menuDuRole('EMPLOYE');

/**
 * Coquille de l'employe a 1280 x 800. `actif` : libelle de l'entree du menu de la page montree
 * (le menu est LU dans components/layout/navigation.ts). `defilement` : decalage vertical de
 * la page (la page defile sous l'en-tete et le menu, comme dans le navigateur). `juri` :
 * remplace JURI au bord droit (scene de JURI).
 */
export function Coquille({
  actif,
  theme,
  defilement = 0,
  cloche = 1,
  juri,
  children,
}: {
  actif: string;
  theme?: ReactNode;
  defilement?: number;
  cloche?: number;
  juri?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="absolute inset-0 overflow-hidden bg-bg text-fg">
      {/* En-tete (AppEnTete) */}
      <div className="absolute inset-x-0 top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-bg-raised px-4">
        <span className="flex shrink-0 items-center gap-3">
          <img src={logoClair} alt="" className="v-logo-clair" style={{ height: 34, width: 'auto' }} draggable={false} />
          <img src={logoSombre} alt="" className="v-logo-sombre" style={{ height: 34, width: 'auto' }} draggable={false} />
          <span className="border-l border-border pl-3">
            <span className="block font-mono text-xs text-fg-subtle">JUR-EXEMPLE</span>
          </span>
        </span>
        <div className="flex min-w-0 flex-1 justify-center">
          <span className="flex h-9 w-full max-w-md items-center justify-start gap-2 rounded-lg border border-border-input bg-bg px-3 text-sm text-fg-subtle">
            <Search className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1 truncate text-left">Rechercher un dossier, un ticket…</span>
            <Touches touches={['Ctrl', 'K']} combinaison />
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <BoutonEntete pastille={{ n: 2, ton: 'warning' }}>
            <Calendar className="h-5 w-5" aria-hidden="true" />
          </BoutonEntete>
          {theme ?? (
            <BoutonEntete>
              <Monitor className="h-5 w-5" aria-hidden="true" />
            </BoutonEntete>
          )}
          <BoutonEntete pastille={cloche > 0 ? { n: cloche, ton: 'danger' } : undefined}>
            <Bell className="h-5 w-5" aria-hidden="true" />
          </BoutonEntete>
          <span className="mx-1 h-6 w-px bg-border" />
          <span className="flex h-10 items-center gap-2 px-1.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-accent/30 bg-accent-soft text-xs font-bold text-accent">N</span>
            <span className="max-w-[12rem] text-left">
              <span className="block truncate text-sm font-medium leading-tight text-fg">Nadia Exemple</span>
              <span className="block text-xs leading-tight text-fg-subtle">Employé</span>
            </span>
            <ChevronDown className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
          </span>
        </div>
      </div>

      {/* Menu lateral (MenuLateral), 240 px */}
      <div className="absolute bottom-0 left-0 top-14 z-20 flex w-60 flex-col bg-nav-bg text-nav-fg" data-menu-demo="">
        <div className="min-h-0 flex-1 px-2 py-4">
          {MENU_EMPLOYE.map((g) => (
            <div key={g.titre} className="mb-4 last:mb-0" data-groupe-demo={g.titre}>
              <p className="mb-1 px-3 text-[0.6875rem] font-semibold uppercase tracking-wider text-nav-fg">{g.titre}</p>
              <ul className="space-y-0.5">
                {g.entrees.map((e) => {
                  const Icone = e.icone;
                  const est = e.libelle === actif;
                  return (
                    <li key={e.libelle}>
                      <span
                        className={cn(
                          'relative flex h-9 items-center gap-3 rounded-lg px-3 text-sm',
                          est
                            ? 'bg-nav-bg-active font-semibold text-nav-active before:absolute before:inset-y-1.5 before:left-0 before:w-[3px] before:rounded-full before:bg-nav-active'
                            : 'text-nav-fg',
                        )}
                      >
                        <Icone className="h-[1.125rem] w-[1.125rem] shrink-0" aria-hidden="true" />
                        <span className="min-w-0 flex-1 truncate" data-entree-demo="" data-actif={est ? '' : undefined}>
                          {e.libelle}
                        </span>
                      </span>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>
        <div className="border-t border-nav-border p-2">
          <span className="flex h-9 w-full items-center gap-3 rounded-lg px-3 text-sm text-nav-fg">
            <PanelLeftClose className="h-[1.125rem] w-[1.125rem]" aria-hidden="true" />
            Réduire le menu
          </span>
        </div>
      </div>

      {/* Page (AppShell : reserve de JURI, main max-w-7xl px-8 py-6) */}
      <div className="absolute bottom-0 left-60 right-0 top-14 overflow-hidden">
        <div className="v-demo-defile @container/page pr-16" style={{ transform: `translateY(${-defilement}px)` }}>
          <div className="mx-auto w-full max-w-7xl px-8 py-6">{children}</div>
        </div>
      </div>

      {/* JURI, au bord droit (Juri.tsx : fixed bottom-6 right-0, a moitie sorti) */}
      {juri ?? (
        <div className="absolute bottom-6 right-0 z-30 mr-1 flex w-14 translate-x-[55%] flex-col items-center">
          <JuriRobot taille={56} className="drop-shadow-sm" />
        </div>
      )}
    </div>
  );
}
