import type { Ticket } from '../../types/ticket';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';

interface Props {
  ticket: Ticket;
  onClose: () => void;
  onConfirm: (comment: string) => Promise<void>;
}

/**
 * Dialog d'annulation de ticket. Depuis 2026-06-25, l'annulation est une
 * transition sensible reversible (un ticket annule peut etre repris ou cloture) :
 * on reutilise le dialog generalise {@link SensitiveTransitionDialog} (motif >= 10
 * caracteres + confirmation explicite). Annulation possible depuis NOUVEAU,
 * EN_COURS et CLOTURE.
 */
export function CancelTicketDialog({ ticket, onClose, onConfirm }: Props) {
  return (
    <SensitiveTransitionDialog
      title="Annuler le ticket"
      intro="Vous etes sur le point d'annuler le ticket"
      reference={ticket.reference}
      minLength={10}
      confirmLabel="Annuler le ticket"
      variant="danger"
      onClose={onClose}
      onConfirm={onConfirm}
    />
  );
}
