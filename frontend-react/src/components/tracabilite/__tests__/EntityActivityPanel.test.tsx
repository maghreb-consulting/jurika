import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EntityActivityPanel } from '../EntityActivityPanel';

// Lot L6b (E9) : defaut vu a l'ecran, "Vous — login success". Le panneau affiche le
// libelle rendu par le serveur ; un libelle absent est dit, jamais fabrique du code.

const entityHistory = vi.fn();
vi.mock('../../../services/tracabilite.service', () => ({
  tracabiliteService: { entityHistory: (...a: unknown[]) => entityHistory(...a) },
}));
vi.mock('../useUserNames', () => ({
  useUserNames: () => (id: string | null) => (id === 'moi' ? 'Vous' : 'Karim Alaoui'),
}));

const ligne = (p: Record<string, unknown>) => ({
  id: 1, action: 'LOGIN_SUCCESS', actionLabel: 'Connexion réussie', userId: 'moi', entityType: 'dossier',
  entityId: 'd1', metadata: null, sourceService: 'auth-service', createdAt: '2026-10-15T08:00:00Z', ...p,
});

describe('EntityActivityPanel', () => {
  it('affiche le libelle du serveur, jamais le code de l action', async () => {
    entityHistory.mockResolvedValue({
      items: [
        ligne({}),
        ligne({ id: 2, action: 'AI_DOCUMENT_GENERATED', actionLabel: 'Document généré', userId: 'u2' }),
      ],
      total: 2, limit: 50, offset: 0,
    });
    render(<EntityActivityPanel entityType="dossier" entityId="d1" />);

    expect(await screen.findByText('— Connexion réussie')).toBeInTheDocument();
    expect(screen.getByText('— Document généré')).toBeInTheDocument();
    expect(entityHistory).toHaveBeenCalledWith('dossier', 'd1', 50);
    const liste = screen.getByRole('list');
    expect(liste).not.toHaveTextContent(/login success|LOGIN_SUCCESS|document generated/i);
  });

  it('libelle absent : nomme le code au lieu de le deguiser', async () => {
    entityHistory.mockResolvedValue({
      items: [ligne({ action: 'TICKET_STATUS_CHANGED', actionLabel: '' })],
      total: 1, limit: 50, offset: 0,
    });
    render(<EntityActivityPanel entityType="dossier" entityId="d1" />);

    expect(await screen.findByText('— Action enregistrée (code TICKET_STATUS_CHANGED)')).toBeInTheDocument();
    expect(screen.queryByText(/ticket status changed/i)).not.toBeInTheDocument();
  });
});
