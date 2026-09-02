import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ClientAccessLogDrawer } from '../ClientAccessLogDrawer';

// Sprint 14 bis / D3 — ClientAccessLogDrawer
// 2 tests : empty state apres load, items affiches en ligne du tableau.

const getAccessLog = vi.fn();

vi.mock('../../../../services/dataroom.service', () => ({
  dataroomService: {
    getAccessLog: (...args: unknown[]) => getAccessLog(...args),
  },
}));

describe('ClientAccessLogDrawer', () => {
  beforeEach(() => {
    getAccessLog.mockReset();
  });

  it('affiche empty state quand aucune entree', async () => {
    getAccessLog.mockResolvedValue({ items: [], total: 0 });

    render(
      <ClientAccessLogDrawer open dossierId="d1" onClose={() => {}} />,
    );

    await waitFor(() => {
      expect(screen.getByText(/Aucune activite enregistree/i)).toBeInTheDocument();
    });
    expect(getAccessLog).toHaveBeenCalledWith('d1', 50, 0);
  });

  it('rend le tableau avec les actions client recentes', async () => {
    getAccessLog.mockResolvedValue({
      items: [
        {
          id: '1',
          createdAt: '2026-05-22T10:30:00Z',
          action: 'PREVIEW_DOC',
          userId: 'u1',
          userEmail: 'client@cabinet.ma',
          documentId: 'doc-1',
          ipAddress: '196.1.2.3',
        },
        {
          id: '2',
          createdAt: '2026-05-22T10:00:00Z',
          action: 'DOWNLOAD_DOC',
          userId: 'u1',
          userEmail: 'client@cabinet.ma',
          documentId: 'doc-2',
          ipAddress: '196.1.2.3',
        },
      ],
      total: 2,
    });

    render(
      <ClientAccessLogDrawer open dossierId="d1" raisonSociale="SARL Demo" onClose={() => {}} />,
    );

    await waitFor(() => {
      expect(screen.getAllByText('196.1.2.3').length).toBe(2);
    });
    expect(screen.getByText(/SARL Demo/i)).toBeInTheDocument();
  });
});
