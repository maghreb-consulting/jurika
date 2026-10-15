import { Calendar, FolderOpen } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { Message } from '../ui/Message';
import type { Ticket } from '../../types/ticket';
import { TICKET_TYPE_LABELS } from '../../types/ticket';

/**
 * Lot L6b (E5) : elements de l'en-tete commun des parcours (fil d'Ariane, etat, ligne
 * d'informations du ticket, message de consultation du superviseur).
 */
export function EtatTicketParcours({ ticket, forme }: { ticket: Ticket; forme?: string }) {
  return (
    <>
      <Badge variant="info">{TICKET_TYPE_LABELS[ticket.type] ?? ticket.type}</Badge>
      {forme && <Badge variant="neutral">{forme}</Badge>}
    </>
  );
}

export function MetaTicketParcours({ ticket }: { ticket: Ticket }) {
  return (
    <span className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-fg-subtle">
      <span className="font-mono">{ticket.reference}</span>
      {ticket.dossierId && (
        <span className="inline-flex items-center gap-1">
          <FolderOpen className="h-3.5 w-3.5" aria-hidden="true" /> Rattaché à un dossier
        </span>
      )}
      {ticket.deadline && (
        <span className="inline-flex items-center gap-1">
          <Calendar className="h-3.5 w-3.5" aria-hidden="true" /> Échéance :{' '}
          {new Date(ticket.deadline).toLocaleDateString('fr-FR')}
        </span>
      )}
    </span>
  );
}

/** Le superviseur suit les parcours ; seul l'employe charge du ticket les fait avancer. */
export function MessageConsultationSuperviseur() {
  return (
    <Message ton="info" titre="Consultation">
      En tant que superviseur, vous suivez ce parcours sans le modifier : les saisies, la
      validation des étapes, le dépôt des pièces et l’annulation sont réservés à l’employé chargé
      du ticket.
    </Message>
  );
}
