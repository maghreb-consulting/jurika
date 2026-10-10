import { useId, useState, type ReactNode } from 'react';
import { Info, X } from 'lucide-react';

/**
 * Aide integree (cahier des charges, section 15.4 ; regle de CLAUDE.md du 2026-10-10) :
 * chaque ecran nouveau ou modifie recoit ses aides contextuelles.
 *
 * - `InfoBulle` : une icone focalisable ; le texte s'affiche au survol, au focus
 *   clavier ou au clic (role="tooltip", relie par aria-describedby).
 * - `TexteAide` : un encadre d'aide masquable, puis reaffichable ; le choix est
 *   memorise dans le navigateur (simple commodite : sans stockage, l'aide s'affiche).
 */

export function InfoBulle({ texte, libelle = 'Aide' }: { texte: string; libelle?: string }) {
  const id = useId();
  const [ouverte, setOuverte] = useState(false);
  return (
    <span className="relative inline-flex align-middle">
      <button
        type="button"
        aria-label={libelle}
        aria-describedby={ouverte ? id : undefined}
        aria-expanded={ouverte}
        onMouseEnter={() => setOuverte(true)}
        onMouseLeave={() => setOuverte(false)}
        onFocus={() => setOuverte(true)}
        onBlur={() => setOuverte(false)}
        onClick={() => setOuverte((o) => !o)}
        className="rounded-full p-0.5 text-fg-subtle hover:text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        <Info className="h-4 w-4" aria-hidden="true" />
      </button>
      {ouverte && (
        <span
          role="tooltip"
          id={id}
          className="absolute left-6 top-0 z-30 w-72 rounded-md border border-border bg-bg-raised p-2 text-xs font-normal leading-relaxed text-fg shadow-lg"
        >
          {texte}
        </span>
      )}
    </span>
  );
}

function lireMasquee(cle: string): boolean {
  try {
    return window.localStorage.getItem(cle) === '1';
  } catch {
    return false;
  }
}

function ecrireMasquee(cle: string, masquee: boolean): void {
  try {
    if (masquee) window.localStorage.setItem(cle, '1');
    else window.localStorage.removeItem(cle);
  } catch {
    // Stockage indisponible (navigation privee...) : le choix vaut pour la session d'ecran.
  }
}

export function TexteAide({ cle, titre, children }: { cle: string; titre: string; children: ReactNode }) {
  const stockage = `jurika.aide.${cle}.masquee`;
  const [masquee, setMasquee] = useState(() => lireMasquee(stockage));

  if (masquee) {
    return (
      <button
        type="button"
        onClick={() => {
          setMasquee(false);
          ecrireMasquee(stockage, false);
        }}
        className="inline-flex items-center gap-1 text-xs text-fg-subtle underline hover:text-fg"
      >
        <Info className="h-3.5 w-3.5" aria-hidden="true" />
        Afficher l’aide
      </button>
    );
  }

  return (
    <div role="note" aria-label={titre} className="relative rounded-md border border-border bg-bg-overlay p-3 pr-8 text-xs leading-relaxed text-fg-muted">
      <p className="mb-1 flex items-center gap-1 font-medium text-fg">
        <Info className="h-3.5 w-3.5" aria-hidden="true" />
        {titre}
      </p>
      {children}
      <button
        type="button"
        aria-label="Masquer l’aide"
        title="Masquer l’aide"
        onClick={() => {
          setMasquee(true);
          ecrireMasquee(stockage, true);
        }}
        className="absolute right-2 top-2 rounded p-0.5 text-fg-subtle hover:text-fg"
      >
        <X className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </div>
  );
}
