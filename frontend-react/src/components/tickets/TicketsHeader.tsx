import { LayoutGrid, List } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * Lot L6b (E7) : l'en-tete de la liste des tickets passe par <Page> (titre, action
 * principale, fil). Restent ici le titre selon le role, le resume chiffre et le choix
 * de l'affichage (par statut ou en tableau), place dans les actions secondaires.
 */
type Vue = 'kanban' | 'table';

const VUES: { id: Vue; libelle: string; icone: typeof LayoutGrid }[] = [
  { id: 'kanban', libelle: 'Par statut', icone: LayoutGrid },
  { id: 'table', libelle: 'Tableau', icone: List },
];

/**
 * Choix de l'affichage : deux boutons a bascule (aria-pressed). L'etat actif porte la
 * teinte or douce et le texte le dit (bouton enfonce), pas la seule couleur.
 */
export function ChoixVueTickets({ view, onViewChange }: { view: Vue; onViewChange: (v: Vue) => void }) {
  return (
    <div role="group" aria-label="Affichage des tickets" className="inline-flex rounded-lg border border-border bg-bg-raised p-0.5">
      {VUES.map(({ id, libelle, icone: Icone }) => {
        const actif = view === id;
        return (
          <button
            key={id}
            type="button"
            aria-pressed={actif}
            onClick={() => onViewChange(id)}
            className={cn(
              'inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-sm font-medium transition-colors duration-150',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
              actif ? 'bg-accent-soft text-accent' : 'text-fg-muted hover:bg-bg-overlay hover:text-fg',
            )}
          >
            <Icone className="h-4 w-4" aria-hidden="true" />
            {libelle}
          </button>
        );
      })}
    </div>
  );
}
