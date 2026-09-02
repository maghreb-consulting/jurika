import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TracabilitePage } from '../TracabilitePage';

// E2 — Tracabilite : filtres par NOM (Select) resolus en UUID cote client avant
// l'appel /tracabilite, annuaire COMPLET (employes + clients + comptes retires),
// et filtre par statut d'acteur (applique cote backend).

const USER_UUID = '11111111-1111-1111-1111-111111111111';
const CLIENT_UUID = '33333333-3333-3333-3333-333333333333';
const RETIRED_UUID = '44444444-4444-4444-4444-444444444444';
const DOSSIER_UUID = '22222222-2222-2222-2222-222222222222';

const search = vi.fn();
const listWorkspaceDirectory = vi.fn();
const listDossiers = vi.fn();
const ticketList = vi.fn();

vi.mock('../../../services/tracabilite.service', () => ({
  tracabiliteService: { search: (...a: unknown[]) => search(...a) },
}));
vi.mock('../../../services/auth.service', () => ({
  authService: { listWorkspaceDirectory: (...a: unknown[]) => listWorkspaceDirectory(...a) },
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: { listDossiers: (...a: unknown[]) => listDossiers(...a) },
}));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: (...a: unknown[]) => ticketList(...a) },
}));
vi.mock('../../../store/authStore', () => ({ useCurrentUser: () => null }));

const emptyPage = { items: [], total: 0, limit: 50, offset: 0 };

const dir = (over: Record<string, unknown>) => ({
  userId: '', email: '', firstName: '', lastName: '', phone: null,
  role: 'EMPLOYE', status: 'ACTIVE', mustChangePassword: false,
  lastLoginAt: null, createdAt: '', ...over,
});

describe('TracabilitePage — filtres par nom + acteurs actifs/retires', () => {
  beforeEach(() => {
    search.mockReset().mockResolvedValue(emptyPage);
    listWorkspaceDirectory.mockReset().mockResolvedValue([
      dir({ userId: USER_UUID, firstName: 'Karim', lastName: 'Alaoui', role: 'EMPLOYE', status: 'ACTIVE' }),
      dir({ userId: CLIENT_UUID, firstName: 'Sara', lastName: 'Bennani', email: 'sara@client.ma', role: 'CLIENT', status: 'ACTIVE' }),
      dir({ userId: RETIRED_UUID, firstName: 'Old', lastName: 'Employe', role: 'EMPLOYE', status: 'INACTIVE' }),
    ]);
    listDossiers.mockReset().mockResolvedValue([
      { id: DOSSIER_UUID, raisonSociale: 'ATLAS SARL', formeJuridique: 'SARL', ice: null,
        ville: null, statut: 'ACTIVE' },
    ]);
    ticketList.mockReset().mockResolvedValue({ items: [], total: 0, limit: 200, offset: 0 });
  });

  it('selectionner un utilisateur par nom -> /tracabilite appele avec son UUID', async () => {
    const user = userEvent.setup();
    render(<TracabilitePage />);

    // Le chargement initial appelle search avec des filtres vides.
    await waitFor(() => expect(search).toHaveBeenCalled());

    // L'option de l'utilisateur (par nom) apparait une fois l'annuaire charge.
    await waitFor(() => expect(screen.getByRole('option', { name: 'Karim Alaoui' })).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText('Acteur'), USER_UUID);
    await user.click(screen.getByRole('button', { name: /Filtrer/i }));

    await waitFor(() =>
      expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ userId: USER_UUID })),
    );
  });

  it('le CLIENT apparait par son NOM dans le groupe "Clients" (pas d\'UUID)', async () => {
    render(<TracabilitePage />);
    await waitFor(() => expect(search).toHaveBeenCalled());

    // Groupe optgroup "Clients" present + option client par nom.
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /Sara Bennani/ })).toBeInTheDocument(),
    );
    const clientOption = screen.getByRole('option', { name: /Sara Bennani/ }) as HTMLOptionElement;
    expect(clientOption.value).toBe(CLIENT_UUID);
    // L'UUID brut n'est jamais affiche comme libelle d'option.
    expect(screen.queryByRole('option', { name: CLIENT_UUID })).not.toBeInTheDocument();
  });

  it('selectionner le statut "Retires" -> /tracabilite appele avec actorStatus=RETIRED', async () => {
    const user = userEvent.setup();
    render(<TracabilitePage />);
    await waitFor(() => expect(search).toHaveBeenCalled());

    await user.selectOptions(screen.getByLabelText("Statut de l'acteur"), 'RETIRED');
    await user.click(screen.getByRole('button', { name: /Filtrer/i }));

    await waitFor(() =>
      expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ actorStatus: 'RETIRED' })),
    );
  });

  it('un acteur retire (INACTIVE) porte le badge "retire" dans le tableau', async () => {
    search.mockReset().mockResolvedValue({
      items: [
        { id: 1, action: 'TICKET_CREATED', actionLabel: 'Ticket cree', userId: RETIRED_UUID,
          entityType: 'ticket', entityId: null, metadata: null, sourceService: 'ticket-service',
          createdAt: '2026-07-15T10:00:00Z' },
      ],
      total: 1, limit: 50, offset: 0,
    });
    render(<TracabilitePage />);

    // Le nom (pas l'UUID) apparait + badge "retire".
    await waitFor(() => expect(screen.getByText('Old Employe')).toBeInTheDocument());
    const row = screen.getByText('Old Employe').closest('td') as HTMLElement;
    expect(within(row).getByText(/retire/i)).toBeInTheDocument();
  });

  it('un acteur purge (absent de l\'annuaire) s\'affiche "Utilisateur supprime"', async () => {
    search.mockReset().mockResolvedValue({
      items: [
        { id: 2, action: 'TICKET_CREATED', actionLabel: 'Ticket cree',
          userId: '99999999-9999-9999-9999-999999999999',
          entityType: 'ticket', entityId: null, metadata: null, sourceService: 'ticket-service',
          createdAt: '2026-07-15T10:00:00Z' },
      ],
      total: 1, limit: 50, offset: 0,
    });
    render(<TracabilitePage />);

    await waitFor(() => expect(screen.getByText(/Utilisateur supprime/)).toBeInTheDocument());
    // Jamais l'UUID brut complet.
    expect(screen.queryByText('99999999-9999-9999-9999-999999999999')).not.toBeInTheDocument();
  });

  it('selectionner un dossier par raison sociale -> /tracabilite appele avec son UUID', async () => {
    const user = userEvent.setup();
    render(<TracabilitePage />);

    await waitFor(() => expect(search).toHaveBeenCalled());

    // 1) choisir le type d'entite "Dossier" (active le <Select> entite)
    await user.selectOptions(screen.getByLabelText("Type d'entite"), 'dossier');
    // 2) l'option du dossier (par nom) apparait
    await waitFor(() => expect(screen.getByRole('option', { name: 'ATLAS SARL' })).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Entite'), DOSSIER_UUID);
    await user.click(screen.getByRole('button', { name: /Filtrer/i }));

    await waitFor(() =>
      expect(search).toHaveBeenLastCalledWith(
        expect.objectContaining({ entityType: 'dossier', entityId: DOSSIER_UUID }),
      ),
    );
  });
});
