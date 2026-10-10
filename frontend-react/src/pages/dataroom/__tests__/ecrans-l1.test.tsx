import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AxiosError } from 'axios';

/**
 * Ecrans du lot L1 (demande du 2026-10-10), a l'ecran :
 *  1. historique des responsables du dossier ;
 *  2. versions datees de la taxe professionnelle, date d'effet completable plus tard ;
 *  3. debours consultes par le client ;
 *  4. bouton de suppression d'un document visible seulement avec le droit, message sinon.
 * Chaque ecran porte ses aides.
 */

const svc = {
  historiqueResponsables: vi.fn(),
  listVersionsTp: vi.fn(),
  updateIdentifiants: vi.fn(),
  mesDroits: vi.fn(),
  getJuridique: vi.fn(),
  deleteJuridiqueDocument: vi.fn(),
};
vi.mock('../../../services/dataroom.service', () => ({
  // Les autres appels des composants enfants (types de documents, etc.) rendent une liste vide.
  dataroomService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => {
      const f = (svc as Record<string, (...x: unknown[]) => unknown>)[p];
      return f ? f(...a) : Promise.resolve([]);
    },
  }),
}));
const tickets = { listDebours: vi.fn(), downloadDeboursPdf: vi.fn() };
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { listDebours: (...a: unknown[]) => tickets.listDebours(...a), downloadDeboursPdf: (...a: unknown[]) => tickets.downloadDeboursPdf(...a) },
}));

import { HistoriqueResponsablesPanel } from '../components/HistoriqueResponsablesPanel';
import { IdentifiantsDrawer } from '../components/IdentifiantsDrawer';
import { ClientDebours } from '../components/ClientDebours';
import { DossierJuridiqueTab } from '../DossierJuridiqueTab';
import type { DossierJuridiqueView, DossierTicket } from '../../../types/dataroom';

const ligne = (p: Record<string, unknown>) => ({
  id: 'r1', dossierId: 'd1', raisonSociale: 'ACME', ancienResponsableId: null, ancienResponsableNom: null,
  nouveauResponsableId: 'e1', nouveauResponsableNom: 'Karim Bennani', nature: 'RATTRAPAGE', auteurId: null,
  auteurNom: null, motif: 'Migration V28 (lot L1) : dossier sans responsable', createdAt: '2026-10-09T10:00:00Z',
  responsableActuelId: 'e2', responsableActuelNom: 'Salma Idrissi', verifiePar: null, verifieParNom: null,
  verifieLe: null, ...p,
});

const vue = (p: Partial<DossierJuridiqueView> = {}): DossierJuridiqueView => ({
  dossierId: 'd1', raisonSociale: 'ACME SARL', formeJuridique: 'SARL', ice: null, rcNumero: null, rcTribunal: null,
  identifiantFiscal: null, taxeProfessionnelle: 'TP-2026', cnss: null, adresseSiege: null, ville: null,
  capitalSocialMad: null, dateConstitution: null, statut: 'ACTIVE', documentsEnVigueur: [], historiqueOperations: [],
  dossiersParTicket: [], ...p,
});

beforeEach(() => {
  window.localStorage.clear();
  Object.values(svc).forEach((f) => f.mockReset());
  Object.values(tickets).forEach((f) => f.mockReset());
});

