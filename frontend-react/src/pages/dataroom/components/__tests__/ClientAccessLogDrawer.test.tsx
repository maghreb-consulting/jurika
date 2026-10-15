import { render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ClientAccessLogDrawer } from '../ClientAccessLogDrawer';

// Sprint 14 bis / D3 — ClientAccessLogDrawer, puis lot L6b (E6) : la colonne « Utilisateur »
// et la colonne « Document » donnent un nom lisible, jamais un identifiant tronque (backlog L6).

const getAccessLog = vi.fn();
const getJuridique = vi.fn();
const listDepots = vi.fn();
const listWorkspaceDirectory = vi.fn();
let role = 'EMPLOYE';

vi.mock('../../../../services/dataroom.service', () => ({
  dataroomService: {
    getAccessLog: (...args: unknown[]) => getAccessLog(...args),
    getJuridique: (...args: unknown[]) => getJuridique(...args),
    listDepots: (...args: unknown[]) => listDepots(...args),
  },
}));
vi.mock('../../../../services/auth.service', () => ({
  authService: { listWorkspaceDirectory: (...a: unknown[]) => listWorkspaceDirectory(...a) },
}));
vi.mock('../../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'moi', role }),
}));

const doc = (id: string, title: string) => ({
  id, dossierId: 'd1', ticketId: 't1', documentType: 'STATUTS', title, version: 1, current: true,
  filename: `${id}.pdf`, contentType: 'application/pdf', sizeBytes: 1, createdAt: '2026-05-01', replacedAt: null,
});

const entree = (p: Record<string, unknown>) => ({
  id: '1', dossierId: 'd1', createdAt: '2026-05-22T10:30:00Z', action: 'PREVIEW_DOC', userId: 'u1',
  documentId: 'doc-1', ipAddress: '196.1.2.3', userAgent: null, ...p,
});

describe('ClientAccessLogDrawer', () => {
  beforeEach(() => {
    getAccessLog.mockReset();
    getJuridique.mockReset();
    listDepots.mockReset();
    listWorkspaceDirectory.mockReset();
    getJuridique.mockResolvedValue({ documentsEnVigueur: [], dossiersParTicket: [], historiqueOperations: [] });
    listDepots.mockResolvedValue([]);
    role = 'EMPLOYE';
  });

  it('affiche un etat vide explicite quand aucune entree', async () => {
    getAccessLog.mockResolvedValue({ items: [], total: 0 });

    render(<ClientAccessLogDrawer open dossierId="d1" onClose={() => {}} />);

    await waitFor(() => {
      expect(screen.getByText(/Aucune activité enregistrée/i)).toBeInTheDocument();
    });
    expect(getAccessLog).toHaveBeenCalledWith('d1', 50, 0);
  });

  it('rend le tableau avec les actions client recentes', async () => {
    getAccessLog.mockResolvedValue({
      items: [entree({ id: '1' }), entree({ id: '2', action: 'DOWNLOAD_DOC', documentId: 'doc-2' })],
      total: 2,
    });

    render(<ClientAccessLogDrawer open dossierId="d1" raisonSociale="SARL Demo" onClose={() => {}} />);

    await waitFor(() => {
      expect(screen.getAllByText('196.1.2.3').length).toBe(2);
    });
    expect(screen.getByText(/SARL Demo/i)).toBeInTheDocument();
    expect(screen.getByText('Visualisation')).toBeInTheDocument();
    expect(screen.getByText('Téléchargement')).toBeInTheDocument();
  });

  it('nomme le client et le document ; jamais un identifiant tronque', async () => {
    getAccessLog.mockResolvedValue({
      items: [
        entree({ id: '1', userId: 'c1-aaaa-bbbb', documentId: 'doc-1111-2222' }),
        entree({ id: '2', userId: 'ancien-9999-0000', documentId: 'depot-3333-4444' }),
        entree({ id: '3', userId: 'c1-aaaa-bbbb', documentId: 'disparu-5555-6666', action: 'VIEW_DOSSIER' }),
      ],
      total: 3,
    });
    getJuridique.mockResolvedValue({
      documentsEnVigueur: [doc('doc-1111-2222', 'Statuts mis à jour')],
      dossiersParTicket: [],
      historiqueOperations: [],
    });
    listDepots.mockResolvedValue([{ id: 'depot-3333-4444', title: 'Bail signé', filename: 'bail.pdf', contentType: null, sizeBytes: 1, uploadedBy: 'c1', createdAt: '2026-05-01' }]);

    render(
      <ClientAccessLogDrawer
        open
        dossierId="d1"
        client={{ userId: 'c1-aaaa-bbbb', firstName: 'Karim', lastName: 'Alaoui', email: 'k@x.ma', status: 'ACTIVE' }}
        onClose={() => {}}
      />,
    );

    const tableau = await screen.findByRole('table');
    await waitFor(() => expect(within(tableau).getAllByText('Karim Alaoui')).toHaveLength(2));
    expect(within(tableau).getByText('Compte inconnu')).toBeInTheDocument();
    expect(await within(tableau).findByText('Statuts mis à jour')).toBeInTheDocument();
    expect(within(tableau).getByText('Bail signé')).toBeInTheDocument();
    expect(within(tableau).getByText('Document introuvable')).toBeInTheDocument();
    for (const tronque of ['c1-aaaa', 'ancien-9', 'doc-1111', 'depot-33', 'disparu-']) {
      expect(tableau).not.toHaveTextContent(tronque);
    }
    // L'employe n'interroge pas l'annuaire du cabinet (reserve au superviseur).
    expect(listWorkspaceDirectory).not.toHaveBeenCalled();
  });

  it('le superviseur resout aussi un ancien client par l annuaire du cabinet', async () => {
    role = 'SUPERVISEUR';
    listWorkspaceDirectory.mockResolvedValue([
      { userId: 'ancien-9999-0000', firstName: 'Salma', lastName: 'Idrissi', email: 's@x.ma', role: 'CLIENT', status: 'INACTIVE' },
    ]);
    getAccessLog.mockResolvedValue({ items: [entree({ userId: 'ancien-9999-0000' })], total: 1 });

    render(<ClientAccessLogDrawer open dossierId="d1" client={null} onClose={() => {}} />);

    expect(await screen.findByText('Salma Idrissi')).toBeInTheDocument();
    expect(listWorkspaceDirectory).toHaveBeenCalledTimes(1);
  });
});
