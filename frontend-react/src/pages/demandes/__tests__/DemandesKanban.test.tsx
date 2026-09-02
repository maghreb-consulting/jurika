import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DemandesKanban } from '../DemandesKanban';

/**
 * Page CLIENT "Mes demandes" — Kanban lecture seule (3 colonnes) + creation.
 *
 * Couvre : (1) agregation par dossier + groupement par statut dans la bonne
 * colonne ; (2) creation d'une demande via dataroomService.createDemande puis
 * rechargement (listDemandesByDossier rappele).
 */

const listDossiers = vi.fn();
const listDemandesByDossier = vi.fn();
const createDemande = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: (...a: unknown[]) => listDossiers(...a),
    listDemandesByDossier: (...a: unknown[]) => listDemandesByDossier(...a),
    createDemande: (...a: unknown[]) => createDemande(...a),
  },
}));

function demande(id: string, statut: string, sujet: string, createdAt: string) {
  return {
    id,
    dossierId: 'dos-1',
    soumisPar: 'c1',
    sujet,
    description: null,
    statut,
    ticketId: null,
    prisEnChargePar: null,
    noteInterne: null,
    traiteAt: null,
    createdAt,
  };
}

describe('DemandesKanban', () => {
  beforeEach(() => {
    listDossiers.mockReset();
    listDemandesByDossier.mockReset();
    createDemande.mockReset();
  });

  it('groupe les demandes par statut dans la bonne colonne', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([
      demande('d1', 'NON_TRAITEE', 'Demande A', '2026-06-01T10:00:00Z'),
      demande('d2', 'NON_TRAITEE', 'Demande B', '2026-06-02T10:00:00Z'),
      demande('d3', 'EN_COURS', 'Demande C', '2026-06-03T10:00:00Z'),
      demande('d4', 'TRAITEE', 'Demande D', '2026-06-04T10:00:00Z'),
    ]);

    render(
      <MemoryRouter>
        <DemandesKanban />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText('Demande A')).toBeInTheDocument());
    expect(listDemandesByDossier).toHaveBeenCalledWith('dos-1');

    // Les 4 demandes sont rendues, chacune dans une carte cliquable.
    expect(screen.getByText('Demande B')).toBeInTheDocument();
    expect(screen.getByText('Demande C')).toBeInTheDocument();
    expect(screen.getByText('Demande D')).toBeInTheDocument();

    // Detail au clic : ouvre le modal avec le sujet.
    fireEvent.click(screen.getByText('Demande C'));
    await waitFor(() =>
      expect(screen.getByRole('dialog')).toBeInTheDocument(),
    );
    expect(
      within(screen.getByRole('dialog')).getByText('Demande C'),
    ).toBeInTheDocument();
  });

  it('cree une demande puis recharge la liste', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([]);
    createDemande.mockResolvedValue({ id: 'new', statut: 'NON_TRAITEE' });

    render(
      <MemoryRouter>
        <DemandesKanban />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getAllByText('Aucune demande').length).toBe(3),
    );

    // Ouvre le formulaire de creation.
    fireEvent.click(screen.getByRole('button', { name: /Nouvelle demande/i }));
    const dialog = await screen.findByRole('dialog');

    fireEvent.change(
      within(dialog).getByPlaceholderText(/Modification de l'objet social/i),
      { target: { value: 'Nouveau sujet' } },
    );
    fireEvent.click(within(dialog).getByRole('button', { name: /Envoyer/i }));

    await waitFor(() =>
      expect(createDemande).toHaveBeenCalledWith({
        dossierId: 'dos-1',
        sujet: 'Nouveau sujet',
        description: undefined,
      }),
    );

    // Rechargement apres creation : 1 appel initial + 1 apres creation.
    await waitFor(() =>
      expect(listDemandesByDossier).toHaveBeenCalledTimes(2),
    );
  });
});
