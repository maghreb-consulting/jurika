import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L5 (E6) : rubrique Débours du ticket (cahier des charges 10.1). Saisie par l'employé en
 * charge (une ligne par frais, sept types, pièce déposée en Data Room du dossier), lecture
 * seule pour le superviseur et sur un ticket clos ou annulé, état des débours en Word.
 */
const { tickets, dataroom } = vi.hoisted(() => ({
  tickets: {
    listDebours: vi.fn(),
    createDebours: vi.fn(),
    updateDebours: vi.fn(),
    deleteDebours: vi.fn(),
    downloadEtatDebours: vi.fn(),
  },
  dataroom: { uploadJuridique: vi.fn(), downloadDocument: vi.fn() },
}));
vi.mock('../../../services/ticket.service', () => ({ ticketService: tickets }));
vi.mock('../../../services/dataroom.service', () => ({ dataroomService: dataroom }));

import { DeboursPanel } from '../DeboursPanel';
import type { TicketStatut } from '../../../types/ticket';

const greffe = {
  id: 'l1', ticketId: 't1', libelle: 'Dépôt au greffe', categorie: 'FRAIS_GREFFE', montantMad: 350,
  dateEngagement: '2026-10-01', pieceJointeUrl: null, pieceJointeFilename: 'quittance.pdf', pieceDocumentId: 'doc-q',
  notes: null, createdAt: '2026-10-01', categorieReprise: null,
};
const timbres = {
  id: 'l2', ticketId: 't1', libelle: 'Timbres fiscaux', categorie: 'AUTRE', montantMad: 884.5,
  dateEngagement: '2026-10-02', pieceJointeUrl: null, pieceJointeFilename: null, pieceDocumentId: null,
  notes: null, createdAt: '2026-10-02', categorieReprise: 'HONORAIRES',
};

function monter(canAct: boolean, statut: TicketStatut = 'DEROULEMENT_DEMARCHE', dossierId: string | null = 'dos1') {
  render(<DeboursPanel ticketId="t1" reference="T-2026-00012" dossierId={dossierId} statut={statut} canAct={canAct} />);
}

