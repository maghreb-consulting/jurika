import { LayoutGrid, List, Plus } from 'lucide-react';
import type { Role } from '../../types/auth';

interface Props {
  view: 'kanban' | 'table';
  onViewChange: (view: 'kanban' | 'table') => void;
  onNewTicket: () => void;
  role: Role | null;
  total: number;
  enCours: number;
  nouveaux: number;
}

/**
 * En-tete de la page Tickets : breadcrumb + titre + stats rapides + toggle vue + bouton creer.
 * Bouton "Nouveau ticket" masque pour SUPERVISEUR/SUPER_ADMIN (lecture seule).
 */
export function TicketsHeader({
  view,
  onViewChange,
  onNewTicket,
  role,
  total,
  enCours,
  nouveaux,
}: Props) {
  const isSupervisor = role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
  const canCreate = role === 'EMPLOYE';

  return (
    <div className="mb-2">
      <div className="text-sm text-fg-subtle mb-2">
        Dashboard /{' '}
        <span className="text-fg">
          {isSupervisor ? 'Tous les Tickets' : 'Mes Tickets'}
        </span>
      </div>

      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl md:text-3xl font-bold text-fg">
            {isSupervisor ? 'Tous les Tickets' : 'Mes Tickets'}
          </h1>
          {isSupervisor && (
            <span className="px-2 py-1 bg-accent/10 text-accent rounded text-xs font-bold">
              Vue globale
            </span>
          )}
          <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-medium text-fg-muted">
            {total}
          </span>
          <span className="text-xs text-fg-subtle hidden md:inline">
            — {enCours} en cours, {nouveaux} nouveaux
          </span>
        </div>

        <div className="flex items-center gap-3">
          <div className="inline-flex bg-bg-overlay p-1 rounded-lg border border-border">
            <button
              type="button"
              onClick={() => onViewChange('kanban')}
              className={`px-3 md:px-4 py-1.5 md:py-2 rounded-md text-sm transition flex items-center gap-2 ${
                view === 'kanban'
                  ? 'bg-accent text-bg-raised shadow-sm'
                  : 'text-fg-subtle hover:text-fg'
              }`}
            >
              <LayoutGrid className="w-4 h-4" />
              Kanban
            </button>
            <button
              type="button"
              onClick={() => onViewChange('table')}
              className={`px-3 md:px-4 py-1.5 md:py-2 rounded-md text-sm transition flex items-center gap-2 ${
                view === 'table'
                  ? 'bg-accent text-bg-raised shadow-sm'
                  : 'text-fg-subtle hover:text-fg'
              }`}
            >
              <List className="w-4 h-4" />
              Tableau
            </button>
          </div>

          {canCreate && (
            <button
              type="button"
              onClick={onNewTicket}
              className="px-4 md:px-5 h-10 bg-accent text-bg-raised rounded-lg hover:bg-accent-hover transition flex items-center gap-2 font-medium text-sm"
            >
              <Plus className="w-4 h-4" />
              <span className="hidden sm:inline">Nouveau ticket</span>
              <span className="sm:hidden">Nouveau</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
