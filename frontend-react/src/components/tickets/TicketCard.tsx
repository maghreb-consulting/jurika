import { useState, type DragEvent } from 'react';
import { AlertTriangle, ArrowRightLeft, Calendar } from 'lucide-react';
import { Badge, type BadgeVariant } from '../ui/Badge';
import { cn } from '../../lib/utils';
import { TICKET_TYPE_LABELS, responsableLabel } from '../../types/ticket';
import type { Ticket } from '../../types/ticket';

interface Props {
  ticket: Ticket;
  onClick: () => void;
  onDragStart?: (e: DragEvent<HTMLDivElement>) => void;
}

const PRIORITE: Record<Ticket['priorite'], { variante: BadgeVariant; libelle: string }> = {
  BASSE: { variante: 'neutral', libelle: 'Basse' },
  NORMALE: { variante: 'info', libelle: 'Normale' },
  HAUTE: { variante: 'warning', libelle: 'Haute' },
  URGENTE: { variante: 'danger', libelle: 'Urgente' },
};

/**
 * Carte d'un ticket dans la vue par statut : type et reference, titre, priorite,
 * responsable, echeance. Lot L6b (E7) : badges de la bibliotheque ; l'or n'est plus
 * employe pour les categories ; un ticket en retard le DIT (icone et texte), le filet
 * rouge ne fait que le souligner.
 */
export function TicketCard({ ticket, onClick, onDragStart }: Props) {
  // Instant de reference fige au montage de la carte (rendu pur).
  const [maintenant] = useState(() => Date.now());
  const prio = PRIORITE[ticket.priorite];

  // Responsable (assigneId resolu par le serveur) : initiales + nom, jamais l'identifiant.
  const responsable = responsableLabel(ticket);
  const assigned = responsable !== 'Non assigné';
  const initials = assigned
    ? responsable
        .split(/\s+/)
        .map((p) => p.charAt(0))
        .join('')
        .slice(0, 2)
        .toUpperCase()
    : '?';

  const overdue =
    !!ticket.deadline &&
    new Date(ticket.deadline).getTime() < maintenant &&
    ticket.statut !== 'CLOTURE_DOSSIER' &&
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
      className={cn(
        'min-w-0 rounded-lg border border-border bg-bg-raised p-3 shadow-card transition-shadow duration-150 hover:shadow-card-lifted',
        onDragStart ? 'cursor-grab active:cursor-grabbing' : 'cursor-pointer',
        overdue && 'border-t-4 border-t-danger',
      )}
    >
      <div className="mb-2 flex flex-wrap items-center gap-1.5">
        <Badge variant="neutral" className="max-w-full truncate">
          {TICKET_TYPE_LABELS[ticket.type]}
        </Badge>
        <Badge variant={prio.variante} title={`Priorité ${prio.libelle.toLowerCase()}`}>
          {prio.libelle}
        </Badge>
        {ticket.transferred && (
          <Badge variant="info" title="Ticket repris à la suite d’un transfert de dossier">
            <ArrowRightLeft className="h-3 w-3" aria-hidden="true" /> Transféré
          </Badge>
        )}
      </div>

      <p className="line-clamp-2 break-words text-sm font-semibold text-fg">{ticket.titre}</p>
      <p className="mt-0.5 font-mono text-[11px] text-fg-subtle">{ticket.reference}</p>

      {ticket.description && <p className="mt-1 line-clamp-2 break-words text-xs text-fg-muted">{ticket.description}</p>}

      {/* Responsable du ticket : initiales + nom (jamais l'identifiant). */}
      <div className="mt-2.5 flex min-w-0 items-center gap-1.5 text-xs" title={`Responsable : ${assigned ? responsable : 'non assigné'}`}>
        <span
          aria-hidden="true"
          className={cn(
            'inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[9px] font-bold',
            assigned ? 'bg-info-soft text-info' : 'bg-bg-overlay text-fg-subtle',
          )}
        >
          {initials}
        </span>
        <span className={cn('truncate', assigned ? 'text-fg-muted' : 'italic text-fg-subtle')}>
          {assigned ? responsable : 'Non assigné'}
        </span>
      </div>

      <div className="mt-2 flex flex-wrap items-center justify-between gap-x-2 gap-y-1 border-t border-border pt-2 text-xs">
        <span className={cn('flex flex-wrap items-center gap-x-1', overdue ? 'font-medium text-danger' : 'text-fg-muted')}>
          {overdue ? <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" /> : <Calendar className="h-3.5 w-3.5" aria-hidden="true" />}
          {ticket.deadline ? new Date(ticket.deadline).toLocaleDateString('fr-FR') : 'Sans échéance'}
          {overdue && <span>(en retard)</span>}
        </span>
        <span className="text-[11px] text-fg-subtle">Créé le {new Date(ticket.createdAt).toLocaleDateString('fr-FR')}</span>
      </div>
    </div>
  );
}
