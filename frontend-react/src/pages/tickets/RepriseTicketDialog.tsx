import { useEffect, useState } from 'react';
import type { Ticket } from '../../types/ticket';
import { STATUT_LABELS } from '../../types/ticket';
import { annulationService, type EtatReprise } from '../../services/annulation.service';
import { extractError } from '../../lib/api';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';
import { Button } from '../../components/ui/Button';

interface Props {
  ticket: Ticket;
  onClose: () => void;
  /** Transition vers le statut anterieur annonce par le serveur, avec le motif saisi. */
  onConfirm: (cible: Ticket['statut'], comment: string) => Promise<void>;
}

/**
 * Lot L4a (E6, RG-TKT-05) : reprise d'un ticket annule, commune au tableau, a la fiche
 * du ticket et a tous les parcours.
 *
 * Le ticket retrouve son statut ANTERIEUR, et lui seul : l'ecran l'annonce avant la
 * confirmation. Si un retrait a ete effectue aupres d'une administration (ou si le
 * statut anterieur est inconnu), la reprise est impossible : l'ecran dit pourquoi et
 * qu'il faut ouvrir un nouveau ticket. Avant : la reprise visait toujours la generation
 * des documents, quel que soit le statut atteint.
 */
export function RepriseTicketDialog({ ticket, onClose, onConfirm }: Props) {
  const [etat, setEtat] = useState<EtatReprise | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    annulationService
      .reprise(ticket.id)
      .then((e) => actif && setEtat(e))
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [ticket.id]);

  if (!etat || !etat.possible || !etat.statutAnterieur) {
    return (
      <div role="dialog" aria-modal="true" aria-label="Reprendre le ticket"
        className="fixed inset-0 z-50 flex items-center justify-center bg-fg/40 px-4">
        <div className="w-full max-w-md space-y-3 rounded-2xl bg-bg-raised p-6 text-sm text-fg shadow-2xl">
          <h2 className="text-lg font-semibold text-fg">Reprendre le ticket {ticket.reference}</h2>
          {!etat && !erreur && <p className="text-fg-muted">Vérification de la reprise…</p>}
          {erreur && <p className="text-danger">{erreur}</p>}
          {etat && (
            <p data-testid="reprise-refus" className="rounded-lg border border-danger/40 bg-danger/10 p-3">
              {etat.motif ?? 'La reprise est impossible.'}
            </p>
          )}
          <div className="flex justify-end">
            <Button variant="secondary" onClick={onClose}>Fermer</Button>
          </div>
        </div>
      </div>
    );
  }

  const cible = etat.statutAnterieur;
  return (
    <SensitiveTransitionDialog
      title="Reprendre le ticket"
      intro={`Le ticket retrouvera son statut antérieur, « ${STATUT_LABELS[cible]} », et le parcours reprendra là où il s’était arrêté. Vous allez reprendre le ticket`}
      reference={ticket.reference}
      minLength={1}
      confirmLabel={`Reprendre au statut « ${STATUT_LABELS[cible]} »`}
      variant="primary"
      onClose={onClose}
      onConfirm={(comment) => onConfirm(cible, comment)}
    />
  );
}
