import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b (E15, parcours de bout en bout " approbation des comptes ") : etape 2 du PV AGO.
 * Defauts trouves a l'ecran sur le Z440 :
 *  - la convocation saisie a l'etape 1 (date, heure) etait redemandee VIDE a l'etape 2 ;
 *  - les postes d'affectation ajoutes n'avaient pas de libelle (montant sans nom accessible,
 *    poste nomme par son exemple " Ex. : Reserve legale... ").
 */

const DOSSIER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    getDossierParties: vi.fn(async () => ({ associes: [], gerants: [], denomination: 'ATLAS NORD SARL' })),
  },
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: vi.fn(async () => []),
    getJuridique: vi.fn(async () => ({ documentsEnVigueur: [], dossiersParTicket: [] })),
  },
}));
vi.mock('../../../services/workflowDocumentService', async (orig) => ({
  ...(await orig<typeof import('../../../services/workflowDocumentService')>()),
  listTemplatesForWorkflow: vi.fn().mockResolvedValue([]),
}));
vi.mock('../WorkflowBoot', () => ({
  WorkflowBoot: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock('../useWorkflow', () => ({
  useWorkflow: () => ({
    ticket: { id: 'ticket-1', reference: 'T-1', titre: 'Approbation des comptes', type: 'PV_AGO', statut: 'GENERATION_DOCUMENTS', dossierId: null },
    progress: { statut: 'EN_COURS', currentStep: 2, totalSteps: 5, data: {} },
    loading: false,
    saving: false,
    error: null,
    setError: vi.fn(),
    stepData: {
      step1: {
        dossierId: DOSSIER_ID, exerciceClos: '2025', exercice: '2025', dateAGO: '2026-06-05', dateAgo: '2026-06-05',
        convocation: { date: '2026-05-20', heure: '10:00' },
      },
    },
    viewStep: 2,
    maxStep: 2,
    saveDraft: vi.fn(),
    executeStep: vi.fn(),
    goToStep: vi.fn(),
    goPrev: vi.fn(),
    goNext: vi.fn(),
    reload: vi.fn(),
    registerDirty: () => () => undefined,
    enregistrement: { type: 'repos' },
    flushDraft: vi.fn(),
    conflits: [],
  }),
}));
vi.mock('../../tickets/CancelTicketDialog', () => ({ CancelTicketDialog: () => null }));
vi.mock('../../../components/workflow/IndicateurEnregistrement', () => ({ IndicateurEnregistrement: () => null }));

const { PvAgoWorkflowPage } = await import('../PvAgoWorkflowPage');

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PV AGO — étape 2', () => {
  it('la convocation de l’étape 1 n’est pas redemandée', async () => {
    render(<MemoryRouter><PvAgoWorkflowPage /></MemoryRouter>);
    await screen.findByText('Affectation du résultat');
    expect(screen.queryByLabelText('Date de convocation')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Heure de convocation')).not.toBeInTheDocument();
  });

  it('chaque poste d’affectation ajouté porte un libellé (poste et montant)', async () => {
    render(<MemoryRouter><PvAgoWorkflowPage /></MemoryRouter>);
    await screen.findByText('Affectation du résultat');
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter un poste' }));
    const postes = screen.getAllByRole('textbox', { name: /^Poste d’affectation/ });
    const montants = screen.getAllByRole('spinbutton', { name: /^Montant/ });
    expect(postes.length).toBeGreaterThanOrEqual(2);
    expect(montants.length).toBe(postes.length);
  });
});