describe('1. Historique des responsables', () => {
  it('affiche transferts, reaffectations et rattrapages, avec noms, motif et verification', async () => {
    svc.historiqueResponsables.mockResolvedValue([
      ligne({}),
      ligne({ id: 'r2', nature: 'FORCEE', ancienResponsableId: 'e1', ancienResponsableNom: 'Karim Bennani',
        nouveauResponsableId: 'e2', nouveauResponsableNom: 'Salma Idrissi', auteurNom: 'Nadia Superviseure',
        motif: 'Absence prolongée', createdAt: '2026-10-10T08:00:00Z' }),
    ]);
    render(<HistoriqueResponsablesPanel dossierId="d1" />);
    const items = await screen.findAllByRole('listitem');
    // Le plus recent d'abord.
    expect(items[0]).toHaveTextContent('Réaffectation par le superviseur');
    expect(items[0]).toHaveTextContent('De Karim Bennani à Salma Idrissi · par Nadia Superviseure');
    expect(items[0]).toHaveTextContent('Motif : Absence prolongée');
    expect(items[1]).toHaveTextContent('Désignation automatique');
    expect(items[1]).toHaveTextContent('À vérifier par le superviseur.');
    expect(screen.getByRole('note', { name: 'Changer de responsable' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Que montre l’historique des responsables ?' })).toBeInTheDocument();
  });
});

describe('2. Versions de la taxe professionnelle', () => {
  it('liste les versions, signale la version en vigueur et la date a completer ; envoie la date d effet', async () => {
    svc.listVersionsTp.mockResolvedValue([
      { id: 'v1', numero: 'TP-2025', dateEffet: '2025-01-01', saisiPar: 'e1', saisiLe: '2025-01-02T00:00:00Z', origine: 'SAISIE', enVigueur: false },
      { id: 'v2', numero: 'TP-2026', dateEffet: null, saisiPar: 'e1', saisiLe: '2026-01-02T00:00:00Z', origine: 'SAISIE', enVigueur: true },
    ]);
    svc.updateIdentifiants.mockResolvedValue(undefined);
    render(<IdentifiantsDrawer open view={vue()} onClose={() => {}} onSaved={() => {}} />);
    const liste = await screen.findByRole('list');
    const versions = within(liste).getAllByRole('listitem');
    expect(versions[0]).toHaveTextContent('TP-2026');
    expect(versions[0]).toHaveTextContent('Date d’effet à compléter');
    expect(versions[0]).toHaveTextContent('En vigueur');
    expect(versions[1]).toHaveTextContent('TP-2025');
    expect(screen.getByRole('note', { name: 'Changer ou dater la taxe professionnelle' })).toBeInTheDocument();

    // Completer plus tard : meme numero, avec la date d'effet.
    fireEvent.change(screen.getByLabelText('Date d’effet de la taxe professionnelle'), { target: { value: '2026-02-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(svc.updateIdentifiants).toHaveBeenCalled());
    expect(svc.updateIdentifiants.mock.calls[0][1]).toMatchObject({
      taxeProfessionnelle: 'TP-2026', taxeProfessionnelleDateEffet: '2026-02-01',
    });
  });

  it('sans date d effet, rien n est invente', async () => {
    svc.listVersionsTp.mockResolvedValue([]);
    svc.updateIdentifiants.mockResolvedValue(undefined);
    render(<IdentifiantsDrawer open view={vue()} onClose={() => {}} onSaved={() => {}} />);
    await screen.findByText('Aucune taxe professionnelle enregistrée.');
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(svc.updateIdentifiants).toHaveBeenCalled());
    expect(svc.updateIdentifiants.mock.calls[0][1].taxeProfessionnelleDateEffet).toBeNull();
  });
});

describe('3. Debours cote client', () => {
  const operations: DossierTicket[] = [
    { ticketId: 't1', libelle: 'Constitution', reference: 'T-2026-001', type: 'CREATION', statut: 'CLOTURE_DOSSIER', ouvertLe: '2026-09-01', groupes: [], totalDocuments: 0 },
    { ticketId: null, libelle: 'Hors ticket', reference: null, type: null, statut: null, ouvertLe: null, groupes: [], totalDocuments: 0 },
  ] as unknown as DossierTicket[];

  it('affiche les frais et le total de chaque operation, et le PDF', async () => {
    tickets.listDebours.mockResolvedValue({
      items: [{ id: 'x', ticketId: 't1', libelle: 'Frais de greffe', categorie: 'FRAIS_TRIBUNAL', montantMad: 350, dateEngagement: '2026-09-02', pieceJointeUrl: null, pieceJointeFilename: null, notes: null, createdAt: '2026-09-02' }],
      total: 350,
    });
    render(<ClientDebours operations={operations} />);
    expect(await screen.findByText('Frais de greffe')).toBeInTheDocument();
    expect(tickets.listDebours).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('table')).toHaveTextContent('Total');
    fireEvent.click(screen.getByRole('button', { name: 'Télécharger l’état des débours (PDF)' }));
    expect(tickets.downloadDeboursPdf).toHaveBeenCalledWith('t1', 'T-2026-001');
    expect(screen.getByRole('note', { name: 'Consulter vos débours' })).toBeInTheDocument();
  });

  it('une operation refusee par le serveur affiche le motif', async () => {
    tickets.listDebours.mockRejectedValue(new AxiosError('Forbidden', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 403, statusText: 'Forbidden', headers: {}, config: {} as never,
      data: { message: 'La consultation des documents n’est pas autorisée pour votre accès.' },
    }));
    render(<ClientDebours operations={operations} />);
    expect(await screen.findByText(/n’est pas autorisée/)).toBeInTheDocument();
  });
});

describe('4. Bouton de suppression d un document', () => {
  const doc = {
    id: 'doc1', dossierId: 'd1', ticketId: 't1', documentType: 'AUTRE', title: 'Statuts', version: 1, current: true,
    filename: 'statuts.pdf', contentType: 'application/pdf', sizeBytes: 10, createdAt: '2026-09-01', replacedAt: null,
  };
  const avecDocument = () => vue({
    documentsEnVigueur: [doc],
    dossiersParTicket: [{ ticketId: 't1', libelle: 'Constitution', reference: 'T-1', type: 'CREATION', statut: 'GENERATION_DOCUMENTS', ouvertLe: '2026-09-01', groupes: [{ code: 'AUTRE', libelle: 'Autres', documents: [doc] }], totalDocuments: 1 }],
  } as unknown as Partial<DossierJuridiqueView>);

  it('visible quand l employe a le droit', async () => {
    svc.getJuridique.mockResolvedValue(avecDocument());
    svc.mesDroits.mockResolvedValue({ peutSupprimerDocuments: true, motif: null });
    render(<MemoryRouter><DossierJuridiqueTab dossierId="d1" role="EMPLOYE" /></MemoryRouter>);
    await screen.findAllByText('Statuts');
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Supprimer' }).length).toBeGreaterThan(0));
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('masque sans le droit, avec un message clair', async () => {
    svc.getJuridique.mockResolvedValue(avecDocument());
    svc.mesDroits.mockResolvedValue({ peutSupprimerDocuments: false, motif: 'Le droit de supprimer des documents ne vous a pas été accordé : demandez-le à votre superviseur.' });
    render(<MemoryRouter><DossierJuridiqueTab dossierId="d1" role="EMPLOYE" /></MemoryRouter>);
    await screen.findAllByText('Statuts');
    expect(await screen.findByRole('status')).toHaveTextContent('Vous pouvez ajouter des documents à ce dossier, mais pas en supprimer.');
    expect(screen.getByRole('status')).toHaveTextContent('demandez-le à votre superviseur');
    expect(screen.queryByRole('button', { name: 'Supprimer' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Qui peut supprimer un document ?' })).toBeInTheDocument();
  });

  it('le superviseur ne voit ni bouton ni message', async () => {
    svc.getJuridique.mockResolvedValue(avecDocument());
    render(<MemoryRouter><DossierJuridiqueTab dossierId="d1" role="SUPERVISEUR" /></MemoryRouter>);
    await screen.findAllByText('Statuts');
    expect(screen.queryByRole('button', { name: 'Supprimer' })).toBeNull();
    expect(svc.mesDroits).not.toHaveBeenCalled();
  });
});
