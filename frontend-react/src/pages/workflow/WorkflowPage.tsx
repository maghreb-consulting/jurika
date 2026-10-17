import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ErreurParcours, SqueletteParcours } from '../../components/workflow/EtatsParcours';
import { extractError } from '../../lib/api';
import { ticketService } from '../../services/ticket.service';
import type { Ticket } from '../../types/ticket';
import { CreationSarlWorkflowPage } from './CreationSarlWorkflowPage';
import { ImportWorkflowPage } from './ImportWorkflowPage';
import { ModificationWorkflowPage } from './ModificationWorkflowPage';
import { DissolutionWorkflowPage } from './DissolutionWorkflowPage';
import { LiquidationWorkflowPage } from './LiquidationWorkflowPage';
import { SuccursaleMaWorkflowPage } from './SuccursaleMaWorkflowPage';
import { SuccursaleEtrWorkflowPage } from './SuccursaleEtrWorkflowPage';
import { FermetureSuccursaleWorkflowPage } from './FermetureSuccursaleWorkflowPage';
import { PvAgoWorkflowPage } from './PvAgoWorkflowPage';
import { AutreWorkflowPage } from './AutreWorkflowPage';

/**
 * Dispatcher — loads the ticket once and renders the dedicated workflow UI
 * matching its type. Each dedicated page re-fetches its own state via
 * useWorkflow() to keep concerns isolated.
 */
export function WorkflowPage() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ticketId) return;
    (async () => {
      try {
        const t = await ticketService.get(ticketId);
        setTicket(t);
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setLoading(false);
      }
    })();
  }, [ticketId]);

  if (loading) {
    return <SqueletteParcours />;
  }

  if (!ticket) {
    return (
      <ErreurParcours>
        {error ?? 'Ticket introuvable : il a peut-être été supprimé, ou vous n’y avez pas accès.'}
      </ErreurParcours>
    );
  }

  switch (ticket.type) {
    case 'CREATION':
      return <CreationSarlWorkflowPage />;
    case 'IMPORT':
      return <ImportWorkflowPage />;
    case 'MODIFICATION':
      return <ModificationWorkflowPage />;
    case 'DISSOLUTION':
      return <DissolutionWorkflowPage />;
    case 'LIQUIDATION':
      return <LiquidationWorkflowPage />;
    case 'SUCCURSALE_MA':
      return <SuccursaleMaWorkflowPage />;
    case 'SUCCURSALE_ETR':
      return <SuccursaleEtrWorkflowPage />;
    case 'FERMETURE_SUCCURSALE':
      return <FermetureSuccursaleWorkflowPage />;
    case 'PV_AGO':
      return <PvAgoWorkflowPage />;
    default:
      return <AutreWorkflowPage />;
  }
}
