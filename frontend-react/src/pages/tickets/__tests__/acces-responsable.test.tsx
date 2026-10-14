import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L4d (E2, RG-DOS-01) : seul l'employé responsable du dossier agit sur le ticket.
 * Un collègue ou le superviseur consulte : aucune action, et la raison est affichée.
 */
const { acces } = vi.hoisted(() => ({ acces: vi.fn() }));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: {
    acces: (...a: unknown[]) => acces(...a),
    get: vi.fn(async () => ({
      id: 't1', titre: 'Création PARACOSME', reference: 'T-2026-00001', type: 'CREATION',
      statut: 'GENERATION_DOCUMENTS', priorite: 'NORMALE', categorie: 'JURIDIQUE',
    })),
    listDebours: vi.fn(async () => ({ items: [], total: 0 })),
  },
}));
let role = 'EMPLOYE';
vi.mock('../../../store/authStore', () => ({ useCurrentUser: () => ({ role }) }));
vi.mock('../../../store/essaiStore', () => ({ useEnEssai: () => false }));
const { stub } = vi.hoisted(() => ({ stub: () => null }));
vi.mock('../DemarchesPanel', () => ({ DemarchesPanel: stub }));
vi.mock('../DecisionsTicketPanel', () => ({ DecisionsTicketPanel: stub }));
vi.mock('../RecapitulatifPanel', () => ({ RecapitulatifPanel: stub }));
vi.mock('../NoteTicketPanel', () => ({ NoteTicketPanel: stub }));
vi.mock('../AnnulationDocumentsPanel', () => ({ AnnulationDocumentsPanel: stub }));
vi.mock('../../../components/tickets/DeadlinesPanel', () => ({ DeadlinesPanel: stub }));
vi.mock('../../../components/tracabilite/EntityActivityPanel', () => ({ EntityActivityPanel: stub }));

import { TicketDetailDrawer } from '../TicketDetailDrawer';

function monter() {
  render(
    <MemoryRouter>
      <TicketDetailDrawer ticketId="t1" onClose={vi.fn()} onChanged={vi.fn(async () => {})} />
    </MemoryRouter>,
  );
}

describe('Accès au ticket (RG-DOS-01)', () => {
  it('le responsable du dossier voit les actions', async () => {
    role = 'EMPLOYE';
    acces.mockResolvedValue({ enCharge: true, peutAgir: true, raison: null });
    monter();
    expect(await screen.findByRole('button', { name: /Reprendre le parcours/ })).toBeInTheDocument();
    expect(screen.queryByTestId('acces-raison')).toBeNull();
  });

  it('un collègue non responsable consulte seulement, avec la raison', async () => {
    role = 'EMPLOYE';
    acces.mockResolvedValue({ enCharge: false, peutAgir: false, raison: 'Ce ticket relève d’un dossier dont vous n’êtes pas le responsable.' });
    monter();
    expect(await screen.findByTestId('acces-raison')).toHaveTextContent(/pas le responsable/);
    expect(screen.queryByRole('button', { name: /Reprendre le parcours/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Annuler le ticket/ })).toBeNull();
  });

  it('le superviseur lit seulement', async () => {
    role = 'SUPERVISEUR';
    acces.mockResolvedValue({ enCharge: false, peutAgir: false, raison: 'Consultation : seul l’employé responsable du dossier agit sur le ticket.' });
    monter();
    expect(await screen.findByTestId('acces-raison')).toHaveTextContent(/Consultation/);
    expect(screen.queryByRole('button', { name: /Annuler le ticket/ })).toBeNull();
  });
});
