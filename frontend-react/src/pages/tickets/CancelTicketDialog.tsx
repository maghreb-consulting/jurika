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
 *
 * Lot L4a (E6, RG-TKT-03 a 05) : dialogue commun a tous les parcours (tableau, fiche du
 * ticket, pages de workflow) ; il annonce ce qui suit l'annulation (documents, reprise).
 */
export function CancelTicketDialog({ ticket, onClose, onConfirm }: Props) {
  return (
    <SensitiveTransitionDialog
      title="Annuler le ticket"
      intro={
        "L'annulation conserve le statut atteint. Depuis la fiche du ticket, vous préparerez ensuite "
        + "la note d'annulation, une lettre de retrait pour chaque administration où un dépôt a été fait, "
        + "et le bordereau de restitution des pièces. Le ticket pourra être repris à son statut antérieur "
        + "tant qu'aucun retrait n'a été effectué. Vous allez annuler le ticket"
      }
      reference={ticket.reference}
      minLength={10}
      confirmLabel="Annuler le ticket"
      variant="danger"
      onClose={onClose}
      onConfirm={onConfirm}
    />
  );
}