describe('Débours du ticket (RG-DEB-01 à 03)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tickets.listDebours.mockResolvedValue({ items: [greffe, timbres], total: 1234.5 });
  });

  it('liste les frais, le total, signale la pièce manquante et la catégorie reprise', async () => {
    monter(true);
    expect(await screen.findByText('Dépôt au greffe')).toBeInTheDocument();
    expect(screen.getByTestId('total-debours').textContent?.replace(/\s/g, ' ')).toMatch(/1 234,50 DH/);
    expect(screen.getByText('Pièce manquante')).toBeInTheDocument();
    expect(screen.getByTestId('pieces-manquantes')).toHaveTextContent('Une ligne n’a pas de pièce justificative.');
    expect(screen.getByTestId('categorie-reprise')).toHaveTextContent('Reprise de « Honoraires »');
    expect(screen.getByRole('note', { name: 'Comment tenir les débours ?' })).toHaveTextContent(/joignez sa pièce justificative/);
  });

  it('propose exactement les sept types du cahier des charges', async () => {
    monter(true);
    fireEvent.click(await screen.findByRole('button', { name: /Ajouter un frais/ }));
    const options = within(screen.getByLabelText('Type de frais')).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['Droits d’enregistrement', 'Frais de greffe', 'Annonce légale', 'Timbres',
      'Légalisation', 'Frais d’organisme', 'Autre']);
  });

  it('refuse une saisie incomplète en nommant ce qui manque', async () => {
    monter(true);
    fireEvent.click(await screen.findByRole('button', { name: /Ajouter un frais/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter le débours' }));
    expect(await screen.findByText('Indiquez le libellé du frais.')).toBeInTheDocument();
    expect(screen.getByText('Indiquez un montant positif, en dirhams.')).toBeInTheDocument();
    expect(tickets.createDebours).not.toHaveBeenCalled();
  });

  it('dépose la pièce en Data Room du dossier puis enregistre la ligne', async () => {
    dataroom.uploadJuridique.mockResolvedValue({ id: 'doc-new' });
    tickets.createDebours.mockResolvedValue({});
    monter(true);
    fireEvent.click(await screen.findByRole('button', { name: /Ajouter un frais/ }));
    fireEvent.change(screen.getByLabelText(/Libellé/), { target: { value: 'Annonce légale JAL' } });
    fireEvent.change(screen.getByLabelText('Type de frais'), { target: { value: 'ANNONCE_LEGALE' } });
    fireEvent.change(screen.getByLabelText(/Montant/), { target: { value: '1234,50' } });
    const fichier = new File(['x'], 'facture-jal.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByLabelText(/^Pièce justificative \(reçu/), { target: { files: [fichier] } });
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter le débours' }));

    await waitFor(() => expect(tickets.createDebours).toHaveBeenCalled());
    expect(dataroom.uploadJuridique).toHaveBeenCalledWith('dos1', expect.objectContaining({
      file: fichier, documentType: 'AUTRE', ticketId: 't1', visibleClient: true,
      title: 'Justificatif de débours — T-2026-00012 — Annonce légale JAL — facture-jal.pdf',
    }));
    expect(tickets.createDebours).toHaveBeenCalledWith('t1', expect.objectContaining({
      libelle: 'Annonce légale JAL', categorie: 'ANNONCE_LEGALE', montant: 1234.5,
      pieceDocumentId: 'doc-new', pieceJointeFilename: 'facture-jal.pdf',
    }));
  });

  it('modifie une ligne en gardant sa pièce', async () => {
    tickets.updateDebours.mockResolvedValue({});
    monter(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Modifier « Dépôt au greffe »' }));
    fireEvent.change(screen.getByLabelText(/Montant/), { target: { value: '400' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer la modification' }));
    await waitFor(() => expect(tickets.updateDebours).toHaveBeenCalledWith('t1', 'l1', expect.objectContaining({
      montant: 400, pieceDocumentId: 'doc-q', pieceJointeFilename: 'quittance.pdf',
    })));
    expect(dataroom.uploadJuridique).not.toHaveBeenCalled();
  });

  it('supprime une ligne après confirmation', async () => {
    tickets.deleteDebours.mockResolvedValue(undefined);
    monter(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Supprimer « Timbres fiscaux »' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Supprimer' }));
    await waitFor(() => expect(tickets.deleteDebours).toHaveBeenCalledWith('t1', 'l2'));
  });

  it('télécharge l’état des débours en Word', async () => {
    tickets.downloadEtatDebours.mockResolvedValue(undefined);
    monter(false);
    fireEvent.click(await screen.findByRole('button', { name: /État des débours \(Word\)/ }));
    expect(tickets.downloadEtatDebours).toHaveBeenCalledWith('t1', 'T-2026-00012');
  });

  it('superviseur ou collègue : consultation seule, aucune action de saisie', async () => {
    monter(false);
    expect(await screen.findByText('Dépôt au greffe')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Ajouter un frais/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Modifier/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Supprimer/ })).toBeNull();
    expect(screen.getByRole('note', { name: 'Comment tenir les débours ?' })).toHaveTextContent(/Consultation seule/);
  });

  it.each(['CLOTURE_DOSSIER', 'ANNULE'] as const)('ticket %s : lecture seule même pour le responsable', async (statut) => {
    monter(true, statut);
    expect(await screen.findByText('Dépôt au greffe')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Ajouter un frais/ })).toBeNull();
    expect(screen.getByRole('note', { name: 'Comment tenir les débours ?' })).toHaveTextContent(/lecture seule/);
    expect(screen.getByRole('button', { name: /État des débours \(Word\)/ })).toBeEnabled();
  });

  it('sans débours, l’état ne peut pas être téléchargé', async () => {
    tickets.listDebours.mockResolvedValue({ items: [], total: 0 });
    monter(true);
    expect(await screen.findByText('Aucun débours saisi pour ce ticket.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /État des débours \(Word\)/ })).toBeDisabled();
  });

  it('ticket sans dossier : la pièce ne peut pas être déposée, l’écran le dit', async () => {
    monter(true, 'CREATION_TICKET', null);
    fireEvent.click(await screen.findByRole('button', { name: /Ajouter un frais/ }));
    expect(screen.getByText(/rattaché à aucun dossier/)).toBeInTheDocument();
  });
});
