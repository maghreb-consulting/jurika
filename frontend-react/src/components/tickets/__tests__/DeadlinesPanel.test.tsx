import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useAuthStore } from '../../../store/authStore';

/**
 * Lot L6b (E7, backlog L0) : le serveur refuse au superviseur de terminer ou d'ignorer une
 * echeance (DeadlineController, EMPLOYE seul) ; il ne voit donc pas ces boutons.
 */
vi.mock('../../../services/deadline.service', () => ({
  deadlineService: {
    list: vi.fn(async () => ({
      items: [{ id: 'e1', ticketId: 't1', dossierId: null, title: 'Dépôt au registre du commerce', description: null,
        dueAt: '2026-11-01T10:00:00Z', severity: 'WARNING', source: 'AUTO', ruleKey: null, statut: 'OUVERTE',
        assigneId: null, creeParId: null, termineeAt: null, ignoreeAt: null, metadata: null, createdAt: '2026-10-01T10:00:00Z' }],
      total: 1,
    })),
    complete: vi.fn(),
    dismiss: vi.fn(),
  },
}));

import { DeadlinesPanel } from '../DeadlinesPanel';

function monter(role: string) {
  useAuthStore.setState({ user: { userId: 'u', workspaceId: 'w', email: 'a@b.ma', role }, isAuthenticated: true } as never);
  render(<DeadlinesPanel ticketId="t1" />);
}

afterEach(() => useAuthStore.setState({ user: null, isAuthenticated: false } as never));

describe('Échéances du ticket', () => {
  it('l’employé peut terminer ou ignorer une échéance', async () => {
    monter('EMPLOYE');
    expect(await screen.findByText('Dépôt au registre du commerce')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Marquer comme terminée' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ignorer' })).toBeInTheDocument();
  });

  it('le superviseur consulte sans boutons d’action', async () => {
    monter('SUPERVISEUR');
    expect(await screen.findByText('Dépôt au registre du commerce')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marquer comme terminée' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Ignorer' })).toBeNull();
  });
});
