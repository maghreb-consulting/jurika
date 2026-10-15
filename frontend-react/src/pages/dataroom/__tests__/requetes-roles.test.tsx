import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Lot L6b (E6), backlog L0 : « boutons d'action encore visibles au superviseur alors que le
 * serveur les refuse ». POST /requetes, /demandes/{id}/valider et /complement sont reserves a
 * l'employe (DemandesController) : le superviseur suit les requetes sans ces boutons.
 */

const svc = { listRequetesByDossier: vi.fn(), validerRequete: vi.fn(), createRequete: vi.fn() };
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listRequetesByDossier: (...a: unknown[]) => svc.listRequetesByDossier(...a),
    validerRequete: (...a: unknown[]) => svc.validerRequete(...a),
    createRequete: (...a: unknown[]) => svc.createRequete(...a),
  },
}));

import { RequetesAuClientSection } from '../RequetesAuClientSection';

const requete = (p: Record<string, unknown> = {}) => ({
  id: 'r1', dossierId: 'd1', sujet: 'Fournir le bail signé', description: null, typeRequete: 'PIECE',
  statut: 'REPONDUE', etatSuivi: 'EN_COURS', noteClient: 'Bail joint', createdAt: '2026-10-01T10:00:00Z',
  reponduAt: '2026-10-02T10:00:00Z', clotureAt: null, ...p,
});

beforeEach(() => {
  Object.values(svc).forEach((f) => f.mockReset());
  svc.listRequetesByDossier.mockResolvedValue([requete()]);
  svc.validerRequete.mockResolvedValue(undefined);
  svc.createRequete.mockResolvedValue(undefined);
});

describe('Requetes du cabinet au client', () => {
  it('employe : cree, valide, demande un complement', async () => {
    render(<RequetesAuClientSection dossierId="d1" role="EMPLOYE" />);
    expect(await screen.findByText('Fournir le bail signé')).toBeInTheDocument();
    expect(screen.getByText('Réponse du client : Bail joint')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Complément' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Valider' }));
    await waitFor(() => expect(svc.validerRequete).toHaveBeenCalledWith('r1'));

    fireEvent.click(screen.getByRole('button', { name: 'Nouvelle requête' }));
    const dialogue = await screen.findByRole('dialog', { name: 'Nouvelle requête au client' });
    expect(dialogue).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la requête' }));
    expect(await screen.findByText('Le sujet est obligatoire.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'Information' }));
    fireEvent.change(screen.getByLabelText('Sujet'), { target: { value: 'Préciser le capital' } });
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la requête' }));
    await waitFor(() => expect(svc.createRequete).toHaveBeenCalledWith(
      expect.objectContaining({ dossierId: 'd1', typeRequete: 'INFO', sujet: 'Préciser le capital' }),
    ));
  }, 15000);

  it('superviseur : suit les requetes, sans bouton que le serveur lui refuse', async () => {
    render(<RequetesAuClientSection dossierId="d1" role="SUPERVISEUR" />);
    expect(await screen.findByText('Fournir le bail signé')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nouvelle requête' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Valider' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Complément' })).not.toBeInTheDocument();
    expect(screen.getByText(/Seul l’employé responsable du dossier/)).toBeInTheDocument();
  }, 15000);
});
