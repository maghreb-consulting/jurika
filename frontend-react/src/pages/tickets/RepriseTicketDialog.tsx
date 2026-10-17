import { useEffect, useState } from 'react';
import type { Ticket } from '../../types/ticket';
import { STATUT_LABELS } from '../../types/ticket';
import { annulationService, type EtatReprise } from '../../services/annulation.service';
import { extractError } from '../../lib/api';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Message } from '../../components/ui/Message';
import { SqueletteZone } from '../../components/ui/LoadingSkeleton';

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
      <Modal
        open
        onOpenChange={(o) => !o && onClose()}
        title={`Reprendre le ticket ${ticket.reference}`}
        size="md"
        footer={
          <div className="flex justify-end">
            <Button type="button" variant="secondary" onClick={onClose}>Fermer</Button>
          </div>
        }
      >
        <div className="space-y-3 text-sm text-fg">
          {!etat && !erreur && (
            <SqueletteZone libelle="Vérification de la reprise…">
              <p className="text-fg-muted">Vérification de la reprise…</p>
            </SqueletteZone>
          )}
          {erreur && <Message ton="erreur">{erreur}</Message>}
          {etat && (
            <div data-testid="reprise-refus">
              <Message ton="erreur" titre="Reprise impossible">
                {etat.motif ?? 'La reprise est impossible.'}
              </Message>
            </div>
          )}
        </div>
      </Modal>
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
