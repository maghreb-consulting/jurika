import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L4a (E6, RG-TKT-03 a 05) : annulation commune a tous les parcours.
 * - la reprise annonce le statut anterieur retrouve, ou pourquoi elle est impossible ;
 * - les documents d'annulation se preparent dans la fiche du ticket : une lettre de
 *   retrait par demarche interrompue, les depots suivis ne se retirent pas d'ici, une
 *   donnee nommee par un refus et absente de l'ecran se saisit sur place.
 * Avant : la reprise visait toujours la generation des documents, et aucun ecran ne
 * produisait les documents d'annulation.
 */
const get = vi.fn();
const put = vi.fn();
const post = vi.fn();
vi.mock('../../../lib/api', () => ({
  api: {
    get: (...a: unknown[]) => get(...a),
    put: (...a: unknown[]) => put(...a),
    post: (...a: unknown[]) => post(...a),
  },
  extractError: (e: unknown) => ({ message: (e as Error)?.message ?? String(e) }),
}));

import { RepriseTicketDialog } from '../RepriseTicketDialog';
import { AnnulationDocumentsPanel } from '../AnnulationDocumentsPanel';
import { CancelTicketDialog } from '../CancelTicketDialog';
import type { Ticket } from '../../../types/ticket';

const ticket = (type: string) => ({
  id: 't1', reference: 'T-2026-00042', type, statut: 'ANNULE', dossierId: 'd1',
}) as unknown as Ticket;

beforeEach(() => {
  get.mockReset();
  put.mockReset();
  post.mockReset();
});

describe('Reprise d’un ticket annulé', () => {
  it('annonce le statut antérieur et reprend vers lui seul', async () => {
    get.mockResolvedValueOnce({ data: { annule: true, statutAnterieur: 'DEROULEMENT_DEMARCHE', possible: true, motif: null } });
    const confirmer = vi.fn().mockResolvedValue(undefined);
    render(<RepriseTicketDialog ticket={ticket('MODIFICATION')} onClose={vi.fn()} onConfirm={confirmer} />);
    expect(await screen.findByText(/« Déroulement de la démarche »/)).toBeInTheDocument();
    // La fenetre s'ouvre dans un effet (showModal, ou l'attribut open sous jsdom) : son texte est
    // deja dans le DOM avant qu'elle soit ouverte, ses boutons pas encore accessibles. On attend
    // donc chaque element par son role (test instable sous charge, echoue en CI le 2026-10-17).
    fireEvent.click(await screen.findByRole('button', { name: 'Continuer' }));
    fireEvent.change(await screen.findByRole('textbox'), { target: { value: 'Le client relance le projet.' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Suivant' }));
    fireEvent.click(await screen.findByRole('button', { name: /Reprendre au statut/ }));
    await waitFor(() => expect(confirmer).toHaveBeenCalledWith('DEROULEMENT_DEMARCHE', 'Le client relance le projet.'));
    expect(get).toHaveBeenCalledWith('/tickets/t1/reprise');
  });

  it('dit pourquoi la reprise est impossible après un retrait, sans bouton de reprise', async () => {
    get.mockResolvedValueOnce({ data: { annule: true, statutAnterieur: 'DEROULEMENT_DEMARCHE', possible: false,
      motif: 'Un retrait a été effectué : ouvrez un nouveau ticket.' } });
    render(<RepriseTicketDialog ticket={ticket('MODIFICATION')} onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(await screen.findByTestId('reprise-refus')).toHaveTextContent('nouveau ticket');
    expect(screen.queryByRole('button', { name: /Reprendre/ })).toBeNull();
  });
});

describe('Annulation : ce qui suit est annoncé', () => {
  it('le dialogue d’annulation annonce les documents et la reprise', () => {
    render(<CancelTicketDialog ticket={ticket('DISSOLUTION')} onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(screen.getByText(/lettre de retrait pour chaque administration/)).toBeInTheDocument();
    expect(screen.getByText(/repris à son statut antérieur/)).toBeInTheDocument();
  });
});

describe('Documents d’annulation', () => {
  const charge = {
    typeTicket: 'MODIFICATION',
    variables: {
      DEMARCHES_INTERROMPUES: [
        { DEMARCHE_DESIGNATION: 'Dépôt du procès-verbal', DEMARCHE_ADMINISTRATION: 'Greffe de Casablanca', _suivie: 'oui' },
        { DEMARCHE_DESIGNATION: 'Demande de taxe professionnelle', DEMARCHE_ADMINISTRATION: 'DGI' },
      ],
    },
  };

  it('propose une lettre par démarche interrompue ; un dépôt suivi ne se retire pas d’ici', async () => {
    get.mockResolvedValueOnce({ data: charge });
    post.mockResolvedValueOnce({ data: new Blob(['x']), headers: { 'content-disposition': 'attachment; filename="lettre.docx"' } });
    render(<AnnulationDocumentsPanel ticket={ticket('MODIFICATION')} canAct />);
    expect(await screen.findByTestId('doc-lettre-0')).toHaveTextContent('Greffe de Casablanca');
    expect(screen.getByTestId('doc-lettre-1')).toHaveTextContent('DGI');
    expect(within(screen.getByTestId('demarche-0')).queryByRole('button', { name: /Retirer/ })).toBeNull();
    expect(within(screen.getByTestId('demarche-1')).getByRole('button', { name: /Retirer/ })).toBeInTheDocument();
    // Aucune valeur pre-remplie : l'origine attend un choix.
    expect(screen.getByLabelText('Origine de l’annulation')).toHaveValue('');
    fireEvent.click(within(screen.getByTestId('doc-lettre-1')).getByRole('button', { name: 'Générer' }));
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/ai/workflows/ANNULATION/documents/LETTRE_RETRAIT_DEPOT_SOCIETE_EXISTANTE',
      { ticketId: 't1', retraitIndex: 1 }, { responseType: 'blob' }));
  });

  it('une donnée nommée par un refus et absente de l’écran se saisit sur place, puis le document se régénère', async () => {
    get.mockResolvedValueOnce({ data: { typeTicket: 'CREATION', variables: {} } });
    const refus = { donneesManquantes: [{ variable: 'POUVOIR_MANDATAIRE_NOM', libelle: 'Mandataire aux formalités', endroit: '' }],
      message: 'Données manquantes', code: 'DONNEES_MANQUANTES' };
    post.mockRejectedValueOnce({ response: { status: 422, data: JSON.stringify(refus) } });
    render(<AnnulationDocumentsPanel ticket={ticket('CREATION')} canAct />);
    fireEvent.click(within(await screen.findByTestId('doc-note')).getByRole('button', { name: 'Générer' }));
    const bloc = await screen.findByTestId('complements-note');
    fireEvent.change(within(bloc).getByLabelText('Mandataire aux formalités'), { target: { value: 'Karim ALAOUI' } });
    put.mockResolvedValueOnce({ data: { typeTicket: 'CREATION', variables: { } } });
    post.mockResolvedValueOnce({ data: new Blob(['x']), headers: {} });
    fireEvent.click(within(bloc).getByRole('button', { name: 'Enregistrer et régénérer' }));
    await waitFor(() => expect(put).toHaveBeenCalledWith('/workflows/t1/annulation/saisies', {
      variables: { POUVOIR_MANDATAIRE_NOM: 'Karim ALAOUI' }, demarches: [], pieces: [] }));
    await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
    expect(post).toHaveBeenLastCalledWith('/ai/workflows/ANNULATION/documents/NOTE_ANNULATION_DOSSIER',
      { ticketId: 't1' }, { responseType: 'blob' });
  });
});
