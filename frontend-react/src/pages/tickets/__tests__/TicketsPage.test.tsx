import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/Toast';
import { useAuthStore } from '../../../store/authStore';

/**
 * Lot L6b (E7) : la liste des tickets dans la structure commune <Page>. Le titre et
 * l'action principale sont au meme endroit ; l'action "Nouveau ticket" n'existe que pour
 * l'employe (le serveur refuse la creation au superviseur, backlog L0).
 */
const { list } = vi.hoisted(() => ({ list: vi.fn() }));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: (...a: unknown[]) => list(...a) },
}));

import { TicketsPage } from '../TicketsPage';

function monter(role: 'EMPLOYE' | 'SUPERVISEUR') {
  useAuthStore.setState({
    user: { id: 'u1', email: 'a@b.ma', role, workspaceId: 'ws', prenom: 'A', nom: 'B' },
    isAuthenticated: true,
  } as never);
  render(
    <MemoryRouter initialEntries={['/tickets']}>
      <ToastProvider>
        <TicketsPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false } as never);
  list.mockReset();
});

describe('Liste des tickets (L6b)', () => {
  it('employe : titre « Mes tickets », action « Nouveau ticket », totaux en base', async () => {
    list.mockImplementation(async (p: { statuts?: string[] }) => ({
      items: [],
      total: p.statuts?.[0] === 'CREATION_TICKET' ? 2 : p.statuts?.[0] === 'GENERATION_DOCUMENTS' ? 3 : 0,
    }));
    monter('EMPLOYE');
    expect(screen.getByRole('heading', { level: 1, name: 'Mes tickets' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Nouveau ticket/ })).toBeInTheDocument();
    expect(await screen.findByTestId('tickets-resume')).toHaveTextContent(/5 tickets, dont/);
  });

  it('superviseur : « Tous les tickets », vue globale, aucune action de creation', async () => {
    list.mockResolvedValue({ items: [], total: 0 });
    monter('SUPERVISEUR');
    expect(screen.getByRole('heading', { level: 1, name: 'Tous les tickets' })).toBeInTheDocument();
    expect(screen.getByText('Vue globale')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Nouveau ticket/ })).toBeNull();
    await waitFor(() => expect(screen.getByText(/dès qu’un employé ouvre une opération/)).toBeInTheDocument());
  });

  it('le choix de l’affichage est un groupe de boutons a bascule', () => {
    list.mockResolvedValue({ items: [], total: 0 });
    monter('EMPLOYE');
    expect(screen.getByRole('button', { name: /Par statut/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /Tableau/ })).toHaveAttribute('aria-pressed', 'false');
  });
});
