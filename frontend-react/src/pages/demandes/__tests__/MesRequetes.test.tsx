/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { RequeteSummary } from '../../../types/dataroom';

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: vi.fn().mockResolvedValue([{ id: 'd1', raisonSociale: 'ACME SARL' }]),
    listRequetesByDossier: vi.fn().mockResolvedValue([]),
    repondreRequete: vi.fn().mockResolvedValue({}),
  },
}));

import { MesRequetes } from '../MesRequetes';
import { dataroomService } from '../../../services/dataroom.service';

function req(id: string, statut: RequeteSummary['statut'], sujet: string): RequeteSummary {
  return {
    id, dossierId: 'd1', soumisPar: 'emp', sujet, description: null, statut,
    ticketId: null, prisEnChargePar: 'emp', noteInterne: null, traiteAt: null,
    createdAt: '2026-07-11T09:00:00Z', direction: 'EMPLOYE_TO_CLIENT',
    typeRequete: 'PIECE', reponduAt: null, clotureAt: null, noteClient: null,
  };
}

describe('<MesRequetes> (client)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('repartit les requetes en colonnes et permet de repondre a une requete OUVERTE', async () => {
    vi.mocked(dataroomService.listRequetesByDossier).mockResolvedValueOnce([
      req('r1', 'OUVERTE', 'Fournir le RC'),
      req('r2', 'REPONDUE', 'Question TVA'),
      req('r3', 'CLOTUREE', 'Bail signe'),
    ]);

    render(
      <MemoryRouter>
        <MesRequetes />
      </MemoryRouter>,
    );

    // Les 3 requetes apparaissent (colonnes A faire / En attente / Termine).
    expect(await screen.findByText('Fournir le RC')).toBeInTheDocument();
    expect(screen.getByText('Question TVA')).toBeInTheDocument();
    expect(screen.getByText('Bail signe')).toBeInTheDocument();

    // Seule la requete OUVERTE (actionnable) propose « Repondre / Fournir ».
    const boutons = screen.getAllByRole('button', { name: /Répondre \/ Fournir/i });
    expect(boutons).toHaveLength(1);

    // Ouvrir la modale + envoyer la reponse.
    fireEvent.click(boutons[0]);
    fireEvent.click(await screen.findByRole('button', { name: /Envoyer/i }));

    await waitFor(() =>
      expect(vi.mocked(dataroomService.repondreRequete)).toHaveBeenCalledWith('r1', expect.anything()),
    );
  });

  it("affiche un etat vide quand il n'y a aucune requete", async () => {
    vi.mocked(dataroomService.listRequetesByDossier).mockResolvedValueOnce([]);
    render(
      <MemoryRouter>
        <MesRequetes />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Aucune requête pour le moment/i)).toBeInTheDocument();
  });
});
