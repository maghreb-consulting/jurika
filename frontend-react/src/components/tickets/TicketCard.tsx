import type { DragEvent } from 'react';
import { Calendar, ArrowRightLeft } from 'lucide-react';
import { TICKET_TYPE_LABELS, responsableLabel } from '../../types/ticket';
import type { Ticket } from '../../types/ticket';

interface Props {
  ticket: Ticket;
  onClick: () => void;
  onDragStart?: (e: DragEvent<HTMLDivElement>) => void;
}

const TYPE_COLORS: Record<
  string,
  { bg: string; text: string }
> = {
  CREATION: { bg: 'bg-accent/10', text: 'text-accent' },
  IMPORT: { bg: 'bg-bg-overlay', text: 'text-accent' },
  MODIFICATION: { bg: 'bg-success/10', text: 'text-success' },
  DISSOLUTION: { bg: 'bg-danger/10', text: 'text-danger' },
  LIQUIDATION: { bg: 'bg-warning/10', text: 'text-warning' },
  SUCCURSALE_MA: { bg: 'bg-accent/10', text: 'text-accent' },
  SUCCURSALE_ETR: { bg: 'bg-accent/10', text: 'text-accent' },
  FERMETURE_SUCCURSALE: { bg: 'bg-danger/10', text: 'text-danger' },
  PV_AGO: { bg: 'bg-warning/10', text: 'text-warning' },
};

const PRIO_COLORS: Record<
  Ticket['priorite'],
  { bg: string; text: string; label: string }
> = {
  BASSE: { bg: 'bg-bg-overlay', text: 'text-fg-muted', label: 'Basse' },
  NORMALE: { bg: 'bg-accent/10', text: 'text-accent', label: 'Normale' },
  HAUTE: { bg: 'bg-warning/10', text: 'text-warning', label: 'Haute' },
  URGENTE: { bg: 'bg-danger/10', text: 'text-danger', label: 'Urgente' },
};

/**
 * Carte ticket : badge type + reference, titre, priorite, deadline.
 * Utilisee en Kanban (drag & drop) et en vue compacte.
 *
 * NOTE : la maquette montre progression "Etape X/Y" et assignee avatar.
 * Backend ne renvoie pas encore ces donnees -> placeholders supprimes pour rester fidele au modele.
 */
export function TicketCard({ ticket, onClick, onDragStart }: Props) {
  const typeColor = TYPE_COLORS[ticket.type] ?? {
    bg: 'bg-bg-overlay',
    text: 'text-fg-subtle',
  };
  const prio = PRIO_COLORS[ticket.priorite];

  // Responsable (assigneId resolu backend) : ligne discrete + avatar initiales.
  // Le superviseur voit ainsi qui porte chaque ticket ; "Non assigne" sinon.
  const responsable = responsableLabel(ticket);
  const assigned = responsable !== 'Non assigne';
  const initials = assigned
    ? responsable
        .split(/\s+/)
        .map((p) => p.charAt(0))
        .join('')
        .slice(0, 2)
        .toUpperCase()
    : '?';

  const overdue =
    ticket.deadline &&
    new Date(ticket.deadline).getTime() < Date.now() &&
    ticket.statut !== 'CLOTURE' &&
    ticket.statut !== 'ANNULE';

  return (
    <div
      draggable={!!onDragStart}
      onDragStart={onDragStart}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }}
      className={`bg-bg-overlay rounded-xl p-3 md:p-4 shadow-card border border-border hover:shadow-card-lifted hover:border-accent/50 transition cursor-grab active:cursor-grabbing ${
        overdue ? 'border-t-4 border-t-danger' : ''
      }`}
    >
      {/* Sprint 12.5 T8 -- card repeinte navy, drag affordance preservee.
          TYPE_COLORS / PRIO_COLORS gardent leurs tints categoriels (lecture
          OK comme badges clairs sur fond navy raised). */}
      <div className="flex items-start justify-between mb-2 gap-2">
        <div className="flex items-center gap-2 flex-wrap">
          <span
            className={`px-2 py-0.5 ${typeColor.bg} ${typeColor.text} rounded text-[10px] font-semibold uppercase tracking-wide`}
          >
            {TICKET_TYPE_LABELS[ticket.type]}
          </span>
          <span className="text-[10px] font-mono text-fg-subtle">
            {ticket.reference}
          </span>
          {ticket.transferred && (
            <span
              className="flex items-center gap-1 rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-accent"
              title="Ticket repris suite a un transfert de dossier"
            >
              <ArrowRightLeft className="h-2.5 w-2.5" /> Transfere
            </span>
          )}
        </div>
        <span
          className={`px-2 py-0.5 ${prio.bg} ${prio.text} rounded-full text-[10px] font-bold`}
        >
          {prio.label}
        </span>
      </div>

      <p className="text-sm font-semibold text-fg line-clamp-2">
        {ticket.titre}
      </p>

      {ticket.description && (
        <p className="mt-1 text-xs text-fg-muted line-clamp-2">
          {ticket.description}
        </p>
      )}

      {/* Responsable du ticket : avatar initiales + nom (jamais l'UUID). */}
      <div className="mt-3 flex items-center gap-1.5 text-xs" title={`Responsable : ${responsable}`}>
        <span
          className={`inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[9px] font-bold ${
            assigned ? 'bg-accent/15 text-accent' : 'bg-bg-raised text-fg-subtle'
          }`}
        >
          {initials}
        </span>
        <span className={`truncate ${assigned ? 'text-fg-muted' : 'text-fg-subtle italic'}`}>
          {responsable}
        </span>
      </div>

      <div className="mt-2 pt-2 border-t border-border flex items-center justify-between text-xs">
        <div className="flex items-center gap-1.5 text-fg-subtle">
          <Calendar className="w-3.5 h-3.5" />
          <span
            className={overdue ? 'text-danger font-medium' : ''}
          >
            {ticket.deadline
              ? new Date(ticket.deadline).toLocaleDateString('fr-FR')
              : '—'}
          </span>
        </div>
        <span className="text-[10px] text-fg-subtle">
          Cree le{' '}
          {new Date(ticket.createdAt).toLocaleDateString('fr-FR')}
        </span>
      </div>
    </div>
  );
}
