import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { CopilotePanel } from '../CopilotePanel';
import type { AgentBriefing } from '../../../types/agent';

// Panneau Copilote : le bouton Rafraichir doit etre fiable — sur echec, afficher
// une erreur discrete SANS effacer les donnees precedentes ; au succes, remplacer.
const getMyBriefing = vi.fn();
const refresh = vi.fn();
const markSeen = vi.fn();

vi.mock('../../../services/agent.service', () => ({
  agentService: {
    getMyBriefing: (...a: unknown[]) => getMyBriefing(...a),
    refresh: (...a: unknown[]) => refresh(...a),
    markSeen: (...a: unknown[]) => markSeen(...a),
  },
}));

const briefing = (over: Partial<AgentBriefing> = {}): AgentBriefing => ({
  id: 'b1',
  workspaceId: 'ws1',
  employeeId: 'e1',
  summary: '1 ticket a traiter.',
  texteLlm: null,
  genereParIa: false,
  planDuJour: [
    {
      ordre: 1,
      urgence: 'HAUTE',
      action: 'Action A',
      justification: 'jx',
      type: 'TICKET',
      targetId: 't1',
      dossierId: 'd1',
      lien: '/workflows/x',
    },
  ],
  signaux: {
    echeances: [],
    resteAFaire: [],
    counts: { echeances: 0, echeancesDepassees: 0, tickets: 1, demandes: 0 },
  },
  createdAt: '2026-08-09T08:00:00Z',
  seenAt: '2026-08-09T08:00:00Z', // deja vu -> pas d'appel markSeen
  ...over,
});

const renderPanel = () => render(<CopilotePanel />, { wrapper: MemoryRouter });

describe('CopilotePanel', () => {
  beforeEach(() => {
    getMyBriefing.mockReset();
    refresh.mockReset();
    markSeen.mockReset();
    markSeen.mockResolvedValue(undefined);
  });

  it('affiche une erreur discrete si le refresh echoue, en conservant les donnees', async () => {
    getMyBriefing.mockResolvedValue(briefing());
    refresh.mockRejectedValue(new Error('boom'));

    renderPanel();
    await waitFor(() => expect(screen.getByText('Action A')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /Rafra/i }));

    // Message d'erreur visible...
    await waitFor(() =>
      expect(screen.getByText(/Actualisation impossible/i)).toBeInTheDocument(),
    );
    // ...et les donnees precedentes restent affichees.
    expect(screen.getByText('Action A')).toBeInTheDocument();
  });

  it('remplace les donnees au succes du refresh (sans erreur)', async () => {
    getMyBriefing.mockResolvedValue(briefing());
    refresh.mockResolvedValue(
      briefing({
        id: 'b2',
        planDuJour: [
          {
            ordre: 1,
            urgence: 'HAUTE',
            action: 'Action B',
            justification: 'jx',
            type: 'TICKET',
            targetId: 't2',
            dossierId: 'd1',
            lien: '/workflows/y',
          },
        ],
      }),
    );

    renderPanel();
    await waitFor(() => expect(screen.getByText('Action A')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /Rafra/i }));

    await waitFor(() => expect(screen.getByText('Action B')).toBeInTheDocument());
    expect(screen.queryByText('Action A')).not.toBeInTheDocument();
    expect(screen.queryByText(/Actualisation impossible/i)).not.toBeInTheDocument();
  });
});
