import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { CalendarPage } from '../CalendarPage';
import type { Ticket } from '../../../types/ticket';

// 2026-06-25 — Calendrier tickets : clic sur un ticket -> TicketDetailDrawer.
// On stub TicketDetailDrawer pour isoler le wiring du calendrier de son arbre de deps.

const list = vi.fn();

vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: (...a: unknown[]) => list(...a) },
}));
vi.mock('../../../services/deadline.service', () => ({
  deadlineService: { list: vi.fn().mockResolvedValue({ items: [], total: 0 }) },
}));
vi.mock('../../tickets/TicketDetailDrawer', () => ({
  TicketDetailDrawer: ({ ticketId, onClose }: { ticketId: string; onClose: () => void }) => (
    <div data-testid="ticket-detail-drawer" data-ticket-id={ticketId}>
      <button type="button" onClick={onClose}>
        close
      </button>
    </div>
  ),
}));

function mkTicket(p: Partial<Ticket>): Ticket {
  return {
    id: p.id ?? 't1',
    workspaceId: 'w1',
    reference: p.reference ?? 'TCK-001',
    titre: p.titre ?? 'Constituer SARL ACME',
    type: 'CREATION',
    statut: p.statut ?? 'NOUVEAU',
    priorite: p.priorite ?? 'URGENTE',
    dossierId: null,
    assigneId: null,
    creeParId: 'u1',
    description: null,
    deadline: p.deadline ?? null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-06-01T00:00:00Z',
  };
}

describe('CalendarPage (vue tickets)', () => {
  beforeEach(() => {
    list.mockReset();
  });

  it('clic sur un ticket du calendrier ouvre le TicketDetailDrawer avec le bon id', async () => {
    // Echeance = aujourd'hui -> tombe dans la grille du mois courant (cursor par defaut).
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    list.mockResolvedValue({
      items: [mkTicket({ id: 'tk-42', deadline: today.toISOString() })],
      total: 1,
    });

    const user = userEvent.setup();
    render(<CalendarPage />);

    // La pastille du ticket est rendue dans sa cellule jour.
    const chip = await screen.findByTestId('cal-ticket-chip');
    expect(chip).toBeInTheDocument();

    // Le drawer n'est pas encore monte.
    expect(screen.queryByTestId('ticket-detail-drawer')).not.toBeInTheDocument();

    await user.click(chip);

    const drawer = await screen.findByTestId('ticket-detail-drawer');
    expect(drawer).toHaveAttribute('data-ticket-id', 'tk-42');
  });

  it('source par defaut = tickets : appelle ticketService.list (pas la vue echeances)', async () => {
    list.mockResolvedValue({ items: [], total: 0 });
    render(<CalendarPage />);
    await waitFor(() => expect(list).toHaveBeenCalledWith({ limit: 500 }));
  });
});
