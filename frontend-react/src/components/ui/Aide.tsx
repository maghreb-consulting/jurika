import { useId, useState, type ReactNode } from 'react';
import { Info, X } from 'lucide-react';
import { useAideMasquee, useAidesStore } from '../../store/aidesStore';

/**
 * Aide integree (cahier des charges, section 15.4 ; regle de CLAUDE.md du 2026-10-10) :
 * chaque ecran nouveau ou modifie recoit ses aides contextuelles.
 *
 * - `InfoBulle` : une icone focalisable ; le texte s'affiche au survol, au focus
 *   clavier ou au clic (role="tooltip", relie par aria-describedby).
 * - `TexteAide` : un encadre d'aide masquable, puis reaffichable ; le choix est
 *   memorise cote serveur, par utilisateur (lot L6, `store/aidesStore`). Le rendu
 *   lit le magasin, hydrate une fois a la connexion : aucun appel reseau ; sans
 *   serveur, l'aide s'affiche.
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
        className="inline-flex h-6 w-6 items-center justify-center rounded-full text-fg-subtle transition-colors duration-150 hover:bg-bg-overlay hover:text-info"
      >
        <Info className="h-4 w-4" aria-hidden="true" />
      </button>
      {ouverte && (
        <span
          role="tooltip"
          id={id}
          className="absolute left-7 top-0 z-30 w-72 max-w-[calc(100vw-3rem)] rounded-lg border border-border bg-bg-raised px-3 py-2 text-xs font-normal normal-case leading-relaxed tracking-normal text-fg shadow-card-lifted"
        >
          {texte}
        </span>
      )}
    </span>
  );
}

export function TexteAide({ cle, titre, children }: { cle: string; titre: string; children: ReactNode }) {
  const masquee = useAideMasquee(cle);
  const masquer = useAidesStore((s) => s.masquer);
  const reafficher = useAidesStore((s) => s.reafficher);

  if (masquee) {
    return (
      <button
        type="button"
        onClick={() => reafficher(cle)}
        className="inline-flex items-center gap-1 rounded-sm text-xs text-fg-subtle underline underline-offset-2 hover:text-fg"
      >
        <Info className="h-3.5 w-3.5" aria-hidden="true" />
        Afficher l’aide
      </button>
    );
  }

  return (
    <div
      role="note"
      aria-label={titre}
      className="relative rounded-lg border border-border border-l-4 border-l-info bg-info-soft py-3 pl-4 pr-10 text-sm leading-relaxed text-fg"
    >
      <p className="mb-1 flex items-center gap-1.5 font-semibold text-fg">
        <Info className="h-4 w-4 text-info" aria-hidden="true" />
        {titre}
      </p>
      {children}
      <button
        type="button"
        aria-label="Masquer l’aide"
        title="Masquer l’aide"
        onClick={() => masquer(cle)}
        className="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-md text-fg-muted hover:bg-bg-raised hover:text-fg"
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
