import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L6b (E6), backlog L6 : « champ "Ticket lie" d'une demande saisi a la main ».
 * Le ticket lie se choisit desormais parmi les tickets du dossier (appel existant
 * GET /tickets?dossierId=...), jamais en tapant un identifiant ; "Aucun" delie.
 */

const svc = { listDemandesByDossier: vi.fn(), updateDemande: vi.fn() };
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDemandesByDossier: (...a: unknown[]) => svc.listDemandesByDossier(...a),
    updateDemande: (...a: unknown[]) => svc.updateDemande(...a),
  },
}));
const tickets = { list: vi.fn(), create: vi.fn() };
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: (...a: unknown[]) => tickets.list(...a), create: (...a: unknown[]) => tickets.create(...a) },
}));

import { DemandesTab } from '../DemandesTab';

const demande = (p: Record<string, unknown> = {}) => ({
  id: 'dem1', dossierId: 'd1', sujet: 'Transfert du siège', description: 'Nouveau local à Rabat', statut: 'NON_TRAITEE',
  noteInterne: null, ticketId: null, createdAt: '2026-10-01T10:00:00Z', traiteAt: null, ...p,
});
const ticket = (id: string, reference: string, titre: string) => ({ id, reference, titre, type: 'MODIFICATION', statut: 'EN_COURS', dossierId: 'd1' });

beforeEach(() => {
  Object.values(svc).forEach((f) => f.mockReset());
  Object.values(tickets).forEach((f) => f.mockReset());
  svc.updateDemande.mockResolvedValue(undefined);
  tickets.list.mockResolvedValue({ items: [ticket('t1', 'T-2026-0007', 'Transfert du siège social'), ticket('t2', 'T-2026-0009', 'Cession de parts')], total: 2 });
});

describe('Demandes du client : ticket lie', () => {
  it('se choisit parmi les tickets du dossier, par sa reference et son titre', async () => {
    svc.listDemandesByDossier.mockResolvedValue([demande()]);
    render(<DemandesTab dossierId="d1" role="EMPLOYE" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Modifier' }));

    const choix = screen.getByLabelText('Ticket lié (facultatif)');
    expect(choix.tagName).toBe('SELECT');
    await waitFor(() => expect(within(choix).getAllByRole('option')).toHaveLength(3));
    expect(tickets.list).toHaveBeenCalledWith(expect.objectContaining({ dossierId: 'd1' }));
    expect(within(choix).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Aucun ticket lié', 'T-2026-0007 — Transfert du siège social', 'T-2026-0009 — Cession de parts',
    ]);

    fireEvent.change(choix, { target: { value: 't1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(svc.updateDemande).toHaveBeenCalled());
    expect(svc.updateDemande.mock.calls[0]).toEqual(['dem1', expect.objectContaining({ ticketId: 't1', statut: 'NON_TRAITEE' })]);
  }, 15000);

  it('un ticket lie hors de la liste reste nomme, jamais un identifiant a recopier', async () => {
    svc.listDemandesByDossier.mockResolvedValue([demande({ ticketId: 'ancien-ticket-uuid' })]);
    render(<DemandesTab dossierId="d1" role="EMPLOYE" />);
    expect(await screen.findByText('Ticket lié')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Modifier' }));
    const choix = screen.getByLabelText('Ticket lié (facultatif)');
    await waitFor(() => expect(within(choix).getAllByRole('option')).toHaveLength(4));
    expect(choix).toHaveValue('ancien-ticket-uuid');
    expect(within(choix).getByRole('option', { name: 'Ticket lié actuel (hors des tickets du dossier)' })).toBeInTheDocument();
  }, 15000);

  it('le superviseur consulte sans modifier ni creer de ticket', async () => {
    svc.listDemandesByDossier.mockResolvedValue([demande()]);
    render(<DemandesTab dossierId="d1" role="SUPERVISEUR" />);
    expect(await screen.findByText('Transfert du siège')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modifier' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Créer un ticket/ })).not.toBeInTheDocument();
  }, 15000);
});
